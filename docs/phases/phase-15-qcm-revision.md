# Phase 15 (École) : QCM de révision auto-générés

**Pour revenir exactement à cet état du code :**
```
git checkout phase-15-qcm-revision
```
Vérifié end-to-end avec un vrai appel Azure OpenAI et un vrai navigateur (Playwright).

---

## 1. Le besoin

`phase-5-ecole-resume-cours` a délibérément limité son périmètre à synthèse + notions/définitions
+ points à réviser, en notant explicitement que les QCM sont un chantier à part : ils ont besoin
d'un état de réponse par utilisateur et d'une UI interactive de quiz distincte. C'est ce chantier
qu'on ouvre ici — dernier morceau de "résumés adaptés au cours" (master prompt) qui restait
entièrement à zéro côté École (avec exercices/corrections et annonces, non traités ici).

## 2. Les décisions de conception

### 2.1 — Source de génération : le résumé de cours, pas la transcription brute

Plutôt que de mirroirer `ResumeCoursService` à l'identique (génération depuis la transcription
complète), le QCM se génère à partir du **résumé de cours déjà produit** (synthèse + notions).
Ça respecte la règle de discipline de coûts du projet ("reuse existing summaries instead of
regenerating") : le texte d'entrée est plus court, et un `ResumeCours` `REUSSI` doit exister avant
de pouvoir générer un QCM (`AucunResumeCoursDisponibleException`, 409, sinon). Traçabilité en
chaîne : QCM → résumé de cours → segments → transcription → audio (mêmes `segmentsSources` que le
`ResumeCours` source, pas de traçabilité par question individuelle — même compromis déjà accepté
pour le résumé et le compte rendu).

### 2.2 — Deux couches : génération/cache (mirroir `resumecours`) + état par utilisateur (mirroir `MaitriseNotion`)

| Génération/cache (`Qcm`) | État par utilisateur (`TentativeQcm`) |
|---|---|
| Un seul QCM par session, généré à la demande, mis en cache | Une ligne par `(qcm_id, utilisateur_id)`, contrainte d'unicité |
| Mirroir `ResumeCours`/`ResumeCoursService`/`ResumeCoursController` | Mirroir `MaitriseNotion` — `nombreTentatives` incrémental, pas d'historique complet |

### 2.3 — Correction renvoyée intégralement par l'API

`GET`/`POST /qcm` renvoient la bonne réponse et l'explication dès le chargement — le front-end les
masque visuellement tant que l'utilisateur n'a pas validé. Pas d'utilisateur adversarial à
bloquer ici (outil de révision personnelle, doctrine "outil d'aide, pas de surveillance" déjà en
place ailleurs dans le projet). Un seul DTO à maintenir plutôt que deux formes de réponse
(question seule / correction).

### 2.4 — Scoring déterministe, pas d'appel IA

`soumettreTentative` compare les index de réponse choisis aux `indexReponseCorrecte` de chaque
question — aucun appel Azure OpenAI supplémentaire pour noter, discipline de coûts.

## 3. Les fichiers backend, un par un

### `Qcm` / `QuestionQcm` (entité + embeddable)

```java
@Entity @Table(name = "qcms")
public class Qcm {
    private UUID sessionId;              // unique — un seul QCM par session
    private List<QuestionQcm> questions; // qcm_questions
    private List<Integer> segmentsSources;
    private StatutQcm statut;
}
```

`QuestionQcm` (`@Embeddable`) : `enonce`, `choixA/B/C/D` (4 choix fixes plutôt qu'une collection
imbriquée — pas de nested `@ElementCollection` dans un `@Embeddable`), `indexReponseCorrecte`,
`explication`. Tables auto-créées par Hibernate (`ddl-auto=update`) : `qcms`, `qcm_questions`,
`qcm_segments_sources`.

### `QcmService` — génère depuis le résumé de cours, pas la transcription

```java
public Qcm obtenirOuGenererQcm(UUID sessionId) {
    sessionService.obtenirSession(sessionId);
    Optional<Qcm> existant = qcmRepository.findBySessionId(sessionId);
    if (existant.isPresent()) return existant.get();

    ResumeCours resumeCours = resumeCoursRepository.findBySessionId(sessionId)
            .filter(rc -> rc.getStatut() == StatutResumeCours.REUSSI)
            .orElseThrow(() -> new AucunResumeCoursDisponibleException(sessionId));

    String contenuCours = synthese + "\n\n" + notions;
    try {
        QcmGenere genere = generateurQcm.genererQcm(contenuCours);
        return enregistrerSiAbsent(sessionId, questions, resumeCours.getSegmentsSources(), REUSSI);
    } catch (Exception e) {
        return enregistrerSiAbsent(sessionId, List.of(), resumeCours.getSegmentsSources(), ECHEC);
    }
}
```

### `GenerateurQcmAzureOpenAI` — même squelette HTTP, nouveau prompt

Même ressource Azure OpenAI (Responses API), mêmes propriétés `azure.openai.*`, même timeout
120s, même `CoutAzureService.enregistrerAppel(ServiceAzure.OPENAI_CHAT, ...)` que
`GenerateurResumeCoursAzureOpenAI`. Prompt : 5 questions à 4 choix, une seule bonne réponse.

```json
{"questions": [{"enonce": "...", "choix": ["...", "...", "...", "..."], "reponse_correcte": 0, "explication": "..."}]}
```

### `TentativeQcm` — état par utilisateur (mirroir `MaitriseNotion`)

```java
@Entity @Table(name = "tentatives_qcm", uniqueConstraints = @UniqueConstraint(columnNames = {"qcm_id", "utilisateur_id"}))
public class TentativeQcm {
    private UUID qcmId;
    private UUID utilisateurId;
    private List<Integer> reponsesChoisies;
    private int score;
    private int nombreQuestions;
    private int nombreTentatives;   // incremente a chaque soumission, comme MaitriseNotion
}
```

### `QcmController`

```
GET  /api/v1/sessions/{sessionId}/qcm                 -> 200 + body, ou 204 si absent
POST /api/v1/sessions/{sessionId}/qcm                  -> get-or-generate, 409 si pas de resume de cours REUSSI
POST /api/v1/sessions/{sessionId}/qcm/tentatives       -> soumet les reponses, renvoie score/nombreTentatives
GET  /api/v1/sessions/{sessionId}/qcm/tentatives/moi   -> derniere tentative de l'utilisateur, 204 si absente
```

### `GestionnaireExceptionsApi` (modifié)

`QcmNotFoundException` → 404, `AucunResumeCoursDisponibleException` → 409.

### Wiring RGPD (repéré en lisant le code existant, pas dans le plan initial)

`TentativeQcm` est une donnée personnelle (progression individuelle) au même titre que
`MaitriseNotion` — câblée dans les mêmes points d'intégration :
- `SessionPurgeService.purgerSessionCompletement` : purge les `TentativeQcm` du QCM de la session
  avant de supprimer le QCM (pas de FK JPA entre les deux tables, nettoyage explicite requis).
- `GouvernanceDonneesService.effacerCompte` : `tentativeQcmRepository.deleteByUtilisateurId(...)`.
- `GouvernanceDonneesService.exporterDonnees` / `ExportDonneesUtilisateur` : nouveau champ
  `tentativesQcm` (mirroir `maitrisesNotions`).

## 4. Le frontend

`types.ts`/`api.ts` : `Qcm`/`QuestionQcm`/`TentativeQcm` recopiés à la main,
`obtenirQcm`/`genererQcm`/`soumettreTentativeQcm`/`obtenirMaTentativeQcm`, même traitement
204/404 → `null` que le résumé de cours.

`SessionDetailEcole.tsx` gagne une section **interactive** "QCM de révision" (contrairement aux
autres sections, purement passives) : formulaire avec 4 choix par question (radio), bouton
"Valider mes réponses" désactivé tant que toutes les questions n'ont pas de réponse, puis
affichage ✓/✗ + explication par question et score global après soumission ; au chargement, si une
tentative existe déjà, l'état corrigé se recharge directement (pas besoin de re-répondre pour
revoir son score) ; bouton "Recommencer" pour repasser en mode réponse.

## 5. Les tests

`QcmServiceTest` (13 tests, mirroir `ResumeCoursServiceTest` + tests de scoring) :

| Test | Ce qu'il prouve |
|---|---|
| `genere_et_sauvegarde_a_partir_du_resume_de_cours` | cas nominal — contenu transmis au générateur, segments copiés |
| `marque_echec_quand_le_generateur_echoue` | `ECHEC` persisté, pas d'exception propagée |
| `renvoie_le_qcm_deja_en_cache_sans_regenerer` | garde d'idempotence |
| `leve_une_exception_si_aucun_resume_de_cours_nexiste` / `_est_en_echec` | `AucunResumeCoursDisponibleException` |
| `leve_une_exception_si_la_session_est_introuvable` | propage `SessionNotFoundException` |
| `obtenirQcm_*` | lecture simple, trouvé/absent |
| `soumettreTentative_calcule_le_score_et_cree_une_tentative` | scoring déterministe correct |
| `soumettreTentative_met_a_jour_une_tentative_existante_et_incremente_le_compteur` | ré-soumission, `nombreTentatives` |
| `soumettreTentative_leve_une_exception_si_le_qcm_est_introuvable` | `QcmNotFoundException` |
| `obtenirMaTentative_*` | trouvée/absente |

`GouvernanceDonneesServiceTest` et `SessionPurgeServiceTest` mis à jour pour les nouveaux
paramètres de constructeur et le nouveau comportement de purge.

`cd backend && mvn test` — **241/241 tests** passent au total.

## 6. Comment on a vérifié en conditions réelles

1. Backend démarré localement (base Postgres de vérification dédiée), utilisateur + session +
   transcription de test insérés (scénario photosynthèse, identique à la vérification de
   `phase-5-ecole-resume-cours`).
2. `POST /resume-cours` → résumé généré avec un vrai appel Azure OpenAI.
3. `POST /qcm` sur une session **sans** résumé de cours → **409**, comme prévu.
4. `GET /qcm` avant génération → **204**.
5. `POST /qcm` → Azure OpenAI renvoie 5 questions à 4 choix fidèles au résumé (photosynthèse,
   chlorophylle, chloroplastes, cycle de Calvin, stroma).
6. `POST /qcm/tentatives` avec un mélange de bonnes/mauvaises réponses → score exact (3/5) ;
   `GET /qcm/tentatives/moi` → renvoie la même tentative ; seconde soumission → `nombreTentatives`
   passe à 2.
7. Vérifié dans un vrai navigateur (Playwright) : génération du QCM, sélection des réponses,
   validation, affichage correct des ✓ (vert) sur les bonnes réponses avec explication, score
   affiché, bouton "Recommencer" ; rechargement de la page → l'état corrigé de la dernière
   tentative se recharge directement, aucune erreur console à aucune étape.
8. Toute l'infrastructure de vérification (conteneur Postgres dédié, backend local, serveur Vite)
   et les données de test ont été nettoyées après coup.

## 7. Limites connues, assumées, pas corrigées ici

- **Dépendance dure au résumé de cours** — pas de génération possible avant que la synthèse +
  notions existent ; choix assumé pour la discipline de coûts, mais ça ajoute une étape
  obligatoire côté utilisateur.
- **Une seule tentative "active" par utilisateur** — pas d'historique des tentatives précédentes,
  seule la dernière est conservée (même compromis que `MaitriseNotion`).
- **Nombre de questions fixe (5)**, non paramétrable.
- **Traçabilité au niveau du document**, pas par question individuelle — même compromis déjà
  accepté pour le résumé de cours et le compte rendu.
- **Pas de couloirs/promotions** — le QCM reste attaché à une session individuelle.
- **Correction non protégée côté serveur** — la bonne réponse est visible dans la réponse HTTP dès
  le chargement (décision assumée, cf. §2.3), pas adapté à un usage type examen surveillé.

## 8. Pour reprendre seul

- Code de référence exact : `git checkout phase-15-qcm-revision`
- Chemin de bout en bout : bouton "Générer le QCM" → `QcmController` → `QcmService` →
  `GenerateurQcmAzureOpenAI` → `QcmRepository` → section "QCM de révision" de
  `SessionDetailEcole.tsx` ; soumission → `QcmController.soumettreTentative` → `QcmService` →
  `TentativeQcmRepository`.
- Pour ajouter les exercices/corrections ou les annonces (derniers morceaux de "résumés adaptés au
  cours") : même squelette que `resumecours`/`qcm`, nouveau prompt, nouvelle entité dédiée si un
  état par utilisateur est nécessaire.

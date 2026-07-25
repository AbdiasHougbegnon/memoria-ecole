# Gouvernance des données (RGPD) — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-13-gouvernance-donnees
```

---

## 1. Le besoin

Le master prompt qualifie sa section "Gouvernance des données" de *"condition d'entrée dans
les secteurs régulés, pas un raffinement tardif"* : durée de conservation paramétrable,
droit à l'effacement réel (y compris dérivés), export à la demande, audit des accès sur les
données sensibles, chiffrement au repos/transit, consentement explicite à l'enregistrement.
Recherche confirmée dans le code (pas supposée) avant de concevoir cette brique : **zéro
ligne de code existait pour tout ce chapitre**, après 12 phases.

## 2. Les décisions de conception

### 2.1 — Périmètre délibérément resserré

Droit à l'effacement + export + durée de conservation, avec un audit **réduit** au journal
des opérations RGPD elles-mêmes (pas un audit général de tout accès à toute donnée
sensible, chantier d'observabilité disproportionné). Chiffrement au repos/transit et
consentement des participants explicitement **différés** (infrastructure et UX de capture,
orthogonaux à la gouvernance de données déjà collectées) — voir `docs/gouvernance-donnees.md`
§6 pour le détail assumé de chaque exclusion.

### 2.2 — Sessions personnelles vs partagées : supprimer ou anonymiser

Une session peut être personnelle (`couloirId == null`) ou partagée (rattachée à un
couloir, plusieurs participants). Impossible de supprimer en bloc une session partagée
parce qu'un seul participant demande l'effacement — elle contient les données d'autres
personnes. Décision : purge complète pour les sessions exclusives, anonymisation
(`createurId = null`) pour les sessions partagées — en réutilisant l'état déjà supporté des
sessions "historiques" (`Session.createurId` nullable depuis l'origine), pas une nouvelle
sémantique.

### 2.3 — Deux précédents du code réutilisés tels quels

`EmpreinteVocaleService.revoquer` a fourni le patron exact à suivre : suppression Postgres
autoritaire, nettoyage externe (disque, Azure AI Search) strictement best-effort. Et
`CouloirService.transfererPropriete`/`supprimerCouloir` avaient déjà résolu "que devient un
contenu partagé quand son propriétaire disparaît" — réutilisés sans aucune modification pour
les couloirs possédés par l'utilisateur effacé.

### 2.4 — Le piège de l'auto-invocation Spring

`@Transactional` ne s'applique pas à un appel interne au sein de la même instance Spring
(le proxy AOP n'intercepte pas les appels `this.methode()`). Solution : deux méthodes
publiques distinctes sur `GouvernanceDonneesService` (`effacerCompte`, transactionnel,
Postgres seul ; `finaliserEffacement`, best-effort, hors transaction), orchestrées par le
contrôleur — exactement le rôle d'un contrôleur ("orchestrent, ne décident pas"). Même
pattern appliqué à `SessionPurgeService` (`purgerSessionCompletement` / `nettoyerDependancesExternes`).

### 2.5 — "Instance = tenant" pour la rétention (même raisonnement que phase-11)

Pas de concept de tenant à construire : une durée de rétention globale pour l'instance,
désactivée par défaut (`-1`, doctrine "sûr par défaut" déjà appliquée au budget Azure). Le
balayage planifié réutilise `SessionPurgeService.purgerSessionCompletement` — sans
distinction personnelle/partagée cette fois, une politique de rétention s'appliquant à
toutes les données de l'instance.

## 3. Les fichiers, un par un

### `core/gouvernance/` (nouveau package)
`SessionPurgeService` (purge complète réutilisée par effacement et rétention),
`GouvernanceDonneesService` (effacement + export), `RetentionService` (balayage planifié),
`GouvernanceDonneesController` (`DELETE`/`GET .../export`), `JournalRgpd` + `TypeActionRgpd`
+ `JournalRgpdRepository`, `ExportDonneesUtilisateur` (DTO).

### Infrastructure (édités)
`StockageAudioPort`+`StockageAudioFichierLocal`, `StockageDocumentPort`+`StockageDocumentFichierLocal` :
`supprimerSession(UUID)`, suppression récursive du sous-dossier de session.
`RecherchePort`+`RechercheAzureAiSearch` : `supprimerDocumentsSession(UUID)` — Azure AI
Search n'offrant pas de "delete by query", recherche des identifiants par filtre puis
suppression par lot.

### Repositories (édités, pattern répété)
Un `deleteBySessionId(UUID)` ajouté sur `DocumentRepository`, `TranscriptionRepository`,
`ResumeRepository`, `CompteRenduRepository`, `ResumeCoursRepository`,
`IndexRechercheRepository`, `AudioChunkRepository`, `EngagementRepository`. Méthodes par
utilisateur sur `SeanceTutoratRepository`, `MaitriseNotionRepository`,
`MembreCouloirRepository`, `CouloirRepository`. Deux bulk updates ciblées :
`EngagementRepository.anonymiserResponsable` (JPQL) et
`TranscriptionRepository.anonymiserSegmentsLocuteur` (SQL natif, la table
`transcription_segments_locuteur` étant une `@ElementCollection`, pas une entité JPA).

### `Session.anonymiserCreateur()`, `FilMemoire.retirerSession()` (nouveaux)
Réutilisent des états déjà supportés par le domaine plutôt que d'inventer une nouvelle
sémantique (voir §2.2 et `docs/gouvernance-donnees.md` §6 pour la limite assumée sur
`resumeCumulatif`).

### Frontend — `ParametresCompteePage.tsx` (édité) + `api.ts` (édité)
Section "Mes données" : export (téléchargement JSON via `Blob`) et suppression de compte
(`window.confirm`, même pattern que la révocation d'empreinte vocale déjà en place).

## 4. Les tests

227/227 tests backend (210 existants + 17 nouveaux, répartis sur
`SessionPurgeServiceTest`, `GouvernanceDonneesServiceTest`, `RetentionServiceTest`). `mvn -B
verify` : `BUILD SUCCESS`, 0 finding SpotBugs/FindSecBugs, couverture maintenue. `npm run
build` + `npm run lint` : propres.

## 5. Comment on a vérifié en conditions réelles

Backend de vérification sur un port dédié : deux utilisateurs A et B, un couloir possédé par
A avec B comme membre, une session personnelle de A (avec un chunk audio réel sur disque) et
une session de A rattachée au couloir. Après `DELETE /api/v1/utilisateurs/moi` par A :

- Session personnelle : `404` (purgée), fichier disque du chunk confirmé disparu.
- Session partagée : toujours accessible, `createurId: null` confirmé dans la réponse.
- Couloir : toujours accessible, `proprietaireId` transféré à B.
- Compte de A : `0` ligne en base (`select count(*) from utilisateurs where email=...`).
- `journal_rgpd` : une ligne `EFFACEMENT_COMPTE` avec le bon `utilisateur_cible_id`.
- `GET /export` (avant l'effacement) : JSON conforme, `empreinteVocale.presente: false`,
  aucune référence externe biométrique exposée.

## 6. Limites connues, assumées, pas corrigées ici

- **Chiffrement au repos/transit** : ni implémenté, ni encore documenté dans
  `docs/deploiement.md` (vérifié explicitement) — vrai trou infrastructure, pas un renvoi de
  façade.
- **Consentement explicite à l'enregistrement des participants** : différé, brique séparée.
- **Aucun rôle admin** : effacement/export strictement self-service ; pas d'effacement au nom
  d'un autre utilisateur, pas d'endpoint de consultation du journal RGPD.
- **`Matiere.createurId` non anonymisé** (`nullable = false`, changerait un schéma pour un
  champ à faible sensibilité).
- **`FilMemoire.resumeCumulatif` non retouché** après le retrait d'une session — régénérer
  le résumé cumulatif demanderait une vraie opération IA, hors de proportion ici.
- **File de rétention sans verrou distribué** — cohérent avec la même limite déjà assumée en
  phase-12 pour le rattrapage des transcriptions (mono-instance, risque faible).

## 7. Pour reprendre seul

- Code de référence exact : `git checkout phase-13-gouvernance-donnees`
- Point d'entrée pour toute nouvelle donnée personnelle future : l'ajouter aux deux endroits
  symétriques `GouvernanceDonneesService.effacerCompte` (suppression/anonymisation) et
  `exporterDonnees` (lecture) — garder les deux parcours alignés.
- Prochaine direction possible : chiffrement au repos/transit (brique infrastructure),
  consentement à l'enregistrement des participants, rôle admin si un besoin de gestion pour
  compte d'autrui se confirme.

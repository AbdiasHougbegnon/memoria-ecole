# Ciblage précis du responsable identifié (boucle fermée) — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-4-ciblage-responsable-engagements
```
Ce tag pointe sur le commit `5655d8f`, vérifié avec de vrais appels Azure OpenAI (pas de mock).

---

## 1. Le besoin

Limite explicitement notée dans le code (`Engagement.java`) et documentée deux fois cette session (docs des rappels d'engagements et de la boucle fermée) : `Engagement.responsable` (et `ActionCompteRendu.responsable`) n'était qu'un label de diarization ("Intervenant 2"), jamais lié à un vrai compte `Utilisateur` — donc les rappels et la notification de complétion visaient tous les participants de la session, pas spécifiquement la bonne personne.

En creusant pour fermer cette limite une fois la reconnaissance de voix récurrente disponible (tag `phase-3-reconnaissance-locuteur`), un bug de fond est apparu : **le transcript envoyé à l'IA pour générer le compte rendu ne contenait jamais de repères "Intervenant N"** — `CompteRenduService` concaténait `Transcription.getTexte()` en texte brut, sans aucune étiquette de locuteur, alors que la consigne envoyée à Azure OpenAI (`GenerateurCompteRenduAzureOpenAI`) lui demandait justement de réutiliser ces repères pour désigner un responsable. L'IA semblait donc halluciner des noms trouvés dans le contenu de la conversation plutôt que de reprendre une étiquette réelle qu'elle n'avait jamais reçue. Corriger le ciblage du responsable nécessitait donc d'abord de corriger cette cause racine.

## 2. Les décisions de conception

### 2.1 — Étiqueter le transcript avant de le soumettre à l'IA, pas après

`ConstructeurTranscriptLabelise` (nouveau, `com.memoria.core.transcription`) construit un texte où chaque ligne commence par une étiquette suivie de `" : "` — un vrai nom si le segment a été identifié par la reconnaissance de voix récurrente, sinon un repère générique stable **au sein d'un seul appel** ("Intervenant A", "Intervenant B"...). C'est un utilitaire du moteur commun (`core.transcription`), pas d'Entreprise : la même construction sert potentiellement au résumé École.

### 2.2 — Repère local à l'appel, jamais une identité globale

Le regroupement se fait par `(numeroSequence, locuteur)`, jamais par `locuteur` seul à l'échelle de la session — cohérent avec la limite déjà documentée dans `IdentificationLocuteurService` (Azure renumérote les locuteurs par appel HTTP, un même index peut désigner des personnes différentes d'un chunk à l'autre). Une personne non identifiée peut donc recevoir plusieurs étiquettes "Intervenant X" différentes selon le chunk — limite assumée, pas corrigée ici.

### 2.3 — `responsableUtilisateurId` résolu automatiquement, pas saisi

Plutôt que de faire ressaisir un lien vers un compte quelque part dans l'UI, `ConstructeurTranscriptLabelise.Resultat` renvoie une `Map<String, UUID>` (étiquette → utilisateur). Quand l'IA renvoie un `responsable` qui correspond exactement à une étiquette de cette map, `CompteRenduService` résout automatiquement `responsableUtilisateurId` — aucune action utilisateur requise, le lien apparaît "gratuitement" dès qu'un locuteur a été identifié par ailleurs.

### 2.4 — Consigne IA durcie : reprendre l'étiquette caractère pour caractère

La consigne d'Azure OpenAI est passée de *"des repères Intervenant N... reprends-les tels quels"* à une formulation plus stricte : *"reprends EXACTEMENT l'étiquette telle qu'elle apparaît... N'invente jamais un nom qui n'apparaît pas comme étiquette, même s'il est mentionné ailleurs dans le contenu de la conversation."* Nécessaire car un vrai nom (ex. "Claire Dubois") ressemble à un nom qu'on pourrait légitimement mentionner dans une phrase ("Claire Dubois a dit qu'elle s'en occupait") — sans cette précision, l'IA pourrait confondre une mention dans le contenu avec l'étiquette du locuteur.

### 2.5 — Repli sur le comportement historique, jamais de régression silencieuse

`EngagementService.notifierCompletion` et `RappelEngagementService` ciblent `responsableUtilisateurId` **quand il est connu**, et retombent sur `SessionService.resoudreEmailsParticipants` (tous les participants) sinon — le comportement d'avant cette brique reste intact pour tout engagement dont le locuteur n'a pas été identifié, y compris les engagements créés avant cette brique (`responsableUtilisateurId` y est `null` par construction).

## 3. Les fichiers backend, un par un

### `ConstructeurTranscriptLabelise.java` — nouveau, `com.memoria.core.transcription`

```java
public record Resultat(String texte, Map<String, UUID> utilisateurIdParLabel) {}

public static Resultat construire(List<Transcription> transcriptions, Function<UUID, String> resolveurNom) {
    // pour chaque segment : etiquette = nom reel si identifie (resolveurNom),
    // sinon "Intervenant X" stable pour (numeroSequence, locuteur) au sein
    // de cet appel seulement -- jamais une identite globale a la session.
}
```
Classe utilitaire statique pure (pas de Spring), donc directement testable sans mock.

### `ActionCompteRendu.java` / `Engagement.java` — gagnent `responsableUtilisateurId`

```java
@Column(name = "responsable_utilisateur_id")
private UUID responsableUtilisateurId;
```
Nullable, colonne ajoutée via `ddl-auto=update` — sans risque ici (nullable par conception, pas de valeur historique à combler).

### `CompteRenduService.java` — étiquette avant de générer, résout après

```java
ConstructeurTranscriptLabelise.Resultat transcriptLabelise =
        ConstructeurTranscriptLabelise.construire(transcriptionsReussies, this::nomUtilisateur);

CompteRenduGenere genere = generateurCompteRendu.genererCompteRendu(transcriptLabelise.texte());
List<ActionCompteRendu> actions = genere.actions().stream()
        .map(action -> new ActionCompteRendu(
                action.description(), action.responsable(), action.echeance(),
                action.responsable() == null ? null : transcriptLabelise.utilisateurIdParLabel().get(action.responsable())))
        .toList();
```
Nouvelle dépendance : `UtilisateurRepository` (déjà existant, `core.auth`) pour résoudre `nomAffichage()`.

### `GenerateurCompteRenduAzureOpenAI.java` — consigne durcie

Voir §2.4 — seul le texte de la `CONSIGNE` change, aucune logique.

### `EngagementService.java` / `RappelEngagementService.java` — ciblage avec repli

```java
private List<String> resoudreDestinataires(Engagement engagement) {
    if (engagement.getResponsableUtilisateurId() != null) {
        return utilisateurRepository.findById(engagement.getResponsableUtilisateurId())
                .map(Utilisateur::getEmail)
                .map(List::of)
                .orElseGet(() -> sessionService.resoudreEmailsParticipants(engagement.getSessionId()));
    }
    return sessionService.resoudreEmailsParticipants(engagement.getSessionId());
}
```
Même méthode dupliquée à l'identique dans les deux services (pas d'abstraction commune extraite pour deux appelants seulement — cohérent avec la discipline "pas d'abstraction prématurée" du projet). Nouvelle dépendance dans les deux : `UtilisateurRepository`.

## 4. Le frontend

Aucun changement — le ciblage plus précis du destinataire est un effet de bord invisible côté UI (l'utilisateur voit les mêmes écrans qu'avant, seul le destinataire réel de l'email change en coulisse).

## 5. Les tests

| Fichier | Changement |
|---|---|
| `CompteRenduServiceTest` | +1 : `obtenirOuGenererCompteRendu_resout_le_responsable_identifie` — un segment identifié, l'étiquette réutilisée par l'IA est bien liée à `responsableUtilisateurId`. |
| `EngagementServiceTest` | +2 : `surCompteRenduGenere_propage_le_responsable_identifie`, `terminer_notifie_precisement_le_responsable_identifie_plutot_que_tous_les_participants`. |
| `RappelEngagementServiceTest` | +1 : `verifierEcheances_cible_precisement_le_responsable_identifie`, avec une assertion `verify(sessionService, never()).resoudreEmailsParticipants(...)` — preuve que le repli n'est **pas** déclenché quand le responsable est connu. |

Pas de fichier de test dédié pour `ConstructeurTranscriptLabelise` lui-même — son comportement (étiquetage, résolution de la map) est couvert indirectement via les tests `CompteRenduServiceTest` ci-dessus, qui exercent le chemin complet transcript → IA → résolution.

`cd backend && mvn test` — **162/162 tests** passent (158 précédents + 4 nouveaux). `cd frontend && npx tsc --noEmit` — aucune erreur (aucun fichier frontend modifié).

## 6. Comment on a vérifié en conditions réelles

Vérifié avec de vrais appels Azure OpenAI (pas de mock, pas de factice) : un locuteur enrôlé et identifié via la reconnaissance de voix récurrente, l'IA réutilise correctement son vrai nom comme étiquette de responsable (pas un nom inventé à partir du contenu de la conversation) ; dans une session à deux participants, un seul email de notification part au responsable identifié, l'autre participant ne le reçoit pas.

## 7. Limites connues, assumées, pas corrigées ici

- **Repère local à un seul appel IA, jamais une identité globale** — un locuteur non identifié parlant dans plusieurs chunks peut recevoir plusieurs étiquettes "Intervenant X" différentes, aucun recollement inter-chunks n'est tenté (même limite que `IdentificationLocuteurService`).
- **Dépend entièrement de la reconnaissance de voix récurrente** — sans profil vocal enrôlé pour la personne concernée, le comportement reste celui d'avant cette brique (repli sur tous les participants).
- **Pas de test dédié pour `ConstructeurTranscriptLabelise`** — couvert seulement indirectement, voir §5.
- **Duplication volontaire de `resoudreDestinataires`** entre `EngagementService` et `RappelEngagementService` — pas d'abstraction commune pour deux appelants seulement.

## 8. Pour reprendre seul

- Code de référence exact : `git checkout phase-4-ciblage-responsable-engagements`
- Chemin de bout en bout : `CompteRenduService.obtenirOuGenererCompteRendu` → `ConstructeurTranscriptLabelise.construire` (étiquette chaque segment) → `GenerateurCompteRenduAzureOpenAI` (reprend l'étiquette exacte) → `ActionCompteRendu.responsableUtilisateurId` résolu → `EngagementService.creerEngagements` (propage vers `Engagement`) → `EngagementService.resoudreDestinataires` / `RappelEngagementService.resoudreDestinataires` (ciblent précisément si connu, sinon repli historique).
- Le chantier "rappels + boucle fermée + ciblage précis" côté engagements est maintenant complet au sens du master prompt. Prochaine direction retenue après cette brique : séparation École/Entreprise en modules distincts (voir `phase-6-modules-separes.md`).

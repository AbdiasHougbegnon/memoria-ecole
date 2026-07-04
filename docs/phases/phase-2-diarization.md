# Phase 2 : la diarization (identification des locuteurs) — comment on l'a construite

**Pour revenir exactement à cet état du code :**
```
git checkout phase-2-diarization
```
Ce tag pointe sur le commit `1f97657`, au moment précis où la diarization a été vérifiée en fonctionnement réel.

Ce document explique en détail *comment* on a construit cette fonctionnalité, pas juste *ce qu'elle fait* — pour que tu puisses reprendre seul, côté backend comme côté frontend, et comprendre pourquoi chaque chose est faite ainsi.

---

## 1. Le besoin

Jusqu'ici, `TranscripteurAzureSpeech` appelait l'API de reconnaissance "courte durée" d'Azure Speech (`/speech/recognition/conversation/cognitiveservices/v1`). Cette API est faite pour transcrire *une* voix — elle ne sait pas distinguer plusieurs personnes qui parlent dans le même enregistrement. Pour du transcript de réunion ou de cours utile, il faut savoir *qui* a dit quoi, pas juste *ce qui* a été dit.

C'est la fonctionnalité "speaker diarization" qu'on voulait ajouter.

---

## 2. Pourquoi il a fallu chercher avant de coder

La première règle du projet (dans `CLAUDE.md`) est de concevoir avant de coder. Ici, il y avait un piège qu'on avait déjà rencontré une fois avec Azure OpenAI (une API qui ressemble à ce qu'on attend, mais qui ne fonctionne pas comme les tutoriels génériques le laissent penser) — donc plutôt que de deviner le format de la requête et écrire du code Java dessus tout de suite, on a d'abord **testé directement avec `curl`**, en dehors de toute application, pour découvrir le vrai comportement de l'API.

### Premier essai — raté, silencieusement

```bash
curl -X POST \
  "https://{region}.api.cognitive.microsoft.com/speechtotext/transcriptions:transcribe?api-version=2024-11-15" \
  -H "Ocp-Apim-Subscription-Key: {cle}" \
  -F "audio=@fichier.wav;type=audio/wav" \
  -F "definition=@definition.json;type=application/json"
```

Résultat : **statut 200**, une vraie transcription revenait... mais **aucune information de locuteur** dans la réponse, malgré le paramètre de diarization envoyé dans `definition.json`. Pas d'erreur, juste silencieusement ignoré. C'est le genre de bug le plus difficile à repérer : tout a l'air de marcher.

### Recherche de la documentation officielle

Plutôt que de continuer à essayer des variantes au hasard, on a cherché la doc Microsoft Learn ("fast transcription API diarization"). Deux choses en sont ressorties :
1. Le bon format JSON était déjà celui utilisé : `{"diarization": {"enabled": true, "maxSpeakers": N}}`.
2. L'exemple officiel de la doc envoyait `definition` **directement comme texte dans le formulaire**, pas comme fichier joint :
   ```bash
   --form 'definition="{\"locales\":[\"en-US\"], \"diarization\": {...}}"'
   ```

### Le vrai test, qui a marché

```bash
curl -X POST \
  "https://{region}.api.cognitive.microsoft.com/speechtotext/transcriptions:transcribe?api-version=2025-10-15" \
  -H "Ocp-Apim-Subscription-Key: {cle}" \
  -F "audio=@fichier.wav;type=audio/wav" \
  -F 'definition={"locales":["fr-FR"],"diarization":{"maxSpeakers":4,"enabled":true}}'
```

Cette fois, la réponse contenait bien `"speaker":1`, `"speaker":2`... **La différence tenait à un seul détail** : envoyer `definition` comme *valeur de champ* (`-F 'definition={...}'`) plutôt que comme *fichier attaché* (`-F "definition=@fichier.json"`). Azure traite visiblement ces deux formes différemment dans le multipart, sans le signaler par une erreur.

**La leçon à retenir pour la suite du projet** : quand une API Azure "a l'air de marcher" (200, réponse plausible) mais qu'un comportement attendu manque, ne pas supposer que c'est impossible — vérifier le format exact de la requête avant d'accuser l'API ou le service.

---

## 3. Le contrat de l'API, une fois découvert

**Endpoint** : `POST https://{region}.api.cognitive.microsoft.com/speechtotext/transcriptions:transcribe?api-version=2025-10-15`

**En-têtes** : `Ocp-Apim-Subscription-Key: {cle}`

**Corps** : `multipart/form-data` avec deux parties :
- `audio` — le fichier audio (fichier joint, avec nom de fichier)
- `definition` — **texte brut**, pas un fichier :
  ```json
  {"locales": ["fr-FR"], "diarization": {"maxSpeakers": 4, "enabled": true}}
  ```

**Réponse** (extrait) :
```json
{
  "combinedPhrases": [{ "text": "texte complet de tout le chunk" }],
  "phrases": [
    {
      "speaker": 1,
      "offsetMilliseconds": 80,
      "durationMilliseconds": 1800,
      "text": "Bonjour, je m'appelle Marie.",
      "words": [...],
      "locale": "fr-FR",
      "confidence": 0.90
    },
    { "speaker": 2, "...": "..." }
  ]
}
```
Le champ `speaker` est un entier (1, 2, 3...), présent uniquement quand la diarization est activée. C'est ce champ qui permet de savoir qui a dit quoi.

---

## 4. Les changements côté backend, fichier par fichier

Tous dans `backend/src/main/java/com/memoria/core/transcription/`.

### `SegmentLocuteur.java` *(nouveau)*

Une classe `@Embeddable` — pas une entité à part entière avec son propre repository, juste un petit objet de valeur toujours chargé avec son `Transcription` parent :
```java
@Embeddable
public class SegmentLocuteur {
    private int locuteur;
    private String texte;
    private long offsetMillisecondes;
    private long dureeMillisecondes;
}
```
Un `SegmentLocuteur` = une prise de parole (un `phrase` de la réponse Azure). Un chunk de 30s peut contenir plusieurs prises de parole de personnes différentes, d'où la liste.

### `ResultatTranscription.java` *(nouveau)*

```java
public record ResultatTranscription(String texteComplet, List<SegmentLocuteur> segments) {}
```
Avant, `TranscripteurPort.transcrire()` renvoyait juste un `String` (le texte). Maintenant qu'on veut *aussi* la liste des locuteurs, on a besoin de renvoyer les deux informations ensemble — d'où ce petit "paquet" de résultat.

### `TranscripteurPort.java` *(modifié)*

```java
public interface TranscripteurPort {
    ResultatTranscription transcrire(byte[] audio); // avant : String transcrire(byte[] audio)
}
```
Un seul changement de type de retour. Comme c'est une interface (un "port" au sens de l'architecture du projet), ce changement force à mettre à jour son unique implémentation (`TranscripteurAzureSpeech`) et son unique appelant (`TranscriptionService`) — exactement l'effet recherché : le compilateur nous dit où adapter le code.

### `TranscripteurAzureSpeech.java` *(récrit en grande partie)*

C'est le fichier qui change le plus. Trois choses importantes à comprendre :

**a) La construction manuelle du corps multipart.** Le `HttpClient` de Java (`java.net.http`) n'a pas de méthode intégrée pour construire une requête `multipart/form-data` — contrairement à `curl` qui le fait avec juste `-F`. Il faut donc fabriquer le corps de la requête à la main : une suite de sections séparées par une "frontière" (`boundary`), chacune avec ses propres en-têtes :
```java
--{frontiere}
Content-Disposition: form-data; name="audio"; filename="audio.wav"
Content-Type: application/octet-stream

{les octets du fichier audio}
--{frontiere}
Content-Disposition: form-data; name="definition"

{"locales":["fr-FR"],"diarization":{"maxSpeakers":4,"enabled":true}}
--{frontiere}--
```
La méthode `construireCorpsMultipart()` écrit exactement ça dans un `ByteArrayOutputStream`. La frontière (`frontiere`) est une chaîne aléatoire (`UUID`) générée à chaque appel, pour être sûr qu'elle n'apparaît jamais par hasard dans l'audio ou le JSON.

**b) La simplification du format audio.** L'ancienne API avait besoin qu'on lui dise explicitement quel format audio on envoyait (`Content-Type: audio/wav; codecs=...`), et le code devait donc inspecter les octets pour deviner s'il s'agissait de WAV ou d'Ogg (`estWav()`, `estOgg()`, `determinerTypeContenu()`). La nouvelle API **détecte le format toute seule** à partir du fichier — tout ce code de détection a donc été supprimé, le code est plus simple qu'avant.

**c) L'extraction du résultat.** La méthode `extraireResultat()` lit la réponse JSON : `combinedPhrases[0].text` devient `texteComplet`, et chaque élément de `phrases[]` devient un `SegmentLocuteur`.

### `Transcription.java` *(entité, modifiée)*

Ajout d'un champ :
```java
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(name = "transcription_segments_locuteur", joinColumns = @JoinColumn(name = "transcription_id"))
@OrderColumn(name = "position")
private List<SegmentLocuteur> segmentsLocuteur;
```
Deux détails qui ne sont pas là par hasard :
- **`fetch = FetchType.EAGER`** — sans ça, Hibernate charge cette liste "paresseusement" (à la demande), et si on essaie de la lire après que la session de base de données soit fermée (typiquement : au moment de sérialiser en JSON pour répondre au navigateur), ça plante. **On s'est déjà fait avoir par ce bug exact** avec l'entité `Resume` en Phase 1 — cette fois on met `EAGER` dès le départ.
- **`@OrderColumn`** — garantit que les segments reviennent toujours dans l'ordre chronologique où ils ont été dits, pas dans un ordre arbitraire.

L'ancien constructeur `Transcription(sessionId, numero, texte, statut)` a été gardé (il délègue au nouveau avec une liste vide), pour ne rien casser dans le code existant qui l'utilisait encore (les tests, notamment).

### `TranscriptionService.java` *(modifié a minima)*

Seul changement : `transcripteur.transcrire(...)` renvoie maintenant un `ResultatTranscription`, donc on récupère `resultat.texteComplet()` et `resultat.segments()` au lieu d'un simple texte, et on les passe tous les deux à `enregistrer()`. Le reste (gestion des échecs, déclenchement de l'événement de fin de transcription) est inchangé.

### `SegmentLocuteurResponse.java` *(nouveau)* et `TranscriptionResponse.java` *(modifié)*

Même principe que pour `Session` en Phase 1 : on ne renvoie jamais une entité JPA directement au frontend, on la traduit en DTO ("Data Transfer Object") :
```java
public record SegmentLocuteurResponse(int locuteur, String texte, long offsetMillisecondes, long dureeMillisecondes) {
    public static SegmentLocuteurResponse depuis(SegmentLocuteur segment) { ... }
}
```
`TranscriptionResponse` a maintenant un champ `segmentsLocuteur: List<SegmentLocuteurResponse>` en plus des champs existants.

### `TranscriptionController.java` — **inchangé**

Aucune modification nécessaire ! Il fait déjà `TranscriptionResponse::depuis` sur chaque `Transcription` — comme `depuis()` inclut maintenant les segments, le contrôleur les expose automatiquement sans le savoir. C'est l'intérêt du découpage en couches : le contrôleur ne connaît que la forme du DTO, pas les détails de ce qu'il contient.

### `application.properties`

```properties
azure.speech.max-locuteurs=4
```
remplace
```properties
azure.speech.content-type=audio/webm; codecs=opus
```
(qui ne sert plus, la nouvelle API détectant le format toute seule). `max-locuteurs` correspond au `maxSpeakers` envoyé à Azure — le nombre maximum de personnes que l'algorithme doit essayer de distinguer (Azure autorise de 2 à 35).

---

## 5. Les changements côté frontend, et le lien avec le backend

Le point essentiel à comprendre : **il n'y a pas de contrat partagé automatique entre le backend Java et le frontend TypeScript.** `SegmentLocuteurResponse` (Java) et `SegmentLocuteur` (TypeScript, dans `types.ts`) sont deux déclarations complètement séparées, dans deux langages différents, et **c'est à nous de les garder synchronisées à la main**. Si demain on renomme un champ côté Java sans le faire côté TypeScript, rien ne le détecte automatiquement (TypeScript ne connaît que ce qu'on lui écrit) — seul un test manuel ou une réponse HTTP qui ne correspond plus au type déclaré le révélera.

### `frontend/src/types.ts` *(modifié)*

```typescript
export interface SegmentLocuteur {
  locuteur: number
  texte: string
  offsetMillisecondes: number
  dureeMillisecondes: number
}

export interface TranscriptionSegment {
  // ... champs existants ...
  segmentsLocuteur: SegmentLocuteur[]
}
```
Remarque : les noms de champs (`locuteur`, `offsetMillisecondes`...) sont recopiés **exactement** depuis `SegmentLocuteurResponse.java`, parce que Jackson (côté Java) sérialise les enregistrements Java en JSON en gardant le nom des champs tel quel, et `fetch()` (côté navigateur) désérialise ce JSON directement vers l'interface TypeScript sans vérification — une faute de frappe dans un nom de champ ne provoquerait aucune erreur, juste une valeur `undefined` silencieuse.

### `frontend/src/pages/SessionDetailPage.tsx` *(modifié)*

Dans la boucle qui affiche chaque segment de transcription, on a ajouté une branche :
```tsx
{segment.segmentsLocuteur.length > 0 ? (
  <div className="mt-1 flex flex-col gap-1">
    {segment.segmentsLocuteur.map((locuteur, index) => (
      <p key={index}>
        <span className="mr-2 font-medium text-slate-500">Intervenant {locuteur.locuteur}</span>
        <span className="text-slate-800">{locuteur.texte}</span>
      </p>
    ))}
  </div>
) : (
  <span className="text-slate-800">{segment.texte}</span>
)}
```
Si `segmentsLocuteur` contient des éléments, on affiche chaque prise de parole séparément avec son étiquette "Intervenant N". Sinon (cas d'un ancien enregistrement fait avant ce changement, ou d'un texte de repli sans diarization), on retombe sur l'affichage du texte complet comme avant — **aucune session existante n'est cassée par ce changement**.

---

## 6. Comment on a vérifié que ça marchait vraiment

Toujours suivre la même règle du projet : ne jamais déclarer un succès sans l'avoir observé en conditions réelles.

**Test à 2 voix.** On a généré un fichier audio avec deux voix de synthèse Windows différentes (Hortense en français, Zira en anglais), simulant "Marie" puis "Paul" qui se présentent. Résultat : `speaker:1` pour Marie, `speaker:2` pour Paul — testé jusqu'à l'affichage réel dans le navigateur via Playwright (capture d'écran à l'appui).

**Test à 3 voix.** On a réutilisé la même voix (Hortense) pour deux "personnes" différentes (juste à une vitesse de parole différente), plus une troisième voix distincte (Zira) pour la troisième personne. Résultat : les deux premières ont été fusionnées en un seul locuteur, la troisième est ressortie séparément. **Ce n'est pas un bug** — ça prouve que la diarization distingue les gens par les caractéristiques acoustiques réelles de la voix (timbre, hauteur), pas par la vitesse d'élocution. Avec de vraies personnes différentes, chacune a une voix suffisamment distincte pour être bien séparée.

---

## 7. Limite connue, assumée, pas corrigée ici

Chaque chunk de 30 secondes est transcrit **indépendamment** des autres (c'est le principe du pipeline établi en Phase 1). Ça veut dire que si le chunk n°0 dit `speaker:1` pour Marie, rien ne garantit que le chunk n°1 dira aussi `speaker:1` pour Marie — Azure pourrait tout aussi bien l'appeler `speaker:2` dans ce chunk-là, puisque chaque appel API est traité comme une conversation à part.

**Donc** : la numérotation "Intervenant N" est fiable *à l'intérieur* d'un même chunk de 30s, mais pas garantie stable *sur toute la session*. Résoudre ça proprement demande la reconnaissance vocale récurrente (reconnaître "c'est la voix de Marie" indépendamment de l'ordre des chunks) — une fonctionnalité explicitement prévue pour la **Phase 3**, pas construite ici.

---

## 8. Pour reprendre seul

- Le code de référence exact de cette étape : `git checkout phase-2-diarization`
- Pour tester à nouveau l'API Azure directement (sans passer par l'application), la commande `curl` de la section 3 ci-dessus fonctionne telle quelle — utile si Azure change encore une fois le comportement de son API.
- Si tu veux ajuster le nombre maximum de locuteurs détectés, c'est uniquement `azure.speech.max-locuteurs` dans `application.properties` — aucun code à toucher.
- Si tu veux comprendre où l'information passe entre le clic dans le navigateur et l'affichage à l'écran, l'ordre est toujours le même que celui expliqué en Phase 1 : navigateur → `SessionDetailPage.tsx` → `api.ts` (`obtenirTranscriptions`) → `TranscriptionController` → `TranscriptionService` → base de données, puis retour dans l'autre sens en DTO.

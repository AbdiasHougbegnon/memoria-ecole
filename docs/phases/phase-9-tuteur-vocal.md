# Tuteur vocal École — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-9-tuteur-vocal
```

---

## 1. Le besoin

Le master prompt décrit le tuteur vocal comme *"la fonctionnalité la plus différenciante"*
de Memoria École (section École, Phase 5 "fonctionnalités avancées")  : *"l'IA raconte le
cours en dialogue vocal naturel comme un camarade présent, vise 100% de maîtrise, ne lâche
pas une notion tant qu'elle n'est pas comprise (change d'approche, analogies,
reformulation), suit un score de maîtrise par notion mis à jour selon la qualité des
réponses, propose un mode exercices. La seule sortie est l'arrêt par l'utilisateur, avec
sauvegarde de l'état."* Les fiches techniques des deux briques précédentes (observabilité,
gates de qualité) citaient explicitement cette fonctionnalité comme la prochaine direction
possible.

État avant cette brique : aucun modèle Matière/Séance/Promotion/Notion n'existait (seul
`ResumeCours.notions`, un `@Embeddable` `NotionCours{terme, definition}` sans id ni suivi
de maîtrise, par session). Aucun TTS implémenté dans le projet. Le STT existant
(`TranscripteurPort`/`TranscripteurAzureSpeech`) était réutilisable tel quel. Pas de rôle
élève/enseignant dans le modèle.

## 2. Les décisions de conception

### 2.1 — Dialogue tour par tour, pas de streaming temps réel

Décidé explicitement avec l'utilisateur avant de concevoir le reste : l'étudiant enregistre
une réponse courte (appui long façon talkie-walkie), elle est transcrite, le tuteur répond
en texte + audio synthétisé joué automatiquement. Le streaming temps réel (parler et être
interrompu en direct) demanderait une infrastructure bien plus lourde (WebSocket, détection
d'activité vocale) — hors de portée d'un seul incrément.

### 2.2 — `Couloir` réutilisé comme "Promotion", aucune nouvelle entité créée

Découverte de recherche déterminante : `Couloir` (`core.couloir`) est déjà, dans sa propre
documentation (`docs/phases/phase-5-couloirs-classe.md`), *"un espace partagé par
promotion"* — le master prompt lui-même décrit le "propriétaire de couloir" comme un rôle
pour *"un espace de classe ou d'équipe"*. Créer une entité `Promotion` séparée aurait
dupliqué `Couloir`. `Matiere`/`Seance` référencent donc `Couloir` par `couloirId` (FK brute,
même convention que `Session.couloirId`), sans nouvelle notion de rôle enseignant/élève :
le propriétaire du couloir (`Couloir.proprietaireId`) joue le rôle enseignant, les
`MembreCouloir` jouent le rôle élève — réutilisation directe, pas de duplication.

### 2.3 — `NiveauMaitrise` qualitatif (3 valeurs), pas un score numérique

`NON_ABORDEE / EN_COURS / MAITRISEE` plutôt qu'un score 0-100. L'évaluation d'un LLM
tour par tour ("cette réponse montre-t-elle une compréhension solide ?") est
intrinsèquement qualitative — un score numérique précis serait une fausse précision.
Mappe directement sur la condition d'arrêt du master prompt : ne pas avancer avant
`MAITRISEE`.

### 2.4 — Pas d'audio persisté, resynthétisé à la demande

`TourDialogueTutorat` ne stocke que le texte. L'audio du tuteur est régénéré à chaque
lecture via un endpoint dédié (`GET /tutorat/{id}/audio/{tourId}`), cohérent avec
l'absence de stockage de blobs ailleurs dans le projet en dehors du cas déjà géré (fichiers
audio de session).

### 2.5 — "Change d'approche, analogies, reformulation" : prompt engineering, pas une machine à états

Toute la logique pédagogique nuancée du master prompt vit dans le prompt système de
`GenerateurTourTuteurAzureOpenAI`, pas dans du code Java explicite (pas de suivi des
"approches déjà tentées", pas de bibliothèque d'analogies). Choix assumé pour un premier
incrément — voir §6 pour la limite documentée, et §5.2 pour la preuve que ça fonctionne
réellement en pratique.

## 3. Les fichiers, un par un

### `com.memoria.ecole.matiere` (nouveau)
`Matiere` (`id, nom, couloirId, createurId, dateCreation`), `MatiereRepository`,
`MatiereService` (autorisation : propriétaire du couloir, réutilise
`CouloirService`/`PasProprietaireDuCouloirException` directement), `MatiereController`,
`MatiereResponse`.

### `com.memoria.ecole.notion` (nouveau)
`Notion` (`id, matiereId, terme, definition, ordre, dateCreation` — entité de premier
ordre, contrairement à l'embeddable `NotionCours`), `NiveauMaitrise` (enum),
`MaitriseNotion` (une ligne par `(notion, étudiant)`, même style que `MembreCouloir`),
`NotionService`, `NotionController`, `NotionResponse`.

### `com.memoria.ecole.seance` (nouveau)
`Seance` (`id, titre, matiereId, couloirId` dénormalisé, `sessionId` optionnel non utilisé
en v1), `SeanceNotion` (jointure ordonnée), `SeanceService` (remplace entièrement les
notions rattachées à chaque appel, `@Transactional`), `SeanceController` (inclut
`GET /seances/{id}/maitrise` pour les pastilles de progression), `SeanceResponse`.

### `com.memoria.ecole.tuteurvocal` (nouveau)
`SeanceTutorat` (l'état "sauvegardé" — `notionCouranteId`, `statut`, `modeExercice`),
`TourDialogueTutorat` (texte seul, pas d'audio), `SynthetiseurVocalPort` +
`SynthetiseurVocalAzure` (TTS, première implémentation TTS du projet, miroir de
`TranscripteurAzureSpeech`), `GenerateurTourTuteurPort` + `GenerateurTourTuteurAzureOpenAI`
(miroir de `GenerateurResumeCoursAzureOpenAI`, prompt système portant la pédagogie),
`TuteurVocalService` (orchestration complète : STT → IA → mise à jour de maîtrise → avance
de notion), `TuteurVocalController` (inclut l'endpoint audio à la demande).

### `SecurityConfig.java` / `GestionnaireExceptionsApi.java` (édités)
Nouvelles routes `/api/v1/{matieres,seances,tutorat}/**` gardées `MODULE_ECOLE`. Nouvelles
exceptions mappées ; `PasMembreDuCouloirException`/`PasProprietaireDuCouloirException`
réutilisées telles quelles (déjà mappées).

### Frontend (nouveau)
`pages/MatieresPage.tsx`, `pages/MatiereDetailPage.tsx` (gestion notions/séances),
`pages/SeanceDetailPage.tsx` (rattachement des notions + démarrage du tutorat),
`pages/TuteurVocalPage.tsx` (vue conversation), `hooks/useTutorRecorder.ts` (capture
micro *push-to-talk*, différent du chunking 30s de `Recorder.tsx`). `api.ts` étendu.
Point d'attention géré : `<audio src=...>` direct n'aurait pas fonctionné (le navigateur
n'attache pas l'en-tête `Authorization` à une requête déclenchée par une balise média) —
l'audio est récupéré via `fetch` authentifié puis joué via une URL de blob
(`obtenirAudioTutorat` dans `api.ts`).

## 4. Les tests

29 nouveaux tests Mockito (convention `CouloirServiceTest` : pas de contexte Spring,
`@Mock` par dépendance, AssertJ, noms français) : 4 `MatiereServiceTest`, 8
`NotionServiceTest`, 6 `SeanceServiceTest`, 11 `TuteurVocalServiceTest` (couvrant : choix de
la première notion, reprise idempotente, toutes notions déjà maîtrisées, notion pas encore
maîtrisée, avance à la notion suivante, dernière notion maîtrisée → fin, accès refusé,
séance non active, arrêt, régénération audio). Aucun test sur les adaptateurs HTTP
(`SynthetiseurVocalAzure`, `GenerateurTourTuteurAzureOpenAI`) — même convention que
`TranscripteurAzureSpeech`/`GenerateurResumeCoursAzureOpenAI`, qui n'en ont pas non plus.
**193/193 tests** passent (164 existants + 29 nouveaux). `mvn verify` (couverture + analyse
statique) : `BUILD SUCCESS`, 0 finding SpotBugs/FindSecBugs, seuil de couverture maintenu.

## 5. Comment on a vérifié en conditions réelles

### 5.1 — Démarrage du backend confirmé sans erreur de câblage

Le backend a démarré proprement (`Started CoreApplication`) avec tous les nouveaux beans
(4 services interdépendants, 2 nouveaux ports/adaptateurs). Une première tentative a
échoué sur "Port 8080 already in use" — pas un bug, un conflit avec une instance déjà
lancée par ailleurs sur la même machine ; redémarré sur le port 8099 pour confirmer.

### 5.2 — Pipeline complet vérifié avec de vrais appels Azure OpenAI et Azure Speech

Contrairement aux briques précédentes (observabilité, gates de qualité) où les identifiants
Azure réels manquaient, **cette session disposait de vrais identifiants Azure OpenAI et
Azure Speech** — la vérification est allée jusqu'au bout, pas seulement jusqu'aux tests
unitaires :

- Inscription, création d'un couloir, d'une matière ("Mathématiques"), d'une notion
  ("Dérivées"), d'une séance, rattachement de la notion à la séance : tout via de vrais
  appels REST, réponses cohérentes.
- `POST /seances/{id}/tutorat` : l'IA a généré une vraie phrase d'ouverture pédagogique
  sur les dérivées, orale et naturelle, exactement dans l'esprit demandé.
- `GET /tutorat/{id}/audio/{tourId}` : audio MP3 réel généré (66 Ko, confirmé
  `MPEG ADTS, layer III, 16 kHz` par `file`) — la synthèse vocale Azure Speech fonctionne.
- **Preuve concrète du comportement "ne lâche pas une notion, change d'approche"** : en
  réinjectant l'audio généré comme réponse étudiant (un test de plomberie, pas une vraie
  réponse cohérente), l'étudiant simulé a répété la définition mot pour mot. L'IA l'a
  détecté explicitement (*"Tu as repris la définition mot pour mot, donc je veux vérifier
  ta compréhension autrement"*) et a changé d'approche avec un exemple concret (une
  voiture, position/vitesse) au lieu de faire avancer la notion — `niveauMaitrise` est
  resté `EN_COURS`, pas `MAITRISEE`. C'est exactement le comportement demandé par le
  master prompt, observé en conditions réelles, pas supposé.
- `GET /tutorat/{id}` a renvoyé l'historique complet et cohérent des tours.
- `GET /seances/{id}/maitrise` a renvoyé la bonne carte de maîtrise.
- `POST /tutorat/{id}/arreter` a répondu `200`.

### 5.3 — Frontend : build et lint propres

`npm run build` (tsc + vite) et `npm run lint` (oxlint) passent sans erreur avec les 4
nouvelles pages et le nouveau hook.

## 6. Limites connues, assumées, pas corrigées ici

- **"Change d'approche, analogies, reformulation" est du prompt engineering**, pas une
  machine à états explicite — pas de suivi structuré des approches déjà tentées. Vérifié
  fonctionner dans les faits (§5.2), mais sans garde-fou si le modèle finit par se répéter.
- **Pas de garde-fou anti-blocage** : si l'IA ne juge jamais une notion `MAITRISEE`, la
  séance peut tourner indéfiniment (l'étudiant peut toujours arrêter manuellement).
  `MaitriseNotion.nombreTentatives` est suivi mais pas encore exploité pour une sortie
  automatique.
- **Le tuteur n'utilise que `Notion.definition`**, jamais `RechercheService` — pas
  d'ancrage dans la transcription réelle d'un cours enregistré. `Seance.sessionId` existe
  pour une extension future.
- **Mode exercices fixé au démarrage**, pas de bascule en cours de conversation.
- **Pas de suivi enseignant en direct** d'une séance de tutorat en cours (l'enseignant ne
  voit la maîtrise qu'a posteriori, via `GET /seances/{id}/maitrise`).
- **`GenerateurTourTuteurAzureOpenAI`/`SynthetiseurVocalAzure` sans test unitaire** — même
  convention que les autres adaptateurs HTTP de ce projet (aucun n'en a).

## 7. Pour reprendre seul

- Code de référence exact : `git checkout phase-9-tuteur-vocal`
- Chemin de bout en bout : Couloir (promotion) → Matière → Notion(s) → Séance (rattache
  des notions) → `POST /seances/{id}/tutorat` (démarre) → `POST /tutorat/{id}/reponse`
  (boucle tour par tour) → `POST /tutorat/{id}/arreter`.
- Depuis l'UI : ouvrir un couloir (module École) → "Matières & tuteur vocal" → créer une
  matière → ajouter des notions et une séance → dans la séance, rattacher des notions →
  "Démarrer le tutorat".
- Prochaine direction possible : garde-fou anti-blocage (utiliser
  `MaitriseNotion.nombreTentatives`), ancrage dans `RechercheService` via
  `Seance.sessionId`, ou un vrai modèle de suivi enseignant en direct.

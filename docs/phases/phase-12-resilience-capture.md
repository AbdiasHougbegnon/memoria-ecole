# Résilience de la capture audio — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-12-resilience-capture
```

---

## 1. Le besoin

Le master prompt place la fiabilité avant les fonctionnalités : *"La vraie difficulté n'est
pas d'ajouter des fonctions, c'est d'atteindre un niveau de fiabilité où une entreprise peut
se reposer sur le produit."* Sa section Résilience liste des scénarios explicites jamais
adressés en 11 phases : coupure réseau pendant une session (chunks bufferisés côté client,
renvoyés à la reconnexion), fermeture du navigateur (reprise à la réouverture), redémarrage
serveur (aucune session active perdue), panne Azure Speech (la transcription se rattrape en
différé).

Recherche effectuée dans le code avant de concevoir cette brique (pas supposé) :
`Recorder.tsx` n'avait aucune résilience — un chunk qui échoue à l'envoi était perdu
silencieusement (`.catch(console.error)`), rien n'était persisté en `localStorage`, aucune
UI de reprise n'existait. Le backend, en revanche, était déjà solide sur la durabilité :
chaque chunk est persisté en Postgres avant d'être traité, et `PUT /sessions/{id}/chunks/{numero}`
est déjà idempotent. Les vrais trous backend étaient ailleurs : aucun endpoint pour qu'un
client sache ce qu'il a déjà envoyé, et aucun rattrapage si l'événement asynchrone de
transcription est perdu.

## 2. Les décisions de conception

### 2.1 — Un endpoint de reprise plutôt qu'un état de session côté client

`GET /api/v1/sessions/{id}/chunks` retourne les numéros déjà reçus. Un client qui reprend
n'a plus besoin de faire confiance à son propre compteur local (perdu à la fermeture de
l'onglet) : il demande au serveur, qui est la source de vérité durable, et repart
exactement à `max(numeros) + 1`. Comme le PUT est déjà idempotent, une reprise même
légèrement pessimiste resterait sans danger — cet endpoint est une optimisation de
justesse, pas un garde-fou de sécurité.

### 2.2 — Rattrapage des transcriptions par balayage planifié, pas par callback

`TranscriptionService.surChunkEnregistre` ne traite un chunk que si l'événement Spring
`@Async @EventListener` a effectivement été exécuté. Un redémarrage serveur entre la
sauvegarde du chunk et l'exécution du listener perdait silencieusement ce segment pour
toujours — rien ne le détectait jamais. Le corps de traitement d'un chunk a été extrait
dans une méthode partagée (`traiterChunk`), réutilisée par l'événement normal et par un
nouveau balayage planifié (`@Scheduled`, même pattern que `RappelEngagementService.verifierEcheances`)
qui retrouve les chunks sans transcription correspondante, plus vieux qu'un délai de grâce
(120s par défaut, marge par rapport au timeout Azure Speech documenté à 90s), et les
retraite en relisant l'audio via `StockageAudioPort.lire()` (port déjà existant, réutilisé
pour la reconnaissance de locuteur récurrente — aucun nouvel adaptateur).

### 2.3 — Persistance minimale côté client, pas d'IndexedDB

`Recorder.tsx` persiste seulement `{ sessionId, titre, couloirId }` en `localStorage` — pas
le numéro de chunk (recalculé via §2.1 à la reprise, plus fiable qu'une valeur locale
potentiellement périmée). Au montage, si une session sauvegardée est encore `EN_COURS`
côté serveur, une bannière propose de reprendre ou d'abandonner. `getUserMedia` reste
déclenché par un clic utilisateur ("Reprendre"), jamais automatiquement — nécessaire de
toute façon dans la plupart des navigateurs (permission micro liée à un geste utilisateur).

### 2.4 — Retry avec backoff en mémoire, pas de file durable

L'envoi d'un chunk retente jusqu'à 5 fois avec un backoff exponentiel plafonné (1s, 2s,
4s, 8s, 16s), attend un événement `online` du navigateur avant de retenter si hors ligne,
et affiche un bandeau non bloquant pendant l'instabilité. La file d'attente reste en
mémoire (pas IndexedDB) : un chunk en retry au moment précis de la fermeture de l'onglet
(30s de perte maximum) n'est pas couvert — seule la fermeture *entre* deux segments l'est,
via §2.3. Une vraie durabilité navigateur (IndexedDB + Background Sync API) est plus lourde
que ce que la coupure réseau typique justifie à ce stade.

## 3. Les fichiers, un par un

### `AudioChunkRepository.java`, `AudioChunkService.java`, `AudioChunkController.java` (édités)
Nouvelle requête projetée (`findNumerosSequenceBySessionId`) + `listerNumerosRecus` +
`GET /api/v1/sessions/{id}/chunks`, même style que `TranscriptionController.obtenirTranscriptions`.

### `TranscriptionService.java` (édité) — refactor + balayage
`traiterChunk` extrait et partagé entre `surChunkEnregistre` (événement normal) et
`rattraperTranscriptionsManquantes` (`@Scheduled`, toutes les 5 minutes par défaut). Chaque
chunk du balayage est traité dans son propre `try/catch` : l'échec d'un chunk n'interrompt
pas les suivants.

### `application.properties` (édité)
`memoria.transcription.rattrapage.cron` (défaut 5 min) et
`memoria.transcription.rattrapage.delai-grace-secondes` (défaut 120s).

### Frontend — `api.ts` (édité) + `Recorder.tsx` (réécrit)
`listerNumerosChunksRecus`. `Recorder.tsx` : bannière de reprise, flux `reprendre()`/`abandonner()`,
`envoyerChunkAvecRetry` avec backoff, écouteurs `online`/`offline`, bandeau "connexion
instable".

### Tests
`AudioChunkServiceTest` (+2 tests : reprise, session introuvable). `TranscriptionServiceTest`
(nouveau mock `StockageAudioPort` sur tous les tests existants du fait du nouveau
constructeur, +3 tests : rattrapage réussi, rien à rattraper, un échec n'interrompt pas les
suivants).

## 4. Les tests

210/210 tests backend (205 existants + 5 nouveaux). `mvn -B verify` : `BUILD SUCCESS`, 0
finding SpotBugs/FindSecBugs, couverture maintenue. `npm run build` + `npm run lint` : propres.

## 5. Comment on a vérifié en conditions réelles

Backend de vérification démarré sur un port dédié, avec un cron de rattrapage accéléré
(10s) et un délai de grâce réduit (3s) pour rendre le balayage observable en quelques
secondes plutôt qu'en 5 minutes :
- `PUT` de 2 chunks + rejeu du chunk 0 : `201`, `201`, `200` (idempotence confirmée).
- `GET /sessions/{id}/chunks` → `[0,1]` : exactement les numéros reçus, dans l'ordre.
- Simulation d'un événement de transcription perdu : suppression directe des 2 lignes
  `Transcription` en base (via `docker exec memoria-postgres psql`) en gardant les
  `AudioChunk`. Après le prochain passage du balayage planifié, les 2 lignes `Transcription`
  réapparaissent (log confirmé sur le thread `scheduling-1`, distinct du thread `task-*` de
  l'événement d'origine, preuve que c'est bien le balayage qui les a régénérées).

**Limite de cette vérification, assumée explicitement** : aucun outil de navigateur
automatisé n'était disponible dans cet environnement pour cette brique. Le comportement
UI (bannière de reprise, bandeau de connexion instable, timing du backoff) a été vérifié
par relecture de code et par les contrats d'API réels sous-jacents (§ ci-dessus), pas par
une session de navigateur réelle — à confirmer manuellement dans un navigateur avant mise
en production si un doute subsiste sur le rendu.

## 6. Limites connues, assumées, pas corrigées ici

- **File d'attente de retry en mémoire, pas IndexedDB** — un chunk en cours de retry au
  moment précis de la fermeture de l'onglet (30s max) est perdu.
- **Compteur de rattrapage sans verrou distribué** — un chevauchement pathologique entre le
  balayage et un traitement normal en cours pourrait tenter un double enregistrement (la
  contrainte unique `(session_id, numero_sequence)` sur `transcriptions` ferait alors
  échouer l'un des deux plutôt que dupliquer silencieusement). Non observé en conditions
  réelles avec les délais par défaut.
- **Pas de mécanisme de session abandonnée/heartbeat** — une session `EN_COURS` dont le
  client a définitivement disparu (sans jamais revenir reprendre) reste `EN_COURS` pour
  toujours côté serveur.
- **Vérification UI non faite dans un vrai navigateur** pour cette brique (voir §5) — outil
  non disponible dans cet environnement, pas un choix de conception.

## 7. Pour reprendre seul

- Code de référence exact : `git checkout phase-12-resilience-capture`
- Nouveau contrat : `GET /api/v1/sessions/{id}/chunks` → `List<Integer>` trié.
- Prochaine direction possible : vérification manuelle dans un vrai navigateur (couper le
  réseau via DevTools, fermer l'onglet en cours d'enregistrement) ; IndexedDB si la perte de
  30s au pire cas devient un problème réel signalé par un client ; heartbeat de session si
  des sessions `EN_COURS` orphelines s'accumulent en production.

# Memoria — Phase 1 : Noyau audio

Dossier technique de référence : ressources, code, dépendances et décisions qui ont permis à la Phase 1 de fonctionner de bout en bout, plus les bugs réels rencontrés et corrigés en route.

**Stack** : Spring Boot 3.3.5 / Java 21 · React 19 / Vite / TypeScript · 28 tests backend, tous verts · tag `phase-1-noyau-audio`

---

## Le pipeline

Une session traverse cinq étapes ; chacune est un module backend séparé qui ne connaît que le strict nécessaire de ses voisins, reliés par des **événements Spring** (`ApplicationEventPublisher`), jamais par des appels directs.

```
Session  →  Audio  →  Transcription  →  Résumé  →  Frontend
créer/       chunks     Azure Speech,     Azure       React —
terminer     de 30s,    async, un         OpenAI,     enregistrer,
             stockés,   segment/chunk     tracé au    lister,
             idempotents                 transcript  consulter
```

---

## Ressources Azure

Un abonnement **Azure for Students** (tenant UPEC), deux ressources Cognitive Services en `francecentral`.

| Ressource | Type | Niveau | Rôle dans Memoria |
|---|---|---|---|
| `memoria-speech` | SpeechServices | F0 · gratuit | Transcription des chunks audio (endpoint REST courte durée) |
| `memoria-openai` | AIServices | S0 · payant à l'usage | Génération du résumé — déploiement `gpt-5-mini`, via la **Responses API** (`/openai/v1/responses`), pas l'ancienne Chat Completions qui renvoie 404 sur ce type de ressource |

Variables d'environnement lues par le backend, jamais codées en dur : `AZURE_SPEECH_KEY`, `AZURE_SPEECH_REGION`, `AZURE_OPENAI_KEY`, `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_DEPLOYMENT`.

## Postgres & Docker

- `docker-compose.yml` — un service `postgres:16`, volume nommé pour la persistance
- Port hôte **5433** → conteneur **5432** (un Postgres natif Windows occupait déjà le 5432 sur la machine de dev)
- `spring.jpa.hibernate.ddl-auto=update` — le schéma se construit depuis les entités JPA, pas de migrations versionnées pour l'instant

---

## Backend

### Module `session`

Le cycle de vie d'une session : création, lecture, liste, fin — le point d'entrée de tout le reste.

| Classe | Rôle |
|---|---|
| `Session` (entité) | id, titre, date, statut — le constructeur fixe `EN_COURS` à la naissance |
| `SessionStatus` | enum `EN_COURS` / `TERMINEE` / `ERREUR` |
| `SessionRepository` | JpaRepository + tri par date décroissante |
| `SessionService` | règles métier — créer, terminer (idempotent), lister ; publie `SessionTermineeEvent` |
| `SessionController` | `POST /sessions`, `GET /sessions`, `GET /sessions/{id}`, `POST /sessions/{id}/terminer` |
| `SessionTermineeEvent` | événement publié une fois, jamais rejoué si déjà terminée |

### Module `audio`

Capture des chunks de 30s. Zéro perte de données : un rejeu réseau ne duplique jamais rien.

| Classe | Rôle |
|---|---|
| `AudioChunk` (entité) | sessionId + numéroSéquence (unique), chemin de stockage, taille |
| `StockageAudioPort` | interface — port remplaçable |
| `StockageAudioFichierLocal` | implémentation disque local (`data/audio/{session}/{n}.chunk`) ; remplaçable par Azure Blob sans toucher au domaine |
| `AudioChunkService` | rejette si session non `EN_COURS`, idempotent si chunk déjà reçu, publie `ChunkAudioEnregistreEvent` |
| `AudioChunkController` | `PUT /sessions/{id}/chunks/{n}` |

### Module `transcription`

Écoute chaque chunk, appelle Azure Speech en tâche de fond — la capture ne bloque jamais dessus.

| Classe | Rôle |
|---|---|
| `Transcription` (entité) | un segment par chunk, statut `REUSSIE`/`ECHEC` |
| `TranscripteurPort` / `TranscripteurAzureSpeech` | appel REST Azure Speech ; détecte le vrai format audio (WAV/Ogg via les octets magiques) plutôt que de faire confiance à un Content-Type fixe ; renvoie un texte de repli si les clés sont vides au lieu d'échouer en silence |
| `TranscriptionService` | `@Async @EventListener` sur `ChunkAudioEnregistreEvent` ; publie `ToutesTranscriptionsTermineesEvent` quand c'était le dernier chunk d'une session déjà terminée |
| `TranscriptionController` | `GET /sessions/{id}/transcriptions` |

### Module `resume`

Un résumé par session, généré une fois toutes les transcriptions disponibles — jamais chunk par chunk.

| Classe | Rôle |
|---|---|
| `Resume` (entité) | texte, points clés, et **segmentsSources** — les numéros de chunks utilisés, exigés pour la traçabilité résumé → transcript → audio |
| `GenerateurResumePort` / `GenerateurResumeAzureOpenAI` | Responses API Azure OpenAI, consigne système imposant une réponse JSON stricte `{"resume", "points_cles"}` |
| `ResumeService` | double déclencheur (`SessionTermineeEvent` **et** `ToutesTranscriptionsTermineesEvent`) avec garde d'idempotence pour éviter un doublon si les deux arrivent presque ensemble |
| `ResumeController` | `GET /sessions/{id}/resume` → 204 si pas encore généré |

### Patrons d'architecture

- **Ports & adaptateurs** — trois interfaces (`StockageAudioPort`, `TranscripteurPort`, `GenerateurResumePort`) séparent le domaine de l'infrastructure Azure ; remplacer un fournisseur ne touche jamais la logique métier.
- **Pipeline événementiel** — `ApplicationEventPublisher` + `@Async @EventListener` relient les modules sans dépendance directe — l'équivalent léger de Kafka pour une Phase 1 mono-instance.
- **Idempotence partout** — chunk (contrainte unique session+numéro), fin de session, génération du résumé (vérification avant écriture) : un rejeu réseau ou une course entre événements ne duplique jamais rien.
- **Traçabilité** — résumé → `segmentsSources` → numéro de chunk → fichier audio d'origine. Exigence non négociable du cahier des charges pour les clients réglementés.

---

## Application React

Vite + React 19 + TypeScript + Tailwind v4, deux pages, un composant d'enregistrement.

| Fichier | Rôle |
|---|---|
| `components/Recorder.tsx` | un nouveau `MediaRecorder` toutes les 30s (arrêt puis relance immédiate) pour que chaque chunk soit un fichier autonome ; convertit le blob en WAV PCM via l'API Web Audio avant l'envoi |
| `pages/SessionsListPage.tsx` | démarrer/arrêter, liste des sessions |
| `pages/SessionDetailPage.tsx` | transcript + résumé ; relit toutes les 3s, abandonne après ~5 tentatives une fois la session terminée pour ne pas sonder indéfiniment |
| `api.ts` / `types.ts` | client fetch typé, miroir exact des contrats backend |
| `vite.config.ts` | proxy dev `/api → localhost:8080` — pas besoin de configurer CORS |

---

## Dépendances & plugins

**Backend — Maven**
- `spring-boot-starter-web` — REST, Jackson inclus
- `spring-boot-starter-data-jpa` — Hibernate / repositories
- `spring-boot-starter-validation` — `@NotBlank` etc.
- `postgresql` — driver JDBC (runtime)
- `spring-boot-starter-test` — JUnit 5, Mockito, AssertJ
- `java.net.http.HttpClient` natif pour Azure Speech/OpenAI — aucun SDK Azure ajouté

**Frontend — npm**
- `react` / `react-dom` 19
- `react-router-dom` — navigation deux pages
- `tailwindcss` v4 + `@tailwindcss/vite` — pas de config PostCSS séparée
- `vite` + `@vitejs/plugin-react`
- `typescript` — mode strict
- `playwright` (dev, hors projet) — vérification en navigateur réel

---

## Bugs trouvés & corrigés

Dans l'ordre où ils sont apparus — chacun a coûté du temps réel avant d'être compris.

**1. Conflit de port Postgres**
Symptôme : le backend ne démarrait pas, erreur d'authentification incompréhensible.
Cause : un Postgres natif Windows occupait déjà le 5432. Correctif : conteneur remappé sur 5433.

**2. Mauvaise API Azure OpenAI**
Symptôme : 404 systématique sur la génération de résumé.
Cause : la ressource est de type AI Foundry, elle expose la Responses API, pas l'ancienne Chat Completions. Correctif : bon format d'appel — régressé une fois par une modification parallèle, corrigé une seconde fois.

**3. Configuration Azure vide, silencieuse**
Symptôme : transcriptions en échec sans indice exploitable.
Cause : variables d'environnement absentes du terminal utilisé pour lancer l'appli. Correctif : avertissement explicite au démarrage si une clé est vide.

**4. Sondage sans fin du résumé**
Symptôme : le frontend interrogeait `/resume` toutes les 3s indéfiniment.
Correctif : arrêt dès qu'un résumé existe, abandon après 5 tentatives sinon, message adapté selon la cause.

**5. Course fin-de-session / dernière transcription**
Symptôme : une session avec un seul chunk, arrêtée vite, restait sans résumé malgré une transcription réussie.
Correctif : double déclencheur d'événement (`ToutesTranscriptionsTermineesEvent`) avec garde d'idempotence.

**6. Timeout Azure Speech**
Symptôme : "ça a marché une fois, plus ensuite" — échecs intermittents.
Cause : la conversion WAV côté navigateur produit des chunks non compressés de plusieurs Mo (contre quelques centaines de Ko en webm/opus) ; 30s de délai n'étaient pas toujours suffisants. Correctif : délai porté à 90s.

**7. Processus zombies sur le port 8080**
Symptôme : des correctifs semblaient ne "rien changer" en test.
Cause : un ancien processus restait actif, le nouveau lancement échouait en silence (port occupé). Discipline adoptée : toujours vérifier qu'une seule instance tourne avant de tester.

---

## Tests & vérification

**Unitaires — JUnit 5 / Mockito / AssertJ** : 28 tests, un par service (session, audio, transcription, résumé) plus les cas limites : idempotence, session inconnue, échec du fournisseur, garde anti-doublon.

**Bout en bout — Playwright** : Chromium headless, micro simulé (réel ou factice) : capture → chunk → transcription → fin de session → résumé, captures d'écran et vérification de la console à chaque étape critique.

---

## Historique Git

| Commit | Message |
|---|---|
| `bc744c4` | Charte d'ingénierie + CLAUDE.md |
| `1732b9f` | Squelette Spring Boot minimal |
| `022cb34` | Gestion de session (CRUD) |
| `81b6c6b` | Capture audio en chunks de 30s |
| `38d38d0` | Terminer une session |
| `ff1a005` | Transcription Azure Speech |
| `b62fa8a` | Résumé Azure OpenAI |
| `2ba12eb` | Interface React basique |
| `4ec7242` | Config Azure vide signalée, arrêt du sondage |
| `3299983` | Course fin-de-session / transcription corrigée |
| `8d1a79b` | Conversion WAV, régression résumé corrigée |
| `341b321` | Délai Azure Speech porté à 90s |

**Tag** : `phase-1-noyau-audio` — Phase 1 complète et vérifiée en usage réel, poussée sur `origin/master`.

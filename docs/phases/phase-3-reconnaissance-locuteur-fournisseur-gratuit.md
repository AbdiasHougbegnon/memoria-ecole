# Phase 3 : reconnaissance de voix récurrente — fournisseur gratuit et auto-hébergé — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-3-reconnaissance-locuteur-fournisseur-gratuit
```
Ce tag pointe sur le commit `d58032a`, vérifié end-to-end avec le vrai modèle (pas une factice) sur deux vrais comptes.

---

## 1. Le besoin

Suite directe de la première brique (tag `phase-3-reconnaissance-locuteur`), qui s'était heurtée à une découverte bloquante : Azure Speaker Recognition a été retiré par Microsoft le 30 septembre 2025. Le port `IdentificateurLocuteurPort` isolait déjà cette décision, mais aucune implémentation réellement fonctionnelle n'existait.

Deux familles d'alternatives avaient été identifiées : d'autres clouds payants (AWS Connect Voice ID, Google SpeakerID) ou des modèles auto-hébergés open-source (pyannote, SpeechBrain, WeSpeaker). **Décision de l'utilisateur, explicite** : pas de fournisseur payant. Direction retenue : auto-hébergé.

## 2. Les décisions de conception

### 2.1 — Un service Python séparé, appelé en HTTP, pas une réécriture en Java

Les modèles de reconnaissance de locuteur open-source sont quasi exclusivement distribués en Python/PyTorch (SpeechBrain, pyannote). Plutôt que de chercher une bibliothèque Java équivalente (rare, moins mature), le choix a été de créer un petit service Python séparé (`speaker-service/`) exposant une API HTTP minimale — cohérent avec la manière dont tout le reste du projet parle à des services externes (Azure via `HttpClient` brut, jamais de SDK). Le port `IdentificateurLocuteurPort` ne change pas ; seule une nouvelle implémentation (`IdentificateurLocuteurSpeechBrain`) vient s'y brancher, exactement comme prévu.

### 2.2 — SpeechBrain ECAPA-TDNN, pas de fine-tuning

Modèle pré-entraîné `speechbrain/spkrec-ecapa-voxceleb`, utilisé tel quel (aucun entraînement supplémentaire) : suffisant pour distinguer des voix a priori différentes, cohérent avec le périmètre de la première brique (pas de calibration de seuil poussée). PyTorch installé en version CPU uniquement — pas de GPU nécessaire pour ce volume, évite une dépendance CUDA lourde.

### 2.3 — Persistance par fichier, pas de base de données dédiée

Chaque profil vocal (embedding numpy) est un fichier `.npy` dans `speaker-service/data/profils/`, nommé par UUID — même réflexe que `StockageAudioFichierLocal` côté Java (fichiers plutôt qu'une DB pour un stockage simple indexé par identifiant). Pas de nouvelle base de données à faire tourner pour ce premier service.

### 2.4 — Le client Azure n'est plus un bean Spring

`IdentificateurLocuteurAzureSpeech` perd son annotation `@Component` : il reste dans le code comme trace documentaire du retrait d'Azure (utile si quelqu'un se demande un jour "pourquoi Azure Speaker Recognition n'est pas utilisé"), mais n'est plus jamais instancié par Spring — `IdentificateurLocuteurSpeechBrain` est désormais la seule implémentation active par défaut.

## 3. Un obstacle technique rencontré et résolu : Windows sans privilège administrateur

Au premier lancement, SpeechBrain tente de créer un **lien symbolique** entre son cache Hugging Face et le répertoire `savedir` du modèle — stratégie par défaut (`LocalStrategy.SYMLINK`). Sur Windows sans privilège administrateur ni mode développeur activé, ça échoue : `OSError: [WinError 1314] Le client ne dispose pas d'un privilège nécessaire`.

**Corrigé** en passant explicitement `local_strategy=LocalStrategy.COPY_SKIP_CACHE` à `EncoderClassifier.from_hparams(...)` — télécharge directement une copie dans `savedir` au lieu de lier symboliquement, sans droits particuliers. Documenté dans le code et le `README.md` du service pour éviter de reproduire l'erreur si cette ligne est modifiée plus tard.

## 4. Les fichiers

### `speaker-service/` (nouveau, Python)

- **`main.py`** — service FastAPI : `POST /profils` (enrôle, renvoie un `profilId`), `DELETE /profils/{id}` (supprime), `POST /identification?profilIds=...` (compare par similarité cosinus, renvoie le profil le plus proche + une confiance dans `[0,1]`, dérivée de la similarité cosinus `[-1,1]`), `GET /sante`.
- **`requirements.txt`** — versions figées (`torch==2.13.0+cpu`, `speechbrain==1.1.0`, `fastapi`, `uvicorn`, `soundfile`, `numpy`).
- **`Dockerfile`** — télécharge le modèle au moment du build plutôt qu'au premier démarrage du conteneur (temps de démarrage stable en production).
- **`README.md`** — lancement local (venv) et Docker, référence API, limites (pas de scaling horizontal, CPU seulement, pas d'authentification entre le backend et ce service — à ajouter avant une exposition au-delà de `localhost`).

### Backend Java (modifiés/nouveaux)

**`IdentificateurLocuteurSpeechBrain`** (nouveau, `com.memoria.core.locuteur`) — implémente `IdentificateurLocuteurPort` en appelant `speaker-service/` via `java.net.http.HttpClient` (multipart pour l'audio, même pattern de construction que les clients Azure existants). Dégradation gracieuse : `identifier()` renvoie `ResultatIdentification.aucunMatch()` si le service est injoignable (laisse `IdentificationLocuteurService` continuer les autres locuteurs) ; `enroller()`/`supprimerProfil()` lèvent `IdentificationLocuteurException`, gérée par l'appelant existant.

**`IdentificateurLocuteurAzureSpeech`** — `@Component`/`@Profile` retirés (voir §2.4).

**`application.properties`** — `memoria.speaker-service.url=${MEMORIA_SPEAKER_SERVICE_URL:http://127.0.0.1:8090}`.

## 5. Les tests

`IdentificateurLocuteurSpeechBrainTest.java` — 4 tests, tous contre un port fermé (`http://127.0.0.1:1`, rien n'écoute) pour vérifier la dégradation gracieuse sans dépendre d'un vrai service en cours d'exécution : `identifier` renvoie `aucunMatch()` si injoignable ou si la liste de candidats est vide, `enroller`/`supprimerProfil` lèvent l'exception attendue.

`cd backend && mvn test` — **158/158 tests** passent (154 précédents + 4 nouveaux).

## 6. Comment on a vérifié en conditions réelles — avec le vrai modèle, pas une factice

Contrairement à la première brique (vérifiée avec `IdentificateurLocuteurFactice`, faute de fournisseur fonctionnel), cette fois le **vrai** service Python tournait réellement, avec le vrai modèle SpeechBrain chargé.

Séquence réelle : deux comptes créés, chacun enrôlé via la vraie route API (`POST /utilisateurs/moi/empreinte-vocale`, qui a réellement appelé le service Python et reçu un vrai embedding) avec deux fichiers audio distincts. Une session réelle, deux chunks placés sur disque, deux lignes `SegmentLocuteur` portant **le même index local `locuteur=1`** dans des chunks différents. `POST /terminer` a déclenché l'événement réel, `IdentificationLocuteurService` a réellement appelé le service Python pour chaque locuteur.

Résultat (`GET /sessions/{id}/transcriptions`) :

| Chunk | Attendu | Obtenu |
|---|---|---|
| #0 (échantillon A, "Claire Dubois") | résolu vers A | **"Claire Dubois"**, confiance ≈ 1.0 |
| #1 (échantillon B, "David Leroy"), même index local | résolu vers B | **"David Leroy"**, confiance ≈ 1.0 |

Vérifié aussi visuellement dans la vraie UI ("Intervenant 1 (Claire Dubois)" / "Intervenant 1 (David Leroy)"). Révocation testée en réel : `DELETE /empreinte-vocale` → 204, appel réel au service Python pour supprimer le fichier `.npy` correspondant.

## 7. Limites connues, assumées, pas corrigées ici

- **Un seul processus Python, pas de scaling horizontal** — suffisant pour ce stade du projet.
- **CPU seulement** — pas de GPU, pas optimisé pour un débit élevé ; suffisant pour le volume actuel.
- **Aucune authentification entre le backend Java et `speaker-service/`** — acceptable tant que le service n'écoute que sur `localhost` ; à corriger avant tout déploiement où il serait exposé au-delà.
- **Confiance dérivée d'une similarité cosinus simple** — pas de calibration statistique poussée du seuil `azure.speaker.seuil-confiance` (toujours `0.70` par défaut, hérité de la première brique) contre ce nouveau modèle.
- **Toutes les limites de la première brique restent valables** — pas de recollement inter-chunks, pas de ciblage de `Engagement.responsable`, pas de ré-identification rétroactive (voir `phase-3-reconnaissance-locuteur.md` §8).

## 8. Pour reprendre seul

- Code de référence exact : `git checkout phase-3-reconnaissance-locuteur-fournisseur-gratuit`
- Lancer le service : voir `speaker-service/README.md` (venv local ou Docker).
- Pour changer de modèle (ex: passer à pyannote ou WeSpeaker) : tout le changement se limite à `speaker-service/main.py`, aucun impact côté Java.
- Chemin de bout en bout : `ParametresCompteePage.tsx` → `EmpreinteVocaleController`/`Service` → `IdentificateurLocuteurSpeechBrain` → `speaker-service` (HTTP, port 8090) → SpeechBrain ECAPA-TDNN → `.npy` sur disque. Identification : `SessionTermineeEvent` → `IdentificationLocuteurService` → `IdentificateurLocuteurSpeechBrain.identifier` → `speaker-service` → `Transcription.identifierLocuteur`.

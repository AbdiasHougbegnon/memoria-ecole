# Memoria Speaker Service

Service de reconnaissance de locuteur auto-hébergé, gratuit (aucun coût par
appel), remplacement d'Azure Speaker Recognition (retiré par Microsoft le
30 septembre 2025 — voir `backend/.../locuteur/IdentificateurLocuteurAzureSpeech.java`).

Utilise le modèle pré-entraîné open-source [SpeechBrain ECAPA-TDNN](https://huggingface.co/speechbrain/spkrec-ecapa-voxceleb)
pour extraire une empreinte vocale (embedding) et comparer par similarité
cosinus. Appelé par le backend Java via `IdentificateurLocuteurSpeechBrain`
(`com.memoria.core.locuteur`), lui-même une implémentation interchangeable
du port `IdentificateurLocuteurPort`.

## Lancer en local (développement)

```bash
python -m venv venv
./venv/Scripts/pip install -r requirements.txt   # Windows
# ./venv/bin/pip install -r requirements.txt      # Linux/macOS
./venv/Scripts/python -m uvicorn main:app --host 127.0.0.1 --port 8090
```

Le modèle (~80 Mo) se télécharge automatiquement au premier démarrage depuis
Hugging Face, puis est mis en cache dans `pretrained_models/`.

**Windows sans privilège administrateur / mode développeur** : la stratégie
par défaut de SpeechBrain (lien symbolique) échoue avec `OSError: [WinError 1314]`.
Le code force déjà `LocalStrategy.COPY_SKIP_CACHE` pour l'éviter — pas
d'action requise, mais si vous changez cette ligne, gardez `COPY_SKIP_CACHE`
sur Windows.

Le backend Java pointe vers ce service via `memoria.speaker-service.url`
(défaut `http://127.0.0.1:8090`, surchargeable par `MEMORIA_SPEAKER_SERVICE_URL`).

## Lancer avec Docker

```bash
docker build -t memoria-speaker-service .
docker run -p 8090:8090 memoria-speaker-service
```

Le modèle est téléchargé au moment du build de l'image, pas au premier
démarrage du conteneur.

## API

| Route | Description |
|---|---|
| `POST /profils` (multipart, champ `audio`) | Enrôle un échantillon vocal, renvoie `{"profilId": "..."}` |
| `DELETE /profils/{profilId}` | Supprime un profil |
| `POST /identification?profilIds=id1,id2,...` (multipart, champ `audio`) | Compare un segment aux profils candidats, renvoie `{"profilIdReconnu": "...", "confiance": 0.0-1.0}` (`profilIdReconnu: null` si aucun candidat) |
| `GET /sante` | Vérification de disponibilité |

## Persistance

Chaque profil est un fichier `.npy` (embedding numpy) dans `data/profils/` —
pas de base de données dédiée pour ce premier service, même style que
`StockageAudioFichierLocal` côté Java (fichiers plutôt qu'une DB pour un
stockage simple par identifiant).

## Limites connues

- Un seul processus, pas de scaling horizontal prévu dans cette brique.
- Le modèle tourne sur CPU (`torch` CPU-only) — suffisant pour ce volume,
  pas optimisé pour un débit élevé.
- Aucune authentification entre le backend Java et ce service — à ajouter
  avant un déploiement où le service serait exposé au-delà de `localhost`.

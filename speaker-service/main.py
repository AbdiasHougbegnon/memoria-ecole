"""Service de reconnaissance de locuteur auto-heberge, remplacement gratuit
d'Azure Speaker Recognition (retire par Microsoft le 30 septembre 2025).

Utilise le modele pre-entraine SpeechBrain ECAPA-TDNN (speechbrain/spkrec-ecapa-voxceleb,
open-source, aucun cout par appel) pour extraire une empreinte vocale (embedding)
et comparer par similarite cosinus. Expose une API HTTP minimale appelee par le
backend Java (IdentificateurLocuteurPort), meme principe que les clients Azure
REST du projet -- un detail d'infrastructure remplacable derriere un port.

Persistance simple : un fichier .npy par profil sur disque (meme style que
StockageAudioFichierLocal cote Java), pas de base de donnees dediee pour ce
premier service.
"""

import io
import uuid
from pathlib import Path

import numpy as np
import soundfile as sf
import torch
from fastapi import FastAPI, File, HTTPException, UploadFile
from speechbrain.inference.speaker import EncoderClassifier
from speechbrain.utils.fetching import LocalStrategy

REPERTOIRE_PROFILS = Path(__file__).parent / "data" / "profils"
REPERTOIRE_PROFILS.mkdir(parents=True, exist_ok=True)

app = FastAPI(title="Memoria Speaker Service")

# Charge le modele une seule fois au demarrage (couteux : quelques secondes).
# local_strategy=COPY_SKIP_CACHE : la strategie par defaut (SYMLINK) echoue
# sur Windows sans privilege administrateur/mode developpeur active.
classifieur = EncoderClassifier.from_hparams(
    source="speechbrain/spkrec-ecapa-voxceleb",
    savedir="pretrained_models/spkrec-ecapa-voxceleb",
    local_strategy=LocalStrategy.COPY_SKIP_CACHE,
)


def extraire_embedding(donnees_audio: bytes) -> np.ndarray:
    signal, taux = sf.read(io.BytesIO(donnees_audio), dtype="float32")
    if signal.ndim > 1:
        signal = signal.mean(axis=1)  # mono
    tensor = torch.tensor(signal).unsqueeze(0)
    with torch.no_grad():
        embedding = classifieur.encode_batch(tensor)
    return embedding.squeeze().cpu().numpy()


def similarite_cosinus(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b)))


@app.post("/profils")
async def enroler(audio: UploadFile = File(...)):
    donnees = await audio.read()
    try:
        embedding = extraire_embedding(donnees)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Audio illisible : {e}")

    profil_id = str(uuid.uuid4())
    np.save(REPERTOIRE_PROFILS / f"{profil_id}.npy", embedding)
    return {"profilId": profil_id}


@app.delete("/profils/{profil_id}")
def supprimer_profil(profil_id: str):
    fichier = REPERTOIRE_PROFILS / f"{profil_id}.npy"
    fichier.unlink(missing_ok=True)
    return {"supprime": True}


@app.post("/identification")
async def identifier(audio: UploadFile = File(...), profilIds: str = ""):
    candidats = [p for p in profilIds.split(",") if p]
    if not candidats:
        return {"profilIdReconnu": None, "confiance": 0.0}

    donnees = await audio.read()
    try:
        embedding_segment = extraire_embedding(donnees)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Audio illisible : {e}")

    meilleur_profil = None
    meilleure_similarite = -1.0
    for profil_id in candidats:
        fichier = REPERTOIRE_PROFILS / f"{profil_id}.npy"
        if not fichier.exists():
            continue
        embedding_profil = np.load(fichier)
        similarite = similarite_cosinus(embedding_segment, embedding_profil)
        if similarite > meilleure_similarite:
            meilleure_similarite = similarite
            meilleur_profil = profil_id

    if meilleur_profil is None:
        return {"profilIdReconnu": None, "confiance": 0.0}

    # Similarite cosinus [-1,1] -> confiance [0,1], meme echelle que le seuil
    # deja utilise cote Java (azure.speaker.seuil-confiance).
    confiance = max(0.0, (meilleure_similarite + 1) / 2)
    return {"profilIdReconnu": meilleur_profil, "confiance": confiance}


@app.get("/sante")
def sante():
    return {"statut": "ok"}

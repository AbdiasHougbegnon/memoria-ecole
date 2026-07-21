FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .

# Telecharge le modele au build plutot qu'au premier demarrage (evite un
# temps de demarrage variable en production).
RUN python -c "from speechbrain.inference.speaker import EncoderClassifier; from speechbrain.utils.fetching import LocalStrategy; EncoderClassifier.from_hparams(source='speechbrain/spkrec-ecapa-voxceleb', savedir='pretrained_models/spkrec-ecapa-voxceleb', local_strategy=LocalStrategy.COPY_SKIP_CACHE)"

EXPOSE 8090

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8090"]

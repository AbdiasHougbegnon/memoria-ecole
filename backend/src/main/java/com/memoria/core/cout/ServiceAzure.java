package com.memoria.core.cout;

// Un service Azure IA parmi ceux reellement utilises par le moteur -- tag
// applique aux metriques Micrometer de la brique "Maitrise des couts Azure".
// Pas de SPEAKER_RECOGNITION : Azure Speaker Recognition a ete retire par
// Microsoft (voir IdentificateurLocuteurAzureSpeech, garde comme reference
// documentaire, plus un bean Spring) -- la reconnaissance de locuteur en
// production passe par un service auto-heberge gratuit (speaker-service/),
// sans cout Azure a suivre ici.
public enum ServiceAzure {
    OPENAI_CHAT,
    OPENAI_EMBEDDING,
    SPEECH_STT,
    SPEECH_TTS,
    AI_SEARCH,
    DOCUMENT_INTELLIGENCE
}

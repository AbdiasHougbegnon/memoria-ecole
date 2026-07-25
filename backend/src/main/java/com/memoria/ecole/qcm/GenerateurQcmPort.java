package com.memoria.ecole.qcm;

// Detail d'infrastructure remplacable (Azure OpenAI aujourd'hui, un autre
// fournisseur demain). L'entree est le contenu du resume de cours (synthese +
// notions), pas la transcription brute -- voir QcmService.
public interface GenerateurQcmPort {

    QcmGenere genererQcm(String contenuCours);
}

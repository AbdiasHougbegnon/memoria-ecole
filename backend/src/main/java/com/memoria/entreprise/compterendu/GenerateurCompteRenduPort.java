package com.memoria.entreprise.compterendu;

// Detail d'infrastructure remplacable (Azure OpenAI aujourd'hui, un autre
// fournisseur demain). Vocabulaire Entreprise (decisions, actions,
// responsables) : ce port n'a pas sa place dans le moteur, contrairement a
// GenerateurResumePort qui reste generique.
public interface GenerateurCompteRenduPort {

    CompteRenduGenere genererCompteRendu(String transcriptComplet);
}

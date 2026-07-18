package com.memoria.ecole.resumecours;

// Detail d'infrastructure remplacable (Azure OpenAI aujourd'hui, un autre
// fournisseur demain). Vocabulaire Ecole (notions, definitions, points a
// revoir) : ce port n'a pas sa place dans le moteur.
public interface GenerateurResumeCoursPort {

    ResumeCoursGenere genererResumeCours(String transcriptComplet);
}

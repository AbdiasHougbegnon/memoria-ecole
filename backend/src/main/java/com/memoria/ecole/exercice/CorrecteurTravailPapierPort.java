package com.memoria.ecole.exercice;

// Detail d'infrastructure remplacable (Azure OpenAI aujourd'hui). Corrige le
// travail qu'un etudiant a fait sur papier, a partir du texte extrait de sa
// photo -- pas seulement le stocker/transcrire (voir TravailPapierService).
public interface CorrecteurTravailPapierPort {

    CorrectionTravailPapier corriger(String texteExtrait);
}

package com.memoria.ecole.exercice;

// Detail d'infrastructure remplacable (Azure OpenAI aujourd'hui). Deux
// operations distinctes de la meme capacite IA : generer des questions
// ouvertes a partir du contenu d'une matiere, et evaluer qualitativement une
// reponse d'etudiant par rapport aux elements attendus d'une question donnee.
public interface GenerateurExerciceSaisieLibrePort {

    ExercicesGeneres genererExercices(String contenuMatiere);

    EvaluationReponseLibre evaluerReponse(String enonce, String elementsAttendus, String reponseEtudiant);
}

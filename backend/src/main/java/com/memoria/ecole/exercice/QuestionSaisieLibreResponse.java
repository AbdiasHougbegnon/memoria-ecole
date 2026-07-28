package com.memoria.ecole.exercice;

// N'expose jamais elementsAttendus : c'est la grille de correction interne a
// l'IA, pas une information a donner a l'etudiant avant qu'il ne reponde.
public record QuestionSaisieLibreResponse(String enonce) {

    public static QuestionSaisieLibreResponse depuis(QuestionSaisieLibre question) {
        return new QuestionSaisieLibreResponse(question.getEnonce());
    }
}

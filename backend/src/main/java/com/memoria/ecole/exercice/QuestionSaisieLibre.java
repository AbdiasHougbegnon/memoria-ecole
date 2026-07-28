package com.memoria.ecole.exercice;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// elementsAttendus n'est jamais expose a l'etudiant (voir QuestionSaisieLibreResponse) :
// sert uniquement de grille de correction transmise a l'IA au moment de
// evaluer une reponse (GenerateurExerciceSaisieLibrePort.evaluerReponse).
@Embeddable
public class QuestionSaisieLibre {

    @Column(columnDefinition = "text")
    private String enonce;

    @Column(name = "elements_attendus", columnDefinition = "text")
    private String elementsAttendus;

    protected QuestionSaisieLibre() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public QuestionSaisieLibre(String enonce, String elementsAttendus) {
        this.enonce = enonce;
        this.elementsAttendus = elementsAttendus;
    }

    public String getEnonce() {
        return enonce;
    }

    public String getElementsAttendus() {
        return elementsAttendus;
    }
}

package com.memoria.ecole.exercice;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// Un choix de la question de verification de comprehension (phase 30), avec
// son propre indicateur de justesse -- evite deux listes paralleles
// (textes / indices corrects) qui pourraient se desynchroniser.
@Embeddable
public class ChoixVerification {

    @Column(columnDefinition = "text")
    private String texte;

    @Column(nullable = false)
    private boolean correct;

    protected ChoixVerification() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public ChoixVerification(String texte, boolean correct) {
        this.texte = texte;
        this.correct = correct;
    }

    public String getTexte() {
        return texte;
    }

    public boolean isCorrect() {
        return correct;
    }
}

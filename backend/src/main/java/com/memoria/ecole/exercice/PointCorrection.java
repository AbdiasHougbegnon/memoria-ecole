package com.memoria.ecole.exercice;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// Un point de correction identifiable (ex. "Date du calcul des probabilites"),
// plutot qu'un seul bloc de texte -- pour permettre un affichage par points
// repliables/depliables cote frontend au lieu d'un mur de texte illisible.
@Embeddable
public class PointCorrection {

    @Column(columnDefinition = "text")
    private String sujet;

    @Column(columnDefinition = "text")
    private String constat;

    @Column(name = "correction_attendue", columnDefinition = "text")
    private String correctionAttendue;

    protected PointCorrection() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public PointCorrection(String sujet, String constat, String correctionAttendue) {
        this.sujet = sujet;
        this.constat = constat;
        this.correctionAttendue = correctionAttendue;
    }

    public String getSujet() {
        return sujet;
    }

    public String getConstat() {
        return constat;
    }

    public String getCorrectionAttendue() {
        return correctionAttendue;
    }
}

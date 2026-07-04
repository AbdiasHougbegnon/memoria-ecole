package com.memoria.core.transcription;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class SegmentLocuteur {

    @Column(name = "locuteur")
    private int locuteur;

    @Column(name = "texte", columnDefinition = "text")
    private String texte;

    @Column(name = "offset_millisecondes")
    private long offsetMillisecondes;

    @Column(name = "duree_millisecondes")
    private long dureeMillisecondes;

    protected SegmentLocuteur() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public SegmentLocuteur(int locuteur, String texte, long offsetMillisecondes, long dureeMillisecondes) {
        this.locuteur = locuteur;
        this.texte = texte;
        this.offsetMillisecondes = offsetMillisecondes;
        this.dureeMillisecondes = dureeMillisecondes;
    }

    public int getLocuteur() {
        return locuteur;
    }

    public String getTexte() {
        return texte;
    }

    public long getOffsetMillisecondes() {
        return offsetMillisecondes;
    }

    public long getDureeMillisecondes() {
        return dureeMillisecondes;
    }
}

package com.memoria.core.transcription;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

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

    // Rempli a posteriori par IdentificationLocuteurService (reconnaissance
    // vocale recurrente) -- null tant que ce locuteur n'a pas ete identifie
    // avec une confiance suffisante. Le "locuteur" (index ci-dessus) est
    // local a ce chunk : voir Transcription.identifierLocuteur.
    @Column(name = "utilisateur_identifie_id")
    private UUID utilisateurIdentifieId;

    @Column(name = "confiance_identification")
    private Double confianceIdentification;

    protected SegmentLocuteur() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public SegmentLocuteur(int locuteur, String texte, long offsetMillisecondes, long dureeMillisecondes) {
        this(locuteur, texte, offsetMillisecondes, dureeMillisecondes, null, null);
    }

    public SegmentLocuteur(
            int locuteur,
            String texte,
            long offsetMillisecondes,
            long dureeMillisecondes,
            UUID utilisateurIdentifieId,
            Double confianceIdentification) {
        this.locuteur = locuteur;
        this.texte = texte;
        this.offsetMillisecondes = offsetMillisecondes;
        this.dureeMillisecondes = dureeMillisecondes;
        this.utilisateurIdentifieId = utilisateurIdentifieId;
        this.confianceIdentification = confianceIdentification;
    }

    // Embeddable immuable : pas de setter, une nouvelle instance est
    // reconstruite avec l'identification en plus (voir Transcription.identifierLocuteur).
    public SegmentLocuteur avecIdentification(UUID utilisateurIdentifieId, double confiance) {
        return new SegmentLocuteur(locuteur, texte, offsetMillisecondes, dureeMillisecondes, utilisateurIdentifieId, confiance);
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

    public UUID getUtilisateurIdentifieId() {
        return utilisateurIdentifieId;
    }

    public Double getConfianceIdentification() {
        return confianceIdentification;
    }
}

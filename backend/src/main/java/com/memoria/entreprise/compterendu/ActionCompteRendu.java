package com.memoria.entreprise.compterendu;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ActionCompteRendu {

    @Column(columnDefinition = "text")
    private String description;

    // "Intervenant N" (numero de locuteur issu de la diarization), ou null
    // si la transcription ne permet pas d'identifier qui est concerne. Pas
    // de vrai nom tant que la reconnaissance de voix recurrente (Phase 3)
    // n'existe pas.
    private String responsable;

    // Texte libre ("vendredi prochain", "avant la fin du mois") : une date
    // relative extraite du langage naturel n'est pas fiable a convertir en
    // date absolue sans risque de contresens.
    private String echeance;

    protected ActionCompteRendu() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public ActionCompteRendu(String description, String responsable, String echeance) {
        this.description = description;
        this.responsable = responsable;
        this.echeance = echeance;
    }

    public String getDescription() {
        return description;
    }

    public String getResponsable() {
        return responsable;
    }

    public String getEcheance() {
        return echeance;
    }
}

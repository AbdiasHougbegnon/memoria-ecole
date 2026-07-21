package com.memoria.entreprise.compterendu;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public class ActionCompteRendu {

    @Column(columnDefinition = "text")
    private String description;

    // Etiquette telle que renvoyee par l'IA : un vrai nom si le locuteur a
    // ete identifie (voir ConstructeurTranscriptLabelise), sinon "Intervenant
    // X" (locale a ce compte rendu). Jamais un nom invente par l'IA (voir la
    // consigne dans GenerateurCompteRenduAzureOpenAI).
    private String responsable;

    // Resolu quand "responsable" correspond a un locuteur identifie -- permet
    // a EngagementService de cibler precisement cette personne au lieu de
    // notifier tous les participants de la session. Null si le locuteur n'a
    // pas ete identifie (reconnaissance de voix recurrente, Phase 3).
    @Column(name = "responsable_utilisateur_id")
    private UUID responsableUtilisateurId;

    // Texte libre ("vendredi prochain", "avant la fin du mois") : une date
    // relative extraite du langage naturel n'est pas fiable a convertir en
    // date absolue sans risque de contresens.
    private String echeance;

    protected ActionCompteRendu() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public ActionCompteRendu(String description, String responsable, String echeance) {
        this(description, responsable, echeance, null);
    }

    public ActionCompteRendu(String description, String responsable, String echeance, UUID responsableUtilisateurId) {
        this.description = description;
        this.responsable = responsable;
        this.echeance = echeance;
        this.responsableUtilisateurId = responsableUtilisateurId;
    }

    public String getDescription() {
        return description;
    }

    public String getResponsable() {
        return responsable;
    }

    public UUID getResponsableUtilisateurId() {
        return responsableUtilisateurId;
    }

    public String getEcheance() {
        return echeance;
    }
}

package com.memoria.entreprise.engagement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// L'IA n'est jamais la source de verite (doctrine du projet) : un
// Engagement extrait automatiquement du compte rendu reste EN_ATTENTE tant
// qu'un humain ne l'a pas confirme ou rejete. La tracabilite jusqu'a la
// transcription passe par sessionId (compte rendu -> segmentsSources).
@Entity
@Table(name = "engagements")
public class Engagement {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(columnDefinition = "text", nullable = false)
    private String description;

    // "Intervenant N" (diarization) ou null : pas de vrai nom tant que la
    // reconnaissance de voix recurrente n'existe pas.
    private String responsable;

    // Texte libre ("vendredi prochain") : voir ActionCompteRendu pour la
    // meme decision de ne pas convertir en date absolue.
    private String echeance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutEngagement statut;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    @Column(name = "date_derniere_maj", nullable = false)
    private Instant dateDerniereMaj;

    protected Engagement() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public Engagement(UUID sessionId, String description, String responsable, String echeance) {
        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.description = description;
        this.responsable = responsable;
        this.echeance = echeance;
        this.statut = StatutEngagement.EN_ATTENTE;
        this.dateCreation = Instant.now();
        this.dateDerniereMaj = this.dateCreation;
    }

    public void confirmer() {
        if (statut != StatutEngagement.EN_ATTENTE) {
            throw new TransitionEngagementInvalideException(id, statut, StatutEngagement.CONFIRME);
        }
        changerStatut(StatutEngagement.CONFIRME);
    }

    public void rejeter() {
        if (statut != StatutEngagement.EN_ATTENTE) {
            throw new TransitionEngagementInvalideException(id, statut, StatutEngagement.REJETE);
        }
        changerStatut(StatutEngagement.REJETE);
    }

    public void terminer() {
        if (statut != StatutEngagement.CONFIRME) {
            throw new TransitionEngagementInvalideException(id, statut, StatutEngagement.TERMINE);
        }
        changerStatut(StatutEngagement.TERMINE);
    }

    private void changerStatut(StatutEngagement nouveauStatut) {
        this.statut = nouveauStatut;
        this.dateDerniereMaj = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
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

    public StatutEngagement getStatut() {
        return statut;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public Instant getDateDerniereMaj() {
        return dateDerniereMaj;
    }
}

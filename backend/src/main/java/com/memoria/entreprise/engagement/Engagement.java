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

    // Etiquette telle que produite par le compte rendu : un vrai nom si le
    // locuteur a ete identifie, sinon "Intervenant X" (voir ActionCompteRendu
    // / ConstructeurTranscriptLabelise).
    private String responsable;

    // Resolu quand "responsable" designe un locuteur identifie -- permet aux
    // rappels/notifications de cibler precisement cette personne au lieu de
    // tous les participants de la session (voir EngagementService.notifierCompletion,
    // RappelEngagementService). Null si non identifie.
    @Column(name = "responsable_utilisateur_id")
    private UUID responsableUtilisateurId;

    // Texte libre ("vendredi prochain") : voir ActionCompteRendu pour la
    // meme decision de ne pas convertir en date absolue. dateEcheance
    // (ci-dessous) est la version structuree, saisie manuellement -- aucun
    // parsing automatique du texte libre (l'IA n'est jamais source de
    // verite pour du business-critical).
    private String echeance;

    // Optionnelle : tant qu'elle n'est pas renseignee, aucun rappel n'est
    // programme pour cet engagement (voir RappelEngagementService).
    @Column(name = "date_echeance")
    private Instant dateEcheance;

    @Column(name = "rappel_echeance_proche_envoye", nullable = false)
    private boolean rappelEcheanceProcheEnvoye = false;

    @Column(name = "rappel_retard_envoye", nullable = false)
    private boolean rappelRetardEnvoye = false;

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
        this(sessionId, description, responsable, echeance, null);
    }

    public Engagement(UUID sessionId, String description, String responsable, String echeance, UUID responsableUtilisateurId) {
        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.description = description;
        this.responsable = responsable;
        this.responsableUtilisateurId = responsableUtilisateurId;
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

    // Changer la date reinitialise les rappels deja envoyes : une nouvelle
    // echeance doit pouvoir redeclencher un rappel "proche" ou "en retard"
    // le moment venu, meme si l'ancienne date avait deja ete notifiee.
    public void planifierEcheance(Instant dateEcheance) {
        this.dateEcheance = dateEcheance;
        this.rappelEcheanceProcheEnvoye = false;
        this.rappelRetardEnvoye = false;
    }

    public void marquerRappelEcheanceProcheEnvoye() {
        this.rappelEcheanceProcheEnvoye = true;
    }

    public void marquerRappelRetardEnvoye() {
        this.rappelRetardEnvoye = true;
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

    public UUID getResponsableUtilisateurId() {
        return responsableUtilisateurId;
    }

    public String getEcheance() {
        return echeance;
    }

    public Instant getDateEcheance() {
        return dateEcheance;
    }

    public boolean isRappelEcheanceProcheEnvoye() {
        return rappelEcheanceProcheEnvoye;
    }

    public boolean isRappelRetardEnvoye() {
        return rappelRetardEnvoye;
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

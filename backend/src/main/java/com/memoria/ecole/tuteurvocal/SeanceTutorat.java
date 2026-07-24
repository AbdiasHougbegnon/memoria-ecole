package com.memoria.ecole.tuteurvocal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// L'etat "sauvegarde" que le master prompt exige de preserver a l'arret --
// notionCouranteId + l'historique ordonne des TourDialogueTutorat + chaque
// MaitriseNotion sont mis a jour a chaque tour, pas de mecanisme de snapshot
// separe (voir docs/phases/phase-9-tuteur-vocal.md).
@Entity
@Table(name = "seances_tutorat")
public class SeanceTutorat {

    @Id
    private UUID id;

    @Column(name = "seance_id", nullable = false)
    private UUID seanceId;

    @Column(name = "utilisateur_id", nullable = false)
    private UUID utilisateurId;

    @Column(name = "notion_courante_id")
    private UUID notionCouranteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutSeanceTutorat statut;

    @Column(name = "mode_exercice", nullable = false)
    private boolean modeExercice;

    @Column(name = "date_debut", nullable = false)
    private Instant dateDebut;

    @Column(name = "date_fin")
    private Instant dateFin;

    protected SeanceTutorat() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public SeanceTutorat(UUID seanceId, UUID utilisateurId, UUID notionCouranteId, boolean modeExercice) {
        this.id = UUID.randomUUID();
        this.seanceId = seanceId;
        this.utilisateurId = utilisateurId;
        this.notionCouranteId = notionCouranteId;
        this.statut = StatutSeanceTutorat.EN_COURS;
        this.modeExercice = modeExercice;
        this.dateDebut = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSeanceId() {
        return seanceId;
    }

    public UUID getUtilisateurId() {
        return utilisateurId;
    }

    public UUID getNotionCouranteId() {
        return notionCouranteId;
    }

    public StatutSeanceTutorat getStatut() {
        return statut;
    }

    public boolean isModeExercice() {
        return modeExercice;
    }

    public Instant getDateDebut() {
        return dateDebut;
    }

    public Instant getDateFin() {
        return dateFin;
    }

    public void avancerNotion(UUID nouvelleNotionId) {
        this.notionCouranteId = nouvelleNotionId;
    }

    public void terminer() {
        this.statut = StatutSeanceTutorat.TERMINEE;
        this.dateFin = Instant.now();
    }
}

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

    // nullable de fait malgre l'annotation : ddl-auto=update ne backfille pas
    // les lignes existantes, voir le script de migration manuel documente
    // dans docs/phases/phase-19-mode-conversation-libre.md.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModeTutorat mode;

    @Column(name = "date_debut", nullable = false)
    private Instant dateDebut;

    @Column(name = "date_fin")
    private Instant dateFin;

    protected SeanceTutorat() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public SeanceTutorat(UUID seanceId, UUID utilisateurId, UUID notionCouranteId, ModeTutorat mode) {
        this.id = UUID.randomUUID();
        this.seanceId = seanceId;
        this.utilisateurId = utilisateurId;
        this.notionCouranteId = notionCouranteId;
        this.statut = StatutSeanceTutorat.EN_COURS;
        this.mode = mode;
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

    public ModeTutorat getMode() {
        return mode;
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

    // Transition automatique une fois toutes les notions maitrisees en mode
    // EXPLICATION -- voir TuteurVocalService.soumettreReponse. Jamais dans
    // l'autre sens (pas de retour EXERCICE -> EXPLICATION).
    public void passerEnModeExercice() {
        this.mode = ModeTutorat.EXERCICE;
    }

    public void terminer() {
        this.statut = StatutSeanceTutorat.TERMINEE;
        this.dateFin = Instant.now();
    }
}

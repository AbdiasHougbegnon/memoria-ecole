package com.memoria.ecole.qcm;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Une ligne par (qcm, utilisateur) -- meme style que MaitriseNotion
// (notion_id, utilisateur_id). Pas de FK JPA vers Qcm : table independante,
// nettoyee explicitement lors de la purge de session (voir SessionPurgeService).
@Entity
@Table(name = "tentatives_qcm", uniqueConstraints = @UniqueConstraint(columnNames = {"qcm_id", "utilisateur_id"}))
public class TentativeQcm {

    @Id
    private UUID id;

    @Column(name = "qcm_id", nullable = false)
    private UUID qcmId;

    @Column(name = "utilisateur_id", nullable = false)
    private UUID utilisateurId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tentative_qcm_reponses", joinColumns = @JoinColumn(name = "tentative_qcm_id"))
    @OrderColumn(name = "position")
    @Column(name = "reponse")
    private List<Integer> reponsesChoisies;

    @Column(nullable = false)
    private int score;

    @Column(name = "nombre_questions", nullable = false)
    private int nombreQuestions;

    @Column(name = "nombre_tentatives", nullable = false)
    private int nombreTentatives;

    @Column(name = "date_mise_a_jour", nullable = false)
    private Instant dateMiseAJour;

    protected TentativeQcm() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public TentativeQcm(UUID qcmId, UUID utilisateurId) {
        this.id = UUID.randomUUID();
        this.qcmId = qcmId;
        this.utilisateurId = utilisateurId;
        this.reponsesChoisies = List.of();
        this.score = 0;
        this.nombreQuestions = 0;
        this.nombreTentatives = 0;
        this.dateMiseAJour = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getQcmId() {
        return qcmId;
    }

    public UUID getUtilisateurId() {
        return utilisateurId;
    }

    public List<Integer> getReponsesChoisies() {
        return reponsesChoisies;
    }

    public int getScore() {
        return score;
    }

    public int getNombreQuestions() {
        return nombreQuestions;
    }

    public int getNombreTentatives() {
        return nombreTentatives;
    }

    public Instant getDateMiseAJour() {
        return dateMiseAJour;
    }

    public void enregistrerReponses(List<Integer> reponses, int score, int nombreQuestions) {
        this.reponsesChoisies = reponses;
        this.score = score;
        this.nombreQuestions = nombreQuestions;
        this.nombreTentatives++;
        this.dateMiseAJour = Instant.now();
    }
}

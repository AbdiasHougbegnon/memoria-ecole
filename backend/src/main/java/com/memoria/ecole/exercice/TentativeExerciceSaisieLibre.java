package com.memoria.ecole.exercice;

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

// Une ligne par (exercice, utilisateur) -- meme style que TentativeQcm
// (qcm_id, utilisateur_id). Chaque nouvelle soumission ecrase les reponses
// evaluees precedentes (une seule tentative "courante" gardee, pas un
// historique) : coherent avec TentativeQcm qui fait de meme.
@Entity
@Table(name = "tentatives_exercice_saisie_libre", uniqueConstraints = @UniqueConstraint(columnNames = {"exercice_matiere_id", "utilisateur_id"}))
public class TentativeExerciceSaisieLibre {

    @Id
    private UUID id;

    @Column(name = "exercice_matiere_id", nullable = false)
    private UUID exerciceMatiereId;

    @Column(name = "utilisateur_id", nullable = false)
    private UUID utilisateurId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tentative_exercice_reponses", joinColumns = @JoinColumn(name = "tentative_exercice_id"))
    @OrderColumn(name = "position")
    private List<ReponseEvaluee> reponses;

    @Column(name = "nombre_tentatives", nullable = false)
    private int nombreTentatives;

    @Column(name = "date_mise_a_jour", nullable = false)
    private Instant dateMiseAJour;

    protected TentativeExerciceSaisieLibre() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public TentativeExerciceSaisieLibre(UUID exerciceMatiereId, UUID utilisateurId) {
        this.id = UUID.randomUUID();
        this.exerciceMatiereId = exerciceMatiereId;
        this.utilisateurId = utilisateurId;
        this.reponses = List.of();
        this.nombreTentatives = 0;
        this.dateMiseAJour = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getExerciceMatiereId() {
        return exerciceMatiereId;
    }

    public UUID getUtilisateurId() {
        return utilisateurId;
    }

    public List<ReponseEvaluee> getReponses() {
        return reponses;
    }

    public int getNombreTentatives() {
        return nombreTentatives;
    }

    public Instant getDateMiseAJour() {
        return dateMiseAJour;
    }

    public void enregistrerReponses(List<ReponseEvaluee> reponses) {
        this.reponses = reponses;
        this.nombreTentatives++;
        this.dateMiseAJour = Instant.now();
    }
}

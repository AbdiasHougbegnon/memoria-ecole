package com.memoria.ecole.exercice;

import com.memoria.ecole.qcm.StatutQcm;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Exercices a reponse libre sur toute la matiere (phase 22d), a cote du QCM
// de matiere (QcmMatiere) : questions ouvertes plutot qu'a choix multiple,
// notees qualitativement par l'IA (NiveauMaitrise) plutot que par un score.
// Reutilise StatutQcm (REUSSI/ECHEC) : meme semantique ("la generation a
// reussi ou non"), pas besoin d'un enum dedie.
@Entity
@Table(name = "exercices_matiere")
public class ExerciceMatiere {

    @Id
    private UUID id;

    @Column(name = "matiere_id", nullable = false, unique = true)
    private UUID matiereId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exercice_matiere_questions", joinColumns = @JoinColumn(name = "exercice_matiere_id"))
    @OrderColumn(name = "position")
    private List<QuestionSaisieLibre> questions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutQcm statut;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected ExerciceMatiere() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public ExerciceMatiere(UUID matiereId, List<QuestionSaisieLibre> questions, StatutQcm statut) {
        this.id = UUID.randomUUID();
        this.matiereId = matiereId;
        this.questions = questions;
        this.statut = statut;
        this.dateCreation = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMatiereId() {
        return matiereId;
    }

    public List<QuestionSaisieLibre> getQuestions() {
        return questions;
    }

    public StatutQcm getStatut() {
        return statut;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}

package com.memoria.ecole.exercice;

import com.memoria.ecole.notion.NiveauMaitrise;

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

import java.util.List;
import java.util.UUID;

// Un exercice individuel decoupe a partir des deux photos soumises (enonce +
// reponse, phase 28) -- entite reelle et non @Embeddable (contrairement a
// PointCorrection) car un @ElementCollection ne peut pas contenir lui-meme un
// @ElementCollection imbrique (meme contrainte deja documentee sur
// QuestionQcm) : pointsCorrection doit vivre sur une vraie entite.
// Jointure plate vers TravailPapierMatiere (travailPapierId), meme style que
// SeanceNotion/MembreCouloir ailleurs dans le projet.
@Entity
@Table(name = "exercices_papier")
public class ExercicePapier {

    @Id
    private UUID id;

    @Column(name = "travail_papier_id", nullable = false)
    private UUID travailPapierId;

    @Column(nullable = false)
    private int ordre;

    @Column(columnDefinition = "text")
    private String enonce;

    @Column(name = "reponse_etudiant", columnDefinition = "text")
    private String reponseEtudiant;

    @Enumerated(EnumType.STRING)
    @Column(name = "correction_niveau")
    private NiveauMaitrise correctionNiveau;

    @Column(name = "correction_synthese", columnDefinition = "text")
    private String correctionSynthese;

    // Pas d'initialisateur par defaut (contrairement a "= List.of()") : un
    // ExercicePapier neuf passe par Spring Data save() -> merge() (l'id est
    // assigne manuellement, jamais null, donc Spring Data le traite comme
    // "pas nouveau" et prend le chemin merge plutot que persist). Hibernate
    // tente alors de repliquer les elements dans la collection cible via
    // add() -- si le champ demarre a List.of() (immuable), cet add() echoue
    // avec UnsupportedOperationException. Meme patron que
    // QcmMatiere.questions (aucun initialisateur), qui fonctionne deja.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exercice_papier_points_correction", joinColumns = @JoinColumn(name = "exercice_papier_id"))
    @OrderColumn(name = "position")
    private List<PointCorrection> pointsCorrection;

    protected ExercicePapier() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public ExercicePapier(
            UUID travailPapierId, int ordre, String enonce, String reponseEtudiant,
            NiveauMaitrise correctionNiveau, String correctionSynthese, List<PointCorrection> pointsCorrection
    ) {
        this.id = UUID.randomUUID();
        this.travailPapierId = travailPapierId;
        this.ordre = ordre;
        this.enonce = enonce;
        this.reponseEtudiant = reponseEtudiant;
        this.correctionNiveau = correctionNiveau;
        this.correctionSynthese = correctionSynthese;
        this.pointsCorrection = pointsCorrection;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTravailPapierId() {
        return travailPapierId;
    }

    public int getOrdre() {
        return ordre;
    }

    public String getEnonce() {
        return enonce;
    }

    public String getReponseEtudiant() {
        return reponseEtudiant;
    }

    public NiveauMaitrise getCorrectionNiveau() {
        return correctionNiveau;
    }

    public String getCorrectionSynthese() {
        return correctionSynthese;
    }

    public List<PointCorrection> getPointsCorrection() {
        return pointsCorrection;
    }
}

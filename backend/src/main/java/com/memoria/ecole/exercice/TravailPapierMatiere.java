package com.memoria.ecole.exercice;

import com.memoria.core.document.StatutDocument;
import com.memoria.core.document.TypeDocument;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Photo d'un travail fait sur papier par un etudiant (phase 22e) -- miroir de
// DocumentMatiere (ecole.document) mais rattache a l'etudiant qui l'envoie,
// pas seulement a la matiere : DocumentMatiere est du contenu de cours
// televerse par l'enseignant (alimente le QCM/tuteur de toute la matiere via
// AgregateurContenuMatiereService), un travail papier est personnel a
// l'etudiant qui l'a soumis (n'alimente que SES PROPRES conversations avec le
// tuteur, voir TuteurVocalService.construireContexteMatiere).
@Entity
@Table(name = "travaux_papier_matiere")
public class TravailPapierMatiere {

    @Id
    private UUID id;

    @Column(name = "matiere_id", nullable = false)
    private UUID matiereId;

    @Column(name = "utilisateur_id", nullable = false)
    private UUID utilisateurId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeDocument type;

    @Column(name = "nom_fichier", nullable = false)
    private String nomFichier;

    @Column(name = "chemin_stockage", nullable = false)
    private String cheminStockage;

    @Column(name = "texte_extrait", columnDefinition = "text")
    private String texteExtrait;

    // Correction automatique generee par l'IA juste apres l'extraction du
    // texte (voir TravailPapierService.surTravailPapierTeleverse) -- avant
    // cet increment, le travail papier n'etait que stocke et transcrit, sans
    // jamais etre corrige, ce qui ne repondait pas au besoin reel de
    // l'etudiant ("l'IA analyse ce que j'ai fait et corrige"). Nullable :
    // reste absent si la correction echoue alors que l'extraction a reussi
    // (meme doctrine degradee que ExerciceSaisieLibreService.soumettreReponses).
    @Enumerated(EnumType.STRING)
    @Column(name = "correction_niveau")
    private NiveauMaitrise correctionNiveau;

    @Column(name = "correction_synthese", columnDefinition = "text")
    private String correctionSynthese;

    // Decoupee en points identifiables (phase 27) plutot qu'un seul bloc de
    // texte -- un mur de texte etait juge illisible et impossible a reviser
    // point par point cote frontend.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "travail_papier_points_correction", joinColumns = @JoinColumn(name = "travail_papier_id"))
    @OrderColumn(name = "position")
    private List<PointCorrection> pointsCorrection = List.of();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutDocument statut;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected TravailPapierMatiere() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public TravailPapierMatiere(UUID matiereId, UUID utilisateurId, TypeDocument type, String nomFichier, String cheminStockage) {
        this.id = UUID.randomUUID();
        this.matiereId = matiereId;
        this.utilisateurId = utilisateurId;
        this.type = type;
        this.nomFichier = nomFichier;
        this.cheminStockage = cheminStockage;
        this.statut = StatutDocument.EN_ATTENTE;
        this.dateCreation = Instant.now();
    }

    public void marquerReussi(String texteExtrait) {
        this.texteExtrait = texteExtrait;
        this.statut = StatutDocument.REUSSI;
    }

    public void enregistrerCorrection(NiveauMaitrise correctionNiveau, String correctionSynthese, List<PointCorrection> pointsCorrection) {
        this.correctionNiveau = correctionNiveau;
        this.correctionSynthese = correctionSynthese;
        this.pointsCorrection = pointsCorrection;
    }

    public void marquerEchec() {
        this.statut = StatutDocument.ECHEC;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMatiereId() {
        return matiereId;
    }

    public UUID getUtilisateurId() {
        return utilisateurId;
    }

    public TypeDocument getType() {
        return type;
    }

    public String getNomFichier() {
        return nomFichier;
    }

    public String getCheminStockage() {
        return cheminStockage;
    }

    public String getTexteExtrait() {
        return texteExtrait;
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

    public StatutDocument getStatut() {
        return statut;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}

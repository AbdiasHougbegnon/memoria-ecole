package com.memoria.ecole.exercice;

import com.memoria.core.document.StatutDocument;
import com.memoria.core.document.TypeDocument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
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

    public StatutDocument getStatut() {
        return statut;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}

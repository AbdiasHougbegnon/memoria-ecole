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

// Travail fait sur papier par un etudiant, soumis en deux photos separees
// (phase 28) -- l'enonce (le sujet) et sa propre reponse -- pour que la
// correction s'appuie sur l'enonce reel plutot que de le deviner a partir de
// la seule reponse (limite assumee des phases 24/26/27, levee ici a la
// demande explicite de l'utilisateur). Miroir de DocumentMatiere
// (ecole.document) mais rattache a l'etudiant qui l'envoie, pas seulement a
// la matiere : DocumentMatiere est du contenu de cours televerse par
// l'enseignant, un travail papier est personnel a l'etudiant qui l'a soumis
// (n'alimente que SES PROPRES conversations avec le tuteur, voir
// TuteurVocalService.construireContexteMatiere).
// Le decoupage en exercices individuels (enonce/reponse/correction) vit sur
// ExercicePapier (jointure plate via travailPapierId), pas ici : cette entite
// ne porte que le pipeline global (fichiers, extraction, statut).
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
    @Column(name = "type_enonce", nullable = false)
    private TypeDocument typeEnonce;

    @Column(name = "nom_fichier_enonce", nullable = false)
    private String nomFichierEnonce;

    @Column(name = "chemin_stockage_enonce", nullable = false)
    private String cheminStockageEnonce;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_reponse", nullable = false)
    private TypeDocument typeReponse;

    @Column(name = "nom_fichier_reponse", nullable = false)
    private String nomFichierReponse;

    @Column(name = "chemin_stockage_reponse", nullable = false)
    private String cheminStockageReponse;

    @Column(name = "texte_extrait_enonce", columnDefinition = "text")
    private String texteExtraitEnonce;

    @Column(name = "texte_extrait_reponse", columnDefinition = "text")
    private String texteExtraitReponse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutDocument statut;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected TravailPapierMatiere() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public TravailPapierMatiere(
            UUID matiereId, UUID utilisateurId,
            TypeDocument typeEnonce, String nomFichierEnonce, String cheminStockageEnonce,
            TypeDocument typeReponse, String nomFichierReponse, String cheminStockageReponse
    ) {
        this.id = UUID.randomUUID();
        this.matiereId = matiereId;
        this.utilisateurId = utilisateurId;
        this.typeEnonce = typeEnonce;
        this.nomFichierEnonce = nomFichierEnonce;
        this.cheminStockageEnonce = cheminStockageEnonce;
        this.typeReponse = typeReponse;
        this.nomFichierReponse = nomFichierReponse;
        this.cheminStockageReponse = cheminStockageReponse;
        this.statut = StatutDocument.EN_ATTENTE;
        this.dateCreation = Instant.now();
    }

    public void marquerReussi(String texteExtraitEnonce, String texteExtraitReponse) {
        this.texteExtraitEnonce = texteExtraitEnonce;
        this.texteExtraitReponse = texteExtraitReponse;
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

    public TypeDocument getTypeEnonce() {
        return typeEnonce;
    }

    public String getNomFichierEnonce() {
        return nomFichierEnonce;
    }

    public String getCheminStockageEnonce() {
        return cheminStockageEnonce;
    }

    public TypeDocument getTypeReponse() {
        return typeReponse;
    }

    public String getNomFichierReponse() {
        return nomFichierReponse;
    }

    public String getCheminStockageReponse() {
        return cheminStockageReponse;
    }

    public String getTexteExtraitEnonce() {
        return texteExtraitEnonce;
    }

    public String getTexteExtraitReponse() {
        return texteExtraitReponse;
    }

    public StatutDocument getStatut() {
        return statut;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}

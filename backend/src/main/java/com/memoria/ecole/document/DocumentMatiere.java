package com.memoria.ecole.document;

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

// Miroir de com.memoria.core.document.Document, mais rattache a une Matiere
// (vocabulaire Ecole) plutot qu'a une Session (moteur generique) -- une
// deuxieme FK optionnelle concurrente sur l'entite existante serait
// incoherente avec le style du projet (Couloir/Matiere n'ont chacun qu'une
// seule FK obligatoire, jamais une FK "au choix").
@Entity
@Table(name = "documents_matiere")
public class DocumentMatiere {

    @Id
    private UUID id;

    @Column(name = "matiere_id", nullable = false)
    private UUID matiereId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeDocument type;

    @Column(name = "nom_fichier", nullable = false)
    private String nomFichier;

    @Column(name = "chemin_stockage", nullable = false)
    private String cheminStockage;

    // Octets, capturee a l'upload (MultipartFile.getSize()) -- sert a la
    // detection cote frontend d'un doublon potentiel par nom de fichier (voir
    // MatiereDocumentsPage.televerserFiche), affichee a titre de comparaison,
    // jamais utilisee seule comme critere de blocage.
    @Column(nullable = false)
    private long taille;

    @Column(name = "texte_extrait", columnDefinition = "text")
    private String texteExtrait;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutDocument statut;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected DocumentMatiere() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public DocumentMatiere(UUID matiereId, TypeDocument type, String nomFichier, String cheminStockage, long taille) {
        this.id = UUID.randomUUID();
        this.matiereId = matiereId;
        this.type = type;
        this.nomFichier = nomFichier;
        this.cheminStockage = cheminStockage;
        this.taille = taille;
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

    public TypeDocument getType() {
        return type;
    }

    public String getNomFichier() {
        return nomFichier;
    }

    public String getCheminStockage() {
        return cheminStockage;
    }

    public long getTaille() {
        return taille;
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

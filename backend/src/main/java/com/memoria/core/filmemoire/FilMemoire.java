package com.memoria.core.filmemoire;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "fils_memoire")
public class FilMemoire {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nom;

    @Column(name = "resume_cumulatif", columnDefinition = "text")
    private String resumeCumulatif;

    // Vecteur representatif du fil (l'embedding de resumeCumulatif), recalcule
    // a chaque mise a jour -- stocke en octets bruts (4 octets par composante
    // float) plutot qu'un type tableau JPA, pour eviter toute dependance a un
    // mapping de type specifique a Postgres. Converti par VecteurUtils.
    // Surtout : PAS de @Lob ici -- sur Postgres, @Lob sur un byte[] force
    // Hibernate a utiliser le type "Large Object" (OID), qui exige une
    // vraie transaction (plante en mode auto-commit avec l'erreur "Les
    // Large Objects ne devraient pas etre utilises en mode auto-commit").
    // Sans @Lob, Hibernate mappe simplement vers bytea, sans ce probleme.
    @Column(name = "embedding", columnDefinition = "bytea")
    private byte[] embedding;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "fil_memoire_sessions", joinColumns = @JoinColumn(name = "fil_memoire_id"))
    @OrderColumn(name = "position")
    @Column(name = "session_id")
    private List<UUID> sessionIds;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    @Column(name = "date_mise_a_jour", nullable = false)
    private Instant dateMiseAJour;

    protected FilMemoire() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public FilMemoire(String nom, String resumeCumulatif, byte[] embedding, List<UUID> sessionIds) {
        this.id = UUID.randomUUID();
        this.nom = nom;
        this.resumeCumulatif = resumeCumulatif;
        this.embedding = embedding;
        this.sessionIds = sessionIds;
        this.dateCreation = Instant.now();
        this.dateMiseAJour = Instant.now();
    }

    public void mettreAJour(String resumeCumulatif, byte[] embedding, UUID nouvelleSessionId) {
        this.resumeCumulatif = resumeCumulatif;
        this.embedding = embedding;
        this.sessionIds.add(nouvelleSessionId);
        this.dateMiseAJour = Instant.now();
    }

    // Droit a l'effacement (voir SessionPurgeService) : retire uniquement la
    // reference a la session supprimee, pour eviter tout pointeur mort dans
    // la navigation. Le resumeCumulatif deja genere par l'IA n'est PAS
    // retouche -- le regenerer demanderait une vraie operation de
    // resumeement, hors de proportion pour une suppression de session (voir
    // docs/phases/phase-13-gouvernance-donnees.md, limite assumee).
    public void retirerSession(UUID sessionId) {
        this.sessionIds.remove(sessionId);
    }

    public boolean estVide() {
        return sessionIds.isEmpty();
    }

    public UUID getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public String getResumeCumulatif() {
        return resumeCumulatif;
    }

    public byte[] getEmbedding() {
        return embedding;
    }

    public List<UUID> getSessionIds() {
        return sessionIds;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public Instant getDateMiseAJour() {
        return dateMiseAJour;
    }
}

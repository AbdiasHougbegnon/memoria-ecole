package com.memoria.core.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
public class Session {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String titre;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus statut;

    @Column(name = "chemin_fichier_audio")
    private String cheminFichierAudio;

    protected Session() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public Session(String titre) {
        this.id = UUID.randomUUID();
        this.titre = titre;
        this.dateCreation = Instant.now();
        this.statut = SessionStatus.EN_COURS;
    }

    public UUID getId() {
        return id;
    }

    public String getTitre() {
        return titre;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public SessionStatus getStatut() {
        return statut;
    }

    public String getCheminFichierAudio() {
        return cheminFichierAudio;
    }

    public void terminer() {
        this.statut = SessionStatus.TERMINEE;
    }
}

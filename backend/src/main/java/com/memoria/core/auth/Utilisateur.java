package com.memoria.core.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "utilisateurs", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class Utilisateur {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "mot_de_passe_hash", nullable = false)
    private String motDePasseHash;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected Utilisateur() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public Utilisateur(String email, String motDePasseHash) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.motDePasseHash = motDePasseHash;
        this.dateCreation = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getMotDePasseHash() {
        return motDePasseHash;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}

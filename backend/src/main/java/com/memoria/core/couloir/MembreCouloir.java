package com.memoria.core.couloir;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "membres_couloir", uniqueConstraints = @UniqueConstraint(columnNames = {"couloir_id", "utilisateur_id"}))
public class MembreCouloir {

    @Id
    private UUID id;

    @Column(name = "couloir_id", nullable = false)
    private UUID couloirId;

    @Column(name = "utilisateur_id", nullable = false)
    private UUID utilisateurId;

    @Column(name = "date_adhesion", nullable = false)
    private Instant dateAdhesion;

    protected MembreCouloir() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public MembreCouloir(UUID couloirId, UUID utilisateurId) {
        this.id = UUID.randomUUID();
        this.couloirId = couloirId;
        this.utilisateurId = utilisateurId;
        this.dateAdhesion = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCouloirId() {
        return couloirId;
    }

    public UUID getUtilisateurId() {
        return utilisateurId;
    }

    public Instant getDateAdhesion() {
        return dateAdhesion;
    }
}

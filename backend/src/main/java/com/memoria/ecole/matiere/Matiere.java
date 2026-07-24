package com.memoria.ecole.matiere;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// Vocabulaire Ecole (matieres, seances, notions) : pas de place dans le
// moteur. Rattachee a un Couloir (concept de moteur generique qui joue deja
// le role de "promotion" -- voir docs/phases/phase-5-couloirs-classe.md et
// phase-9-tuteur-vocal.md) par une reference brute, comme partout ailleurs
// dans le projet (Session.couloirId, Couloir.proprietaireId...).
@Entity
@Table(name = "matieres")
public class Matiere {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nom;

    @Column(name = "couloir_id", nullable = false)
    private UUID couloirId;

    @Column(name = "createur_id", nullable = false)
    private UUID createurId;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected Matiere() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public Matiere(String nom, UUID couloirId, UUID createurId) {
        this.id = UUID.randomUUID();
        this.nom = nom;
        this.couloirId = couloirId;
        this.createurId = createurId;
        this.dateCreation = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public UUID getCouloirId() {
        return couloirId;
    }

    public UUID getCreateurId() {
        return createurId;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}

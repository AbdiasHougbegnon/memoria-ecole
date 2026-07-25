package com.memoria.core.gouvernance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journal_rgpd")
public class JournalRgpd {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeActionRgpd type;

    // Null pour PURGE_RETENTION : pas liee a un utilisateur precis.
    @Column(name = "utilisateur_cible_id")
    private UUID utilisateurCibleId;

    @Column(name = "date_action", nullable = false)
    private Instant dateAction;

    @Column(columnDefinition = "text")
    private String details;

    protected JournalRgpd() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public JournalRgpd(TypeActionRgpd type, UUID utilisateurCibleId, String details) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.utilisateurCibleId = utilisateurCibleId;
        this.dateAction = Instant.now();
        this.details = details;
    }

    public UUID getId() {
        return id;
    }

    public TypeActionRgpd getType() {
        return type;
    }

    public UUID getUtilisateurCibleId() {
        return utilisateurCibleId;
    }

    public Instant getDateAction() {
        return dateAction;
    }

    public String getDetails() {
        return details;
    }
}

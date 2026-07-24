package com.memoria.ecole.seance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// couloirId est denormalise depuis Matiere (meme style que
// Session.couloirId), pour eviter un aller-retour vers Matiere a chaque
// verification d'appartenance. sessionId est optionnel : lien futur vers un
// enregistrement/transcription reel (ancrage du tuteur dans le cours reel via
// RechercheService), non utilise dans ce premier increment -- voir
// docs/phases/phase-9-tuteur-vocal.md.
@Entity
@Table(name = "seances")
public class Seance {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String titre;

    @Column(name = "matiere_id", nullable = false)
    private UUID matiereId;

    @Column(name = "couloir_id", nullable = false)
    private UUID couloirId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected Seance() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public Seance(String titre, UUID matiereId, UUID couloirId) {
        this.id = UUID.randomUUID();
        this.titre = titre;
        this.matiereId = matiereId;
        this.couloirId = couloirId;
        this.dateCreation = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTitre() {
        return titre;
    }

    public UUID getMatiereId() {
        return matiereId;
    }

    public UUID getCouloirId() {
        return couloirId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public void rattacherSession(UUID sessionId) {
        this.sessionId = sessionId;
    }
}

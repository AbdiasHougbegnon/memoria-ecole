package com.memoria.ecole.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

// Vocabulaire Ecole (rattachement d'une session enregistree a une matiere) :
// pas de place sur l'entite moteur Session (core/session/Session.java), qui
// doit rester reutilisable telle quelle par Entreprise -- meme raison que
// ContexteScolaireCouloir pour Couloir (voir docs/phases/phase-17a-import-matieres.md).
// Relation 1-1 avec Session.id via une reference brute ; la coherence
// matiere/couloir est verifiee par ContexteScolaireSessionService, pas ici.
@Entity
@Table(name = "contextes_scolaires_session")
public class ContexteScolaireSession {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "matiere_id", nullable = false)
    private UUID matiereId;

    protected ContexteScolaireSession() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public ContexteScolaireSession(UUID sessionId, UUID matiereId) {
        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.matiereId = matiereId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getMatiereId() {
        return matiereId;
    }

    public void changerMatiere(UUID matiereId) {
        this.matiereId = matiereId;
    }
}

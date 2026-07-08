package com.memoria.core.filmemoire;

import com.memoria.core.session.Session;

import java.time.Instant;
import java.util.UUID;

public record SessionSommaireResponse(UUID id, String titre, Instant dateCreation) {

    public static SessionSommaireResponse depuis(Session session) {
        return new SessionSommaireResponse(session.getId(), session.getTitre(), session.getDateCreation());
    }
}

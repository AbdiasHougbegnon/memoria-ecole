package com.memoria.core.filmemoire;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FilMemoireResponse(
        UUID id,
        String nom,
        String resumeCumulatif,
        List<SessionSommaireResponse> sessions,
        Instant dateCreation,
        Instant dateMiseAJour
) {
}

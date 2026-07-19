package com.memoria.core.couloir;

import java.time.Instant;
import java.util.UUID;

public record MembreCouloirResponse(UUID utilisateurId, String email, Instant dateAdhesion) {
}

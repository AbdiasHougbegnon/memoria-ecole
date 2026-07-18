package com.memoria.core.couloir;

import java.time.Instant;
import java.util.UUID;

public record CouloirResponse(UUID id, String nom, UUID proprietaireId, Instant dateCreation, long nombreMembres) {
}

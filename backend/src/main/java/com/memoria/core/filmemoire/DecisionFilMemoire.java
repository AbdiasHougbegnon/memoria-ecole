package com.memoria.core.filmemoire;

import java.util.UUID;

// filExistantId non nul -> rejoint ce fil (resumeMisAJour est alors le nouveau
// resume cumulatif de ce fil). filExistantId nul -> nouveau fil (nouveauNom
// est son nom, resumeMisAJour son resume initial).
public record DecisionFilMemoire(UUID filExistantId, String nouveauNom, String resumeMisAJour) {
}

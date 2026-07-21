package com.memoria.entreprise.tableaudebord;

import com.memoria.entreprise.engagement.StatutEngagement;

import java.util.Map;

// Volontairement agrege, jamais nominatif : "le manager voit l'avancement
// global, pas le detail permanent des retards individuels" (master prompt,
// doctrine IA / vie privee). Aucun champ de ce DTO ne permet de remonter a
// un engagement ou un utilisateur precis.
public record TableauDeBordEntrepriseResponse(
        long total,
        Map<StatutEngagement, Long> parStatut,
        double tauxCompletion,
        long enRetard
) {
}

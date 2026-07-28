package com.memoria.core.session;

import java.util.UUID;

// titre optionnel depuis la phase 22a : un titre plus specifique (ex. nom de
// la matiere pour une session Ecole) peut etre calcule cote client avant
// l'envoi ; sinon SessionService.creerSession genere un repli generique
// ("Session du <date>").
public record CreateSessionRequest(
        String titre,
        UUID couloirId,
        boolean consentementEnregistrement
) {
}

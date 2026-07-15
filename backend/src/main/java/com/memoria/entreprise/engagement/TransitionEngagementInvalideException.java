package com.memoria.entreprise.engagement;

import java.util.UUID;

public class TransitionEngagementInvalideException extends RuntimeException {

    public TransitionEngagementInvalideException(UUID engagementId, StatutEngagement statutActuel, StatutEngagement statutVoulu) {
        super("L'engagement " + engagementId + " est " + statutActuel + ", impossible de passer a " + statutVoulu);
    }
}

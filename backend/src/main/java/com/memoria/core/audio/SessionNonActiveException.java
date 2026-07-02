package com.memoria.core.audio;

import java.util.UUID;

public class SessionNonActiveException extends RuntimeException {

    public SessionNonActiveException(UUID sessionId) {
        super("La session " + sessionId + " n'accepte plus de chunks audio (elle n'est pas EN_COURS)");
    }
}

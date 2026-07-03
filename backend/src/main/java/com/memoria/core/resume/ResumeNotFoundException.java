package com.memoria.core.resume;

import java.util.UUID;

public class ResumeNotFoundException extends RuntimeException {

    public ResumeNotFoundException(UUID sessionId) {
        super("Aucun resume disponible pour la session " + sessionId);
    }
}

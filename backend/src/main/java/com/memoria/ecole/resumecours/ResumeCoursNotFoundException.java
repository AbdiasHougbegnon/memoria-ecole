package com.memoria.ecole.resumecours;

import java.util.UUID;

public class ResumeCoursNotFoundException extends RuntimeException {

    public ResumeCoursNotFoundException(UUID sessionId) {
        super("Aucun resume de cours disponible pour la session " + sessionId);
    }
}

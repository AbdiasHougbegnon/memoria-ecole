package com.memoria.core.session;

import java.util.UUID;

public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(UUID id) {
        super("Session introuvable : " + id);
    }
}

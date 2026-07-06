package com.memoria.core.recherche;

public class RechercheException extends RuntimeException {

    public RechercheException(String message) {
        super(message);
    }

    public RechercheException(String message, Throwable cause) {
        super(message, cause);
    }
}

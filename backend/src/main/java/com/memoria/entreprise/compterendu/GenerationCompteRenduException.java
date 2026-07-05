package com.memoria.entreprise.compterendu;

public class GenerationCompteRenduException extends RuntimeException {

    public GenerationCompteRenduException(String message) {
        super(message);
    }

    public GenerationCompteRenduException(String message, Throwable cause) {
        super(message, cause);
    }
}

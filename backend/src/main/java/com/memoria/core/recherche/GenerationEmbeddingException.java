package com.memoria.core.recherche;

public class GenerationEmbeddingException extends RuntimeException {

    public GenerationEmbeddingException(String message) {
        super(message);
    }

    public GenerationEmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}

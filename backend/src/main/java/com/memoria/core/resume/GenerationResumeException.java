package com.memoria.core.resume;

public class GenerationResumeException extends RuntimeException {

    public GenerationResumeException(String message) {
        super(message);
    }

    public GenerationResumeException(String message, Throwable cause) {
        super(message, cause);
    }
}

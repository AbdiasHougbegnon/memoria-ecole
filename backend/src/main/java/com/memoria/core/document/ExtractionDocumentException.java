package com.memoria.core.document;

public class ExtractionDocumentException extends RuntimeException {

    public ExtractionDocumentException(String message) {
        super(message);
    }

    public ExtractionDocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}

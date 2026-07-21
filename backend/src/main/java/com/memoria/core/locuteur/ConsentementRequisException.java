package com.memoria.core.locuteur;

public class ConsentementRequisException extends RuntimeException {

    public ConsentementRequisException() {
        super("Le consentement explicite est obligatoire pour enregistrer une empreinte vocale");
    }
}

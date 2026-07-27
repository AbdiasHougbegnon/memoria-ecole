package com.memoria.core.session;

public class ConsentementEnregistrementRequisException extends RuntimeException {

    public ConsentementEnregistrementRequisException() {
        super("Le consentement explicite a l'enregistrement est obligatoire pour demarrer une session");
    }
}

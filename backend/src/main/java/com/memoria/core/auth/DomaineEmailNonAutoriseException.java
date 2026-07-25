package com.memoria.core.auth;

public class DomaineEmailNonAutoriseException extends RuntimeException {

    public DomaineEmailNonAutoriseException(String email) {
        super("L'inscription n'est pas ouverte au domaine de l'email " + email);
    }
}

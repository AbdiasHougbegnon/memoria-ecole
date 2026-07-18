package com.memoria.core.auth;

public class EmailDejaUtiliseException extends RuntimeException {

    public EmailDejaUtiliseException(String email) {
        super("Un compte existe deja avec l'email " + email);
    }
}

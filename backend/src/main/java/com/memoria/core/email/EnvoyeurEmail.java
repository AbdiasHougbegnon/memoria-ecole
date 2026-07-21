package com.memoria.core.email;

// Port du moteur generique (pas Entreprise ni Ecole) -- n'importe quelle
// fonctionnalite future qui a besoin d'envoyer un email (rappels
// d'engagements aujourd'hui, potentiellement d'autres notifications demain)
// depend de cette interface, jamais d'un client SMTP concret.
public interface EnvoyeurEmail {

    void envoyer(String destinataire, String sujet, String corps);
}

package com.memoria.core.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

// Meme reflexe que TranscripteurAzureSpeech pour les identifiants Azure :
// memoria.email.host vide = pas de credentials configures, on log et on
// n'envoie rien plutot que de planter au demarrage ou a l'envoi.
@Component
public class EnvoyeurEmailSmtp implements EnvoyeurEmail {

    private static final Logger LOG = LoggerFactory.getLogger(EnvoyeurEmailSmtp.class);

    private final JavaMailSenderImpl mailSender;
    private final String expediteur;
    private final boolean configure;

    public EnvoyeurEmailSmtp(
            @Value("${memoria.email.host}") String host,
            @Value("${memoria.email.port}") int port,
            @Value("${memoria.email.username}") String username,
            @Value("${memoria.email.password}") String password,
            @Value("${memoria.email.expediteur}") String expediteur) {
        this.expediteur = expediteur;
        this.configure = host != null && !host.isBlank();

        if (configure) {
            JavaMailSenderImpl impl = new JavaMailSenderImpl();
            impl.setHost(host);
            impl.setPort(port);
            impl.setUsername(username);
            impl.setPassword(password);
            Properties proprietes = impl.getJavaMailProperties();
            proprietes.put("mail.smtp.auth", username != null && !username.isBlank());
            proprietes.put("mail.smtp.starttls.enable", "true");
            this.mailSender = impl;
        } else {
            this.mailSender = null;
            LOG.warn("memoria.email.host est vide : aucun email ne sera envoye (rappels d'engagements desactives)");
        }
    }

    @Override
    public void envoyer(String destinataire, String sujet, String corps) {
        if (!configure) {
            LOG.info("Email non envoye (SMTP non configure) -- destinataire={}, sujet={}", destinataire, sujet);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(expediteur);
        message.setTo(destinataire);
        message.setSubject(sujet);
        message.setText(corps);
        mailSender.send(message);
    }
}

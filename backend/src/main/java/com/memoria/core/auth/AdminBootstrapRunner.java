package com.memoria.core.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

// Complement a la promotion admin faite dans AuthService.inscrire (qui ne
// couvre que les nouvelles inscriptions) : reconcilie memoria.admin.emails-autorises
// a CHAQUE demarrage, pour couvrir aussi un compte deja cree avant que
// l'operateur de l'instance ne mette a jour la config -- redemarrer suffit,
// jamais d'UI ni d'endpoint de promotion. Idempotent : ne fait rien si le
// compte n'existe pas encore ou est deja admin. Retirer un email de la liste
// ne retrograde jamais personne automatiquement (aucune retrogradation en
// libre-service, coherent avec docs/gouvernance-donnees.md).
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UtilisateurRepository utilisateurRepository;
    private final Set<String> emailsAdminAutorises;

    public AdminBootstrapRunner(
            UtilisateurRepository utilisateurRepository,
            @Value("${memoria.admin.emails-autorises:}") String emailsAdminAutorises
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.emailsAdminAutorises = Arrays.stream(emailsAdminAutorises.split(","))
                .map(String::trim)
                .map(email -> email.toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String email : emailsAdminAutorises) {
            utilisateurRepository.findByEmailIgnoreCase(email)
                    .filter(utilisateur -> !utilisateur.estAdmin())
                    .ifPresent(utilisateur -> {
                        utilisateur.promouvoirAdmin();
                        utilisateurRepository.save(utilisateur);
                        LOG.info("Compte {} promu administrateur au demarrage (memoria.admin.emails-autorises)", email);
                    });
        }
    }
}

package com.memoria.core.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Set<String> domainesAutorises;

    // "Instance = tenant" (meme raisonnement que le budget Azure en phase-11 et
    // la retention RGPD en phase-13) : une seule liste globale de domaines pour
    // l'instance, pas un concept de tenant a construire. Vide par defaut = aucune
    // restriction, comportement historique preserve. Plusieurs domaines separes
    // par des virgules car un etablissement a souvent plusieurs domaines email
    // (ex. profs vs etudiants).
    public AuthService(
            UtilisateurRepository utilisateurRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${memoria.inscription.domaines-autorises:}") String domainesAutorises
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.domainesAutorises = Arrays.stream(domainesAutorises.split(","))
                .map(String::trim)
                .map(domaine -> domaine.toLowerCase(Locale.ROOT))
                .filter(domaine -> !domaine.isEmpty())
                .collect(Collectors.toSet());
    }

    public AuthResponse inscrire(String email, String motDePasse, ModuleMemoria module) {
        if (!domainesAutorises.isEmpty() && !domainesAutorises.contains(domaine(email))) {
            throw new DomaineEmailNonAutoriseException(email);
        }
        if (utilisateurRepository.existsByEmail(email)) {
            throw new EmailDejaUtiliseException(email);
        }
        Utilisateur utilisateur = new Utilisateur(email, passwordEncoder.encode(motDePasse), module);
        utilisateur = utilisateurRepository.save(utilisateur);
        return AuthResponse.depuis(utilisateur, jwtService.genererToken(utilisateur));
    }

    public AuthResponse connecter(String email, String motDePasse) {
        Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
                .orElseThrow(IdentifiantsInvalidesException::new);
        if (!passwordEncoder.matches(motDePasse, utilisateur.getMotDePasseHash())) {
            throw new IdentifiantsInvalidesException();
        }
        return AuthResponse.depuis(utilisateur, jwtService.genererToken(utilisateur));
    }

    public void renseignerNom(UUID utilisateurId, String nom) {
        Utilisateur utilisateur = obtenirUtilisateur(utilisateurId);
        utilisateur.renseignerNom(nom);
        utilisateurRepository.save(utilisateur);
    }

    public Utilisateur obtenirUtilisateur(UUID utilisateurId) {
        return utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new UtilisateurNotFoundException(utilisateurId));
    }

    // @Email (InscriptionRequest) garantit deja la presence d'un "@" avant
    // d'atteindre le service.
    private String domaine(String email) {
        return email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
    }
}

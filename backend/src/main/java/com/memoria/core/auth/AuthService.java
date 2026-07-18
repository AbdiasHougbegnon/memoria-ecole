package com.memoria.core.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UtilisateurRepository utilisateurRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse inscrire(String email, String motDePasse) {
        if (utilisateurRepository.existsByEmail(email)) {
            throw new EmailDejaUtiliseException(email);
        }
        Utilisateur utilisateur = new Utilisateur(email, passwordEncoder.encode(motDePasse));
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
}

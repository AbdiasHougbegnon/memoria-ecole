package com.memoria.core.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/inscription")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse inscrire(@Valid @RequestBody InscriptionRequest requete) {
        return authService.inscrire(requete.email(), requete.motDePasse());
    }

    @PostMapping("/connexion")
    public AuthResponse connecter(@Valid @RequestBody ConnexionRequest requete) {
        return authService.connecter(requete.email(), requete.motDePasse());
    }
}

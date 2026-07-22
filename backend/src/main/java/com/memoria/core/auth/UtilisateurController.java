package com.memoria.core.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/utilisateurs/moi")
public class UtilisateurController {

    private final AuthService authService;

    public UtilisateurController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public UtilisateurResponse obtenir(@AuthenticationPrincipal UUID utilisateurId) {
        return UtilisateurResponse.depuis(authService.obtenirUtilisateur(utilisateurId));
    }

    @PutMapping
    public void renseignerNom(@Valid @RequestBody RenseignerNomRequest requete, @AuthenticationPrincipal UUID utilisateurId) {
        authService.renseignerNom(utilisateurId, requete.nom());
    }

    public record RenseignerNomRequest(@NotBlank(message = "le nom est obligatoire") String nom) {
    }

    public record UtilisateurResponse(UUID id, String email, String nom, ModuleMemoria module) {
        public static UtilisateurResponse depuis(Utilisateur utilisateur) {
            return new UtilisateurResponse(
                    utilisateur.getId(), utilisateur.getEmail(), utilisateur.getNom(), utilisateur.getModule());
        }
    }
}

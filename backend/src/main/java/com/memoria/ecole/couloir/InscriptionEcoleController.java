package com.memoria.ecole.couloir;

import com.memoria.core.auth.AuthResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Endpoints publics (voir SecurityConfig) : accessibles avant authentification,
// puisqu'un futur etudiant n'a justement pas encore de compte.
@RestController
@RequestMapping("/api/v1/ecole")
public class InscriptionEcoleController {

    private final InscriptionEcoleService inscriptionEcoleService;

    public InscriptionEcoleController(InscriptionEcoleService inscriptionEcoleService) {
        this.inscriptionEcoleService = inscriptionEcoleService;
    }

    @GetMapping("/options-inscription")
    public List<OptionInscriptionResponse> optionsInscription() {
        return inscriptionEcoleService.listerOptionsInscription();
    }

    @PostMapping("/inscription")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse inscrire(@Valid @RequestBody InscriptionEcoleRequest requete) {
        return inscriptionEcoleService.inscrire(
                requete.email(), requete.motDePasse(), requete.anneeAcademique(), requete.filiere(), requete.specialite());
    }

    public record InscriptionEcoleRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String motDePasse,
            @NotBlank(message = "l'annee academique est obligatoire") String anneeAcademique,
            @NotBlank(message = "la filiere est obligatoire") String filiere,
            String specialite
    ) {
    }
}

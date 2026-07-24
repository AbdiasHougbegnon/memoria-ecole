package com.memoria.ecole.matiere;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class MatiereController {

    private final MatiereService matiereService;

    public MatiereController(MatiereService matiereService) {
        this.matiereService = matiereService;
    }

    @PostMapping("/matieres")
    @ResponseStatus(HttpStatus.CREATED)
    public MatiereResponse creerMatiere(@Valid @RequestBody CreerMatiereRequest requete, @AuthenticationPrincipal UUID utilisateurId) {
        return MatiereResponse.depuis(matiereService.creerMatiere(requete.nom(), requete.couloirId(), utilisateurId));
    }

    @GetMapping("/couloirs/{couloirId}/matieres")
    public List<MatiereResponse> listerMatieresParCouloir(@PathVariable UUID couloirId) {
        return matiereService.listerMatieresParCouloir(couloirId).stream()
                .map(MatiereResponse::depuis)
                .toList();
    }

    @GetMapping("/matieres/{id}")
    public MatiereResponse obtenirMatiere(@PathVariable UUID id) {
        return MatiereResponse.depuis(matiereService.obtenirMatiere(id));
    }

    public record CreerMatiereRequest(
            @NotBlank(message = "le nom est obligatoire") String nom,
            @NotNull(message = "le couloir est obligatoire") UUID couloirId
    ) {
    }
}

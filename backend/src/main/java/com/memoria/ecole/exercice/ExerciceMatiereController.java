package com.memoria.ecole.exercice;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matieres/{matiereId}/exercices")
public class ExerciceMatiereController {

    private final ExerciceSaisieLibreService exerciceSaisieLibreService;

    public ExerciceMatiereController(ExerciceSaisieLibreService exerciceSaisieLibreService) {
        this.exerciceSaisieLibreService = exerciceSaisieLibreService;
    }

    @GetMapping
    public ResponseEntity<ExerciceMatiereResponse> obtenirExercices(@PathVariable UUID matiereId) {
        try {
            return ResponseEntity.ok(ExerciceMatiereResponse.depuis(exerciceSaisieLibreService.obtenirExercices(matiereId)));
        } catch (ExerciceMatiereNotFoundException ignored) {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping
    public ExerciceMatiereResponse genererExercices(@PathVariable UUID matiereId, @AuthenticationPrincipal UUID utilisateurId) {
        return ExerciceMatiereResponse.depuis(exerciceSaisieLibreService.obtenirOuGenererExercices(matiereId, utilisateurId));
    }

    @PostMapping("/tentatives")
    public TentativeExerciceSaisieLibreResponse soumettreReponses(
            @PathVariable UUID matiereId,
            @RequestBody SoumettreReponsesRequest requete,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        return TentativeExerciceSaisieLibreResponse.depuis(
                exerciceSaisieLibreService.soumettreReponses(matiereId, utilisateurId, requete.reponses())
        );
    }

    @GetMapping("/tentatives/moi")
    public ResponseEntity<TentativeExerciceSaisieLibreResponse> obtenirMaTentative(
            @PathVariable UUID matiereId,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        return exerciceSaisieLibreService.obtenirMaTentative(matiereId, utilisateurId)
                .map(tentative -> ResponseEntity.ok(TentativeExerciceSaisieLibreResponse.depuis(tentative)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    public record SoumettreReponsesRequest(List<String> reponses) {
    }
}

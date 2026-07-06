package com.memoria.core.recherche;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recherche")
public class RechercheController {

    private final RechercheService rechercheService;

    public RechercheController(RechercheService rechercheService) {
        this.rechercheService = rechercheService;
    }

    @GetMapping
    public List<RechercheResultatResponse> rechercher(
            @RequestParam("q") String requete,
            @RequestParam(name = "limite", defaultValue = "10") int limite
    ) {
        if (requete == null || requete.isBlank()) {
            return List.of();
        }
        return rechercheService.rechercher(requete, limite).stream()
                .map(RechercheResultatResponse::depuis)
                .toList();
    }

    // Rattrape les sessions terminees avant l'existence de la recherche
    // semantique. Traitement asynchrone (peut prendre plusieurs minutes selon
    // l'historique) : 202 renvoye immediatement, pas de suivi de progression.
    @PostMapping("/reindexation")
    public ResponseEntity<Void> reindexerHistorique() {
        rechercheService.reindexerHistorique();
        return ResponseEntity.accepted().build();
    }
}

package com.memoria.entreprise.compterendu;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/compte-rendu")
public class CompteRenduController {

    private final CompteRenduService compteRenduService;

    public CompteRenduController(CompteRenduService compteRenduService) {
        this.compteRenduService = compteRenduService;
    }

    @GetMapping
    public ResponseEntity<CompteRenduResponse> obtenirCompteRendu(@PathVariable UUID sessionId) {
        try {
            return ResponseEntity.ok(CompteRenduResponse.depuis(compteRenduService.obtenirCompteRendu(sessionId)));
        } catch (CompteRenduNotFoundException ignored) {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping
    public CompteRenduResponse genererCompteRendu(@PathVariable UUID sessionId) {
        return CompteRenduResponse.depuis(compteRenduService.obtenirOuGenererCompteRendu(sessionId));
    }
}

package com.memoria.core.gouvernance;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Self-service : un utilisateur ne peut effacer/exporter que ses propres
// donnees. Voir GouvernanceAdminController pour l'effacement au nom d'autrui
// reserve aux comptes admin (phase 20).
@RestController
@RequestMapping("/api/v1/utilisateurs/moi")
public class GouvernanceDonneesController {

    private final GouvernanceDonneesService gouvernanceDonneesService;

    public GouvernanceDonneesController(GouvernanceDonneesService gouvernanceDonneesService) {
        this.gouvernanceDonneesService = gouvernanceDonneesService;
    }

    // Deux appels distincts au service plutot qu'un seul : @Transactional ne
    // s'applique pas a un appel interne au sein d'une meme instance Spring
    // (self-invocation) -- le controleur orchestre la partie transactionnelle
    // (Postgres) puis la partie best-effort (disque, Azure AI Search),
    // exactement le role d'un controleur ("orchestrent, ne decident pas").
    @DeleteMapping
    public ResponseEntity<Void> supprimerCompte(@AuthenticationPrincipal UUID utilisateurId) {
        List<UUID> sessionsAPurger = gouvernanceDonneesService.effacerCompte(utilisateurId);
        gouvernanceDonneesService.finaliserEffacement(utilisateurId, sessionsAPurger);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/export")
    public ExportDonneesUtilisateur exporterDonnees(@AuthenticationPrincipal UUID utilisateurId) {
        return gouvernanceDonneesService.exporterDonnees(utilisateurId);
    }
}

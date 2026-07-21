package com.memoria.core.couloir;

import com.memoria.core.auth.Utilisateur;
import com.memoria.core.auth.UtilisateurRepository;
import com.memoria.core.session.Session;
import com.memoria.core.session.SessionResponse;
import com.memoria.core.session.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/couloirs")
public class CouloirController {

    private final CouloirService couloirService;
    private final SessionService sessionService;
    private final UtilisateurRepository utilisateurRepository;

    public CouloirController(CouloirService couloirService, SessionService sessionService, UtilisateurRepository utilisateurRepository) {
        this.couloirService = couloirService;
        this.sessionService = sessionService;
        this.utilisateurRepository = utilisateurRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouloirResponse creerCouloir(@Valid @RequestBody CreerCouloirRequest requete, @AuthenticationPrincipal UUID utilisateurId) {
        Couloir couloir = couloirService.creerCouloir(requete.nom(), utilisateurId);
        return versReponse(couloir);
    }

    @GetMapping
    public List<CouloirResponse> listerMesCouloirs(@AuthenticationPrincipal UUID utilisateurId) {
        return couloirService.listerMesCouloirs(utilisateurId).stream()
                .map(this::versReponse)
                .toList();
    }

    @GetMapping("/{id}")
    public CouloirResponse obtenirCouloir(@PathVariable UUID id) {
        return versReponse(couloirService.obtenirCouloir(id));
    }

    @PostMapping("/{id}/rejoindre")
    public CouloirResponse rejoindreCouloir(@PathVariable UUID id, @AuthenticationPrincipal UUID utilisateurId) {
        return versReponse(couloirService.rejoindreCouloir(id, utilisateurId));
    }

    @PostMapping("/{id}/quitter")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitterCouloir(@PathVariable UUID id, @AuthenticationPrincipal UUID utilisateurId) {
        couloirService.quitterCouloir(id, utilisateurId);
    }

    @PostMapping("/{id}/transferer-propriete")
    public CouloirResponse transfererPropriete(
            @PathVariable UUID id,
            @Valid @RequestBody TransfererProprieteRequest requete,
            @AuthenticationPrincipal UUID utilisateurId) {
        return versReponse(couloirService.transfererPropriete(id, requete.nouveauProprietaireId(), utilisateurId));
    }

    @GetMapping("/{id}/sessions")
    public List<SessionResponse> listerSessionsDuCouloir(@PathVariable UUID id) {
        List<Session> sessions = sessionService.listerSessionsParCouloir(id);
        return sessions.stream().map(SessionResponse::depuis).toList();
    }

    @PatchMapping("/{id}")
    public CouloirResponse renommerCouloir(@PathVariable UUID id, @Valid @RequestBody RenommerCouloirRequest requete, @AuthenticationPrincipal UUID utilisateurId) {
        return versReponse(couloirService.renommerCouloir(id, requete.nom(), utilisateurId));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerCouloir(@PathVariable UUID id, @AuthenticationPrincipal UUID utilisateurId) {
        couloirService.supprimerCouloir(id, utilisateurId);
    }

    @GetMapping("/{id}/membres")
    public List<MembreCouloirResponse> listerMembres(@PathVariable UUID id) {
        return couloirService.listerMembres(id).stream()
                .map(membre -> {
                    Utilisateur utilisateur = utilisateurRepository.findById(membre.getUtilisateurId()).orElseThrow();
                    return new MembreCouloirResponse(membre.getUtilisateurId(), utilisateur.getEmail(), membre.getDateAdhesion());
                })
                .toList();
    }

    @DeleteMapping("/{id}/membres/{utilisateurIdARetirer}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirerMembre(@PathVariable UUID id, @PathVariable UUID utilisateurIdARetirer, @AuthenticationPrincipal UUID utilisateurId) {
        couloirService.retirerMembre(id, utilisateurIdARetirer, utilisateurId);
    }

    private CouloirResponse versReponse(Couloir couloir) {
        return new CouloirResponse(
                couloir.getId(),
                couloir.getNom(),
                couloir.getProprietaireId(),
                couloir.getDateCreation(),
                couloirService.compterMembres(couloir.getId())
        );
    }

    public record CreerCouloirRequest(@NotBlank(message = "le nom est obligatoire") String nom) {
    }

    public record RenommerCouloirRequest(@NotBlank(message = "le nom est obligatoire") String nom) {
    }

    public record TransfererProprieteRequest(@NotNull(message = "le nouveau proprietaire est obligatoire") UUID nouveauProprietaireId) {
    }
}

package com.memoria.core.couloir;

import com.memoria.core.session.Session;
import com.memoria.core.session.SessionResponse;
import com.memoria.core.session.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/v1/couloirs")
public class CouloirController {

    private final CouloirService couloirService;
    private final SessionService sessionService;

    public CouloirController(CouloirService couloirService, SessionService sessionService) {
        this.couloirService = couloirService;
        this.sessionService = sessionService;
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

    @GetMapping("/{id}/sessions")
    public List<SessionResponse> listerSessionsDuCouloir(@PathVariable UUID id) {
        List<Session> sessions = sessionService.listerSessionsParCouloir(id);
        return sessions.stream().map(SessionResponse::depuis).toList();
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
}

package com.memoria.core.session;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateSessionResponse creerSession(@Valid @RequestBody CreateSessionRequest requete) {
        Session session = sessionService.creerSession(requete.titre());
        return new CreateSessionResponse(session.getId());
    }

    @GetMapping("/{id}")
    public SessionResponse obtenirSession(@PathVariable UUID id) {
        Session session = sessionService.obtenirSession(id);
        return SessionResponse.depuis(session);
    }

    @ExceptionHandler(SessionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void gererSessionIntrouvable() {
    }
}

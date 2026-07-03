package com.memoria.core.session;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public Session creerSession(String titre) {
        Session session = new Session(titre);
        return sessionRepository.save(session);
    }

    public Session obtenirSession(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new SessionNotFoundException(id));
    }

    public Session terminerSession(UUID id) {
        Session session = obtenirSession(id);
        session.terminer();
        return sessionRepository.save(session);
    }
}

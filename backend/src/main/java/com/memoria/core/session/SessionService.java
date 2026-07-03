package com.memoria.core.session;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SessionService(SessionRepository sessionRepository, ApplicationEventPublisher eventPublisher) {
        this.sessionRepository = sessionRepository;
        this.eventPublisher = eventPublisher;
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
        boolean etaitDejaTerminee = session.getStatut() == SessionStatus.TERMINEE;
        session.terminer();
        Session sessionSauvegardee = sessionRepository.save(session);
        if (!etaitDejaTerminee) {
            eventPublisher.publishEvent(new SessionTermineeEvent(id));
        }
        return sessionSauvegardee;
    }
}

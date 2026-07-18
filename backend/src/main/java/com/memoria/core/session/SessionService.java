package com.memoria.core.session;

import com.memoria.core.couloir.MembreCouloirRepository;
import com.memoria.core.couloir.PasMembreDuCouloirException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MembreCouloirRepository membreCouloirRepository;

    public SessionService(
            SessionRepository sessionRepository,
            ApplicationEventPublisher eventPublisher,
            MembreCouloirRepository membreCouloirRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.eventPublisher = eventPublisher;
        this.membreCouloirRepository = membreCouloirRepository;
    }

    public Session creerSession(String titre) {
        Session session = new Session(titre);
        return sessionRepository.save(session);
    }

    // Rattache la session a un couloir seulement si le createur en est
    // membre -- pas de restriction de visibilite ajoutee (toute session
    // reste visible a tout utilisateur connecte comme avant), juste un
    // regroupement.
    public Session creerSession(String titre, UUID couloirId, UUID createurId) {
        if (!membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloirId, createurId)) {
            throw new PasMembreDuCouloirException(couloirId, createurId);
        }
        Session session = new Session(titre, couloirId);
        return sessionRepository.save(session);
    }

    public List<Session> listerSessionsParCouloir(UUID couloirId) {
        return sessionRepository.findByCouloirIdOrderByDateCreationDesc(couloirId);
    }

    public Session obtenirSession(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new SessionNotFoundException(id));
    }

    public List<Session> listerSessions() {
        return sessionRepository.findAllByOrderByDateCreationDesc();
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

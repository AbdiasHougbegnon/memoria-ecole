package com.memoria.ecole.session;

import com.memoria.core.session.Session;
import com.memoria.core.session.SessionService;
import com.memoria.ecole.matiere.Matiere;
import com.memoria.ecole.matiere.MatiereService;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

// Rattache une session (core, moteur generique) a une matiere (Ecole) sans
// faire porter ce vocabulaire par l'entite moteur -- meme pattern que
// ContexteScolaireCouloir pour Couloir. Idempotent : rattacher une nouvelle
// matiere a une session deja taguee remplace le rattachement precedent
// plutot que d'echouer, pour permettre a l'etudiant de se corriger.
@Service
public class ContexteScolaireSessionService {

    private final ContexteScolaireSessionRepository contexteScolaireSessionRepository;
    private final SessionService sessionService;
    private final MatiereService matiereService;

    public ContexteScolaireSessionService(
            ContexteScolaireSessionRepository contexteScolaireSessionRepository,
            SessionService sessionService,
            MatiereService matiereService
    ) {
        this.contexteScolaireSessionRepository = contexteScolaireSessionRepository;
        this.sessionService = sessionService;
        this.matiereService = matiereService;
    }

    public void rattacherMatiere(UUID sessionId, UUID matiereId, UUID utilisateurId) {
        Session session = sessionService.obtenirSession(sessionId);
        if (!utilisateurId.equals(session.getCreateurId())) {
            throw new PasCreateurDeLaSessionException(sessionId, utilisateurId);
        }
        Matiere matiere = matiereService.obtenirMatiere(matiereId);
        if (!matiere.getCouloirId().equals(session.getCouloirId())) {
            throw new MatiereIncompatibleAvecSessionException(matiereId, sessionId);
        }

        Optional<ContexteScolaireSession> existant = contexteScolaireSessionRepository.findBySessionId(sessionId);
        if (existant.isPresent()) {
            existant.get().changerMatiere(matiereId);
            contexteScolaireSessionRepository.save(existant.get());
        } else {
            contexteScolaireSessionRepository.save(new ContexteScolaireSession(sessionId, matiereId));
        }
    }
}

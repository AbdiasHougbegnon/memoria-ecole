package com.memoria.core.session;

import com.memoria.core.auth.Utilisateur;
import com.memoria.core.auth.UtilisateurRepository;
import com.memoria.core.couloir.MembreCouloir;
import com.memoria.core.couloir.MembreCouloirRepository;
import com.memoria.core.couloir.PasMembreDuCouloirException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MembreCouloirRepository membreCouloirRepository;
    private final UtilisateurRepository utilisateurRepository;

    public SessionService(
            SessionRepository sessionRepository,
            ApplicationEventPublisher eventPublisher,
            MembreCouloirRepository membreCouloirRepository,
            UtilisateurRepository utilisateurRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.eventPublisher = eventPublisher;
        this.membreCouloirRepository = membreCouloirRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    public Session creerSession(String titre) {
        Session session = new Session(titre);
        return sessionRepository.save(session);
    }

    // Sans couloir, mais avec un createur enregistre -- utilise par le
    // controleur des que la requete precise un utilisateur authentifie
    // (donc systematiquement depuis la Phase 5 securite).
    // Consentement verifie AVANT toute construction/sauvegarde, meme
    // doctrine que EmpreinteVocaleService.enregistrerConsentement (phase 21).
    public Session creerSession(String titre, UUID createurId, boolean consentementEnregistrement) {
        if (!consentementEnregistrement) {
            throw new ConsentementEnregistrementRequisException();
        }
        Session session = new Session(titreOuGenere(titre), createurId, null);
        session.confirmerConsentementEnregistrement();
        return sessionRepository.save(session);
    }

    // Rattache la session a un couloir seulement si le createur en est
    // membre. Le rattachement ne change rien a la visibilite par lui-meme :
    // c'est listerSessionsVisibles() qui l'exploite.
    public Session creerSession(String titre, UUID couloirId, UUID createurId, boolean consentementEnregistrement) {
        if (!consentementEnregistrement) {
            throw new ConsentementEnregistrementRequisException();
        }
        if (!membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloirId, createurId)) {
            throw new PasMembreDuCouloirException(couloirId, createurId);
        }
        titre = titreOuGenere(titre);
        Session session = new Session(titre, createurId, couloirId);
        session.confirmerConsentementEnregistrement();
        return sessionRepository.save(session);
    }

    // Titre desormais optionnel (phase 22a) : un titre plus specifique (ex. nom
    // de la matiere) peut etre calcule cote client avant l'appel -- le moteur
    // ne connait pas le concept de matiere (Ecole), donc ce repli generique
    // reste volontairement neutre. Jamais de null en base (Session.titre
    // reste nullable=false), juste un gabarit, pas un appel IA pour formatter
    // une date.
    private static String titreOuGenere(String titre) {
        if (titre != null && !titre.isBlank()) {
            return titre;
        }
        return "Session du " + DateTimeFormatter.ofPattern("dd/MM/yyyy").format(LocalDate.now());
    }

    public List<Session> listerSessionsParCouloir(UUID couloirId) {
        return sessionRepository.findByCouloirIdOrderByDateCreationDesc(couloirId);
    }

    public Session obtenirSession(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new SessionNotFoundException(id));
    }

    // Utilisee par le controleur de la liste principale : ne renvoie que ce
    // qui est visible pour cet utilisateur (ses propres sessions, celles de
    // ses couloirs, et les sessions anciennes sans createur enregistre).
    public List<Session> listerSessionsVisibles(UUID utilisateurId) {
        List<UUID> mesCouloirIds = membreCouloirRepository.findByUtilisateurId(utilisateurId).stream()
                .map(MembreCouloir::getCouloirId)
                .toList();
        if (mesCouloirIds.isEmpty()) {
            return sessionRepository.findByCreateurIdOrCreateurIdIsNullOrderByDateCreationDesc(utilisateurId);
        }
        return sessionRepository.findVisiblesPour(utilisateurId, mesCouloirIds);
    }

    // Non filtree, volontairement -- utilisee par RechercheService.reindexerHistorique()
    // pour le rattrapage d'indexation, qui doit voir tout l'historique
    // independamment de qui declenche l'operation. Ne pas la faire evoluer
    // vers une version filtree.
    public List<Session> listerSessions() {
        return sessionRepository.findAllByOrderByDateCreationDesc();
    }

    // Le createur de la session + les membres de son couloir (si rattachee),
    // dedupliques, resolus en emails. Utilise pour toute notification liee a
    // une session (rappels d'engagements, notification de completion) --
    // partage plutot que duplique, voir RappelEngagementService et
    // EngagementService.terminer.
    public List<String> resoudreEmailsParticipants(UUID sessionId) {
        Optional<Session> session = sessionRepository.findById(sessionId);
        if (session.isEmpty()) {
            return List.of();
        }

        Set<UUID> utilisateurIds = new HashSet<>();
        if (session.get().getCreateurId() != null) {
            utilisateurIds.add(session.get().getCreateurId());
        }
        if (session.get().getCouloirId() != null) {
            membreCouloirRepository.findByCouloirId(session.get().getCouloirId())
                    .forEach(membre -> utilisateurIds.add(membre.getUtilisateurId()));
        }

        return utilisateurIds.stream()
                .map(utilisateurRepository::findById)
                .flatMap(Optional::stream)
                .map(Utilisateur::getEmail)
                .toList();
    }

    public Session terminerSession(UUID id, UUID utilisateurId) {
        Session session = obtenirSession(id);
        verifierAccesSession(session, utilisateurId);
        boolean etaitDejaTerminee = session.getStatut() == SessionStatus.TERMINEE;
        session.terminer();
        Session sessionSauvegardee = sessionRepository.save(session);
        if (!etaitDejaTerminee) {
            eventPublisher.publishEvent(new SessionTermineeEvent(id));
        }
        return sessionSauvegardee;
    }

    // Point unique de verification d'acces a une session, reutilise par tout
    // service qui mute ou declenche un traitement paye (Azure) sur une
    // session : AudioChunkService, ResumeService, ResumeCoursService,
    // QcmService, CompteRenduService, EngagementService (via sessionId de
    // l'engagement). Autorise le createur, tout membre du couloir de
    // rattachement, ou n'importe qui si la session est anterieure a
    // l'introduction du createur (createurId null, meme doctrine que
    // listerSessionsVisibles -- voir audit du 2026-07-27).
    public void verifierAcces(UUID sessionId, UUID utilisateurId) {
        verifierAccesSession(obtenirSession(sessionId), utilisateurId);
    }

    private void verifierAccesSession(Session session, UUID utilisateurId) {
        if (session.getCreateurId() == null) {
            return;
        }
        if (session.getCreateurId().equals(utilisateurId)) {
            return;
        }
        if (session.getCouloirId() != null
                && membreCouloirRepository.existsByCouloirIdAndUtilisateurId(session.getCouloirId(), utilisateurId)) {
            return;
        }
        throw new AccesSessionRefuseException(session.getId(), utilisateurId);
    }
}

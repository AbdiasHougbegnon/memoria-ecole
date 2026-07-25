package com.memoria.core.gouvernance;

import com.memoria.core.auth.Utilisateur;
import com.memoria.core.auth.UtilisateurNotFoundException;
import com.memoria.core.auth.UtilisateurRepository;
import com.memoria.core.couloir.Couloir;
import com.memoria.core.couloir.CouloirRepository;
import com.memoria.core.couloir.CouloirService;
import com.memoria.core.couloir.MembreCouloir;
import com.memoria.core.couloir.MembreCouloirRepository;
import com.memoria.core.locuteur.EmpreinteVocale;
import com.memoria.core.locuteur.EmpreinteVocaleRepository;
import com.memoria.core.locuteur.EmpreinteVocaleService;
import com.memoria.core.session.Session;
import com.memoria.core.session.SessionRepository;
import com.memoria.core.transcription.Transcription;
import com.memoria.core.transcription.TranscriptionRepository;
import com.memoria.ecole.notion.MaitriseNotion;
import com.memoria.ecole.notion.MaitriseNotionRepository;
import com.memoria.ecole.resumecours.ResumeCours;
import com.memoria.ecole.resumecours.ResumeCoursRepository;
import com.memoria.ecole.tuteurvocal.SeanceTutorat;
import com.memoria.ecole.tuteurvocal.SeanceTutoratRepository;
import com.memoria.ecole.tuteurvocal.TourDialogueTutorat;
import com.memoria.ecole.tuteurvocal.TourDialogueTutoratRepository;
import com.memoria.entreprise.compterendu.CompteRendu;
import com.memoria.entreprise.compterendu.CompteRenduRepository;
import com.memoria.entreprise.engagement.Engagement;
import com.memoria.entreprise.engagement.EngagementRepository;
import com.memoria.core.resume.Resume;
import com.memoria.core.resume.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// Droit a l'effacement + export (voir docs/phases/phase-13-gouvernance-donnees.md).
// Deux methodes publiques distinctes pour l'effacement (effacerCompte,
// finaliserEffacement) plutot qu'une seule : @Transactional ne s'applique pas
// aux appels internes a une meme instance Spring (self-invocation), donc le
// controleur orchestre les deux etapes (voir GouvernanceDonneesController) --
// c'est exactement le role d'un controleur ("orchestrent, ne decident pas").
@Service
public class GouvernanceDonneesService {

    private static final Logger LOG = LoggerFactory.getLogger(GouvernanceDonneesService.class);

    private final UtilisateurRepository utilisateurRepository;
    private final EmpreinteVocaleService empreinteVocaleService;
    private final EmpreinteVocaleRepository empreinteVocaleRepository;
    private final SeanceTutoratRepository seanceTutoratRepository;
    private final TourDialogueTutoratRepository tourDialogueTutoratRepository;
    private final MaitriseNotionRepository maitriseNotionRepository;
    private final CouloirRepository couloirRepository;
    private final CouloirService couloirService;
    private final MembreCouloirRepository membreCouloirRepository;
    private final SessionRepository sessionRepository;
    private final EngagementRepository engagementRepository;
    private final TranscriptionRepository transcriptionRepository;
    private final ResumeRepository resumeRepository;
    private final CompteRenduRepository compteRenduRepository;
    private final ResumeCoursRepository resumeCoursRepository;
    private final SessionPurgeService sessionPurgeService;
    private final JournalRgpdRepository journalRgpdRepository;

    public GouvernanceDonneesService(
            UtilisateurRepository utilisateurRepository,
            EmpreinteVocaleService empreinteVocaleService,
            EmpreinteVocaleRepository empreinteVocaleRepository,
            SeanceTutoratRepository seanceTutoratRepository,
            TourDialogueTutoratRepository tourDialogueTutoratRepository,
            MaitriseNotionRepository maitriseNotionRepository,
            CouloirRepository couloirRepository,
            CouloirService couloirService,
            MembreCouloirRepository membreCouloirRepository,
            SessionRepository sessionRepository,
            EngagementRepository engagementRepository,
            TranscriptionRepository transcriptionRepository,
            ResumeRepository resumeRepository,
            CompteRenduRepository compteRenduRepository,
            ResumeCoursRepository resumeCoursRepository,
            SessionPurgeService sessionPurgeService,
            JournalRgpdRepository journalRgpdRepository
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.empreinteVocaleService = empreinteVocaleService;
        this.empreinteVocaleRepository = empreinteVocaleRepository;
        this.seanceTutoratRepository = seanceTutoratRepository;
        this.tourDialogueTutoratRepository = tourDialogueTutoratRepository;
        this.maitriseNotionRepository = maitriseNotionRepository;
        this.couloirRepository = couloirRepository;
        this.couloirService = couloirService;
        this.membreCouloirRepository = membreCouloirRepository;
        this.sessionRepository = sessionRepository;
        this.engagementRepository = engagementRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.resumeRepository = resumeRepository;
        this.compteRenduRepository = compteRenduRepository;
        this.resumeCoursRepository = resumeCoursRepository;
        this.sessionPurgeService = sessionPurgeService;
        this.journalRgpdRepository = journalRgpdRepository;
    }

    // Etape 1 (transactionnelle, Postgres uniquement) : anonymise/supprime
    // tout ce qui appartient a l'utilisateur, sauf les sessions personnelles
    // exclusives dont la purge complete (disque + Azure AI Search) est
    // best-effort et doit arriver APRES le commit -- voir finaliserEffacement.
    @Transactional
    public List<UUID> effacerCompte(UUID utilisateurId) {
        if (!utilisateurRepository.existsById(utilisateurId)) {
            throw new UtilisateurNotFoundException(utilisateurId);
        }

        empreinteVocaleService.revoquer(utilisateurId);

        for (SeanceTutorat seance : seanceTutoratRepository.findByUtilisateurId(utilisateurId)) {
            tourDialogueTutoratRepository.deleteBySeanceTutoratId(seance.getId());
            seanceTutoratRepository.delete(seance);
        }

        maitriseNotionRepository.deleteByUtilisateurId(utilisateurId);

        for (Couloir couloir : couloirRepository.findByProprietaireId(utilisateurId)) {
            List<MembreCouloir> autresMembres = membreCouloirRepository.findByCouloirId(couloir.getId()).stream()
                    .filter(membre -> !membre.getUtilisateurId().equals(utilisateurId))
                    .sorted(Comparator.comparing(MembreCouloir::getDateAdhesion))
                    .toList();
            if (autresMembres.isEmpty()) {
                couloirService.supprimerCouloir(couloir.getId(), utilisateurId);
            } else {
                couloirService.transfererPropriete(couloir.getId(), autresMembres.get(0).getUtilisateurId(), utilisateurId);
            }
        }
        membreCouloirRepository.deleteByUtilisateurId(utilisateurId);

        List<UUID> sessionsAPurger = new ArrayList<>();
        for (Session session : sessionRepository.findByCreateurId(utilisateurId)) {
            if (session.getCouloirId() == null) {
                sessionsAPurger.add(session.getId());
            } else {
                session.anonymiserCreateur();
                sessionRepository.save(session);
            }
        }

        engagementRepository.anonymiserResponsable(utilisateurId);
        transcriptionRepository.anonymiserSegmentsLocuteur(utilisateurId);

        utilisateurRepository.deleteById(utilisateurId);

        return sessionsAPurger;
    }

    // Etape 2 (best-effort, hors transaction) : purge complete des sessions
    // personnelles exclusives collectees a l'etape 1, puis journalisation.
    // Jamais transactionnel avec effacerCompte -- voir doctrine
    // SessionPurgeService.nettoyerDependancesExternes.
    public void finaliserEffacement(UUID utilisateurId, List<UUID> sessionsAPurger) {
        for (UUID sessionId : sessionsAPurger) {
            try {
                sessionPurgeService.purgerSessionCompletement(sessionId);
            } catch (Exception e) {
                LOG.warn("Echec de la purge complete de la session {} lors de l'effacement du compte {}", sessionId, utilisateurId, e);
                continue;
            }
            sessionPurgeService.nettoyerDependancesExternes(sessionId);
        }
        journalRgpdRepository.save(new JournalRgpd(
                TypeActionRgpd.EFFACEMENT_COMPTE, utilisateurId,
                sessionsAPurger.size() + " session(s) personnelle(s) purgee(s)"
        ));
    }

    public ExportDonneesUtilisateur exporterDonnees(UUID utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new UtilisateurNotFoundException(utilisateurId));

        List<ExportDonneesUtilisateur.SessionExportee> sessions = sessionRepository.findByCreateurId(utilisateurId).stream()
                .map(this::exporterSession)
                .toList();

        List<ExportDonneesUtilisateur.EngagementExporte> engagements = engagementRepository.findByResponsableUtilisateurId(utilisateurId).stream()
                .map(e -> new ExportDonneesUtilisateur.EngagementExporte(
                        e.getId(), e.getSessionId(), e.getDescription(), e.getEcheance(), e.getStatut().name()))
                .toList();

        ExportDonneesUtilisateur.EmpreinteVocaleExportee empreinte = empreinteVocaleRepository.findByUtilisateurId(utilisateurId)
                .map(this::exporterEmpreinte)
                .orElse(new ExportDonneesUtilisateur.EmpreinteVocaleExportee(false, null));

        List<ExportDonneesUtilisateur.SeanceTutoratExportee> seancesTutorat = seanceTutoratRepository.findByUtilisateurId(utilisateurId).stream()
                .map(this::exporterSeanceTutorat)
                .toList();

        List<ExportDonneesUtilisateur.MaitriseNotionExportee> maitrises = maitriseNotionRepository.findByUtilisateurId(utilisateurId).stream()
                .map(m -> new ExportDonneesUtilisateur.MaitriseNotionExportee(m.getNotionId(), m.getNiveau().name(), m.getNombreTentatives()))
                .toList();

        List<ExportDonneesUtilisateur.CouloirExporte> couloirs = membreCouloirRepository.findByUtilisateurId(utilisateurId).stream()
                .map(membre -> {
                    Couloir couloir = couloirRepository.findById(membre.getCouloirId()).orElse(null);
                    if (couloir == null) {
                        return null;
                    }
                    return new ExportDonneesUtilisateur.CouloirExporte(
                            couloir.getId(), couloir.getNom(), couloir.getProprietaireId().equals(utilisateurId));
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        return new ExportDonneesUtilisateur(
                utilisateur.getId(), utilisateur.getEmail(), utilisateur.getNom(), utilisateur.getDateCreation(),
                utilisateur.getModule(), sessions, engagements, empreinte, seancesTutorat, maitrises, couloirs
        );
    }

    private ExportDonneesUtilisateur.SessionExportee exporterSession(Session session) {
        String transcriptionComplete = transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(session.getId()).stream()
                .map(Transcription::getTexte)
                .filter(java.util.Objects::nonNull)
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);

        List<ExportDonneesUtilisateur.ResumeExporte> resumes = resumeRepository.findBySessionId(session.getId()).stream()
                .map((Resume r) -> new ExportDonneesUtilisateur.ResumeExporte(r.getType().name(), r.getTexteResume()))
                .toList();

        String compteRendu = compteRenduRepository.findBySessionId(session.getId())
                .map(CompteRendu::getSynthese)
                .orElse(null);

        String resumeCours = resumeCoursRepository.findBySessionId(session.getId())
                .map(ResumeCours::getSynthese)
                .orElse(null);

        return new ExportDonneesUtilisateur.SessionExportee(
                session.getId(), session.getTitre(), session.getDateCreation(), session.getCouloirId(),
                transcriptionComplete, resumes, compteRendu, resumeCours
        );
    }

    private ExportDonneesUtilisateur.EmpreinteVocaleExportee exporterEmpreinte(EmpreinteVocale empreinte) {
        return new ExportDonneesUtilisateur.EmpreinteVocaleExportee(true, empreinte.getDateConsentement());
    }

    private ExportDonneesUtilisateur.SeanceTutoratExportee exporterSeanceTutorat(SeanceTutorat seance) {
        List<ExportDonneesUtilisateur.TourExporte> tours = tourDialogueTutoratRepository
                .findBySeanceTutoratIdOrderByDateCreationAsc(seance.getId()).stream()
                .map((TourDialogueTutorat t) -> new ExportDonneesUtilisateur.TourExporte(
                        t.getLocuteur().name(), t.getTexte(), t.getDateCreation()))
                .toList();
        return new ExportDonneesUtilisateur.SeanceTutoratExportee(
                seance.getId(), seance.getSeanceId(), seance.getStatut().name(), seance.isModeExercice(),
                seance.getDateDebut(), seance.getDateFin(), tours
        );
    }
}

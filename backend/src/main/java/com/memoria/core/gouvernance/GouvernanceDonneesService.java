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
import com.memoria.core.resume.Resume;
import com.memoria.core.resume.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Droit a l'effacement + export (voir docs/phases/phase-13-gouvernance-donnees.md).
// Deux methodes publiques distinctes pour l'effacement (effacerCompte,
// finaliserEffacement) plutot qu'une seule : @Transactional ne s'applique pas
// aux appels internes a une meme instance Spring (self-invocation), donc le
// controleur orchestre les deux etapes (voir GouvernanceDonneesController) --
// c'est exactement le role d'un controleur ("orchestrent, ne decident pas").
//
// Les donnees specifiques a un produit (Ecole, Entreprise) sont effacees et
// exportees via EffaceurDonneesUtilisateurPort/ExportateurDonneesUtilisateurPort,
// implementes par chaque produit et collectes automatiquement par Spring --
// ce service core ne connait plus aucun type Ecole/Entreprise concret (voir
// audit du 2026-07-27, avant cette extraction il importait directement
// SeanceTutorat, Engagement, ResumeCours, etc.).
@Service
public class GouvernanceDonneesService {

    private static final Logger LOG = LoggerFactory.getLogger(GouvernanceDonneesService.class);

    private final UtilisateurRepository utilisateurRepository;
    private final EmpreinteVocaleService empreinteVocaleService;
    private final EmpreinteVocaleRepository empreinteVocaleRepository;
    private final CouloirRepository couloirRepository;
    private final CouloirService couloirService;
    private final MembreCouloirRepository membreCouloirRepository;
    private final SessionRepository sessionRepository;
    private final TranscriptionRepository transcriptionRepository;
    private final ResumeRepository resumeRepository;
    private final SessionPurgeService sessionPurgeService;
    private final JournalRgpdRepository journalRgpdRepository;
    private final List<EffaceurDonneesUtilisateurPort> effaceursProduits;
    private final List<ExportateurDonneesUtilisateurPort> exportateursProduits;

    public GouvernanceDonneesService(
            UtilisateurRepository utilisateurRepository,
            EmpreinteVocaleService empreinteVocaleService,
            EmpreinteVocaleRepository empreinteVocaleRepository,
            CouloirRepository couloirRepository,
            CouloirService couloirService,
            MembreCouloirRepository membreCouloirRepository,
            SessionRepository sessionRepository,
            TranscriptionRepository transcriptionRepository,
            ResumeRepository resumeRepository,
            SessionPurgeService sessionPurgeService,
            JournalRgpdRepository journalRgpdRepository,
            List<EffaceurDonneesUtilisateurPort> effaceursProduits,
            List<ExportateurDonneesUtilisateurPort> exportateursProduits
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.empreinteVocaleService = empreinteVocaleService;
        this.empreinteVocaleRepository = empreinteVocaleRepository;
        this.couloirRepository = couloirRepository;
        this.couloirService = couloirService;
        this.membreCouloirRepository = membreCouloirRepository;
        this.sessionRepository = sessionRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.resumeRepository = resumeRepository;
        this.sessionPurgeService = sessionPurgeService;
        this.journalRgpdRepository = journalRgpdRepository;
        this.effaceursProduits = effaceursProduits;
        this.exportateursProduits = exportateursProduits;
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
        effaceursProduits.forEach(effaceur -> effaceur.effacerDonneesUtilisateur(utilisateurId));

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

        transcriptionRepository.anonymiserSegmentsLocuteur(utilisateurId);

        utilisateurRepository.deleteById(utilisateurId);

        return sessionsAPurger;
    }

    // Etape 2 (best-effort, hors transaction) : purge complete des sessions
    // personnelles exclusives collectees a l'etape 1, puis journalisation.
    // Jamais transactionnel avec effacerCompte -- voir doctrine
    // SessionPurgeService.nettoyerDependancesExternes.
    // Self-service (initiateur = la cible elle-meme) : voir la variante 3-arg
    // pour l'effacement declenche par un admin au nom d'autrui (phase 20).
    public void finaliserEffacement(UUID utilisateurId, List<UUID> sessionsAPurger) {
        finaliserEffacement(utilisateurId, sessionsAPurger, null);
    }

    public void finaliserEffacement(UUID utilisateurId, List<UUID> sessionsAPurger, UUID initiateurId) {
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
                TypeActionRgpd.EFFACEMENT_COMPTE, utilisateurId, initiateurId,
                sessionsAPurger.size() + " session(s) personnelle(s) purgee(s)"
        ));
    }

    // Resout la cible d'un effacement declenche par un admin (phase 20) : les
    // demandes RGPD arrivent par email (support, formulaire), pas par UUID.
    // Insensible a la casse (voir UtilisateurRepository.findByEmailIgnoreCase).
    public UUID resoudreParEmail(String email) {
        return utilisateurRepository.findByEmailIgnoreCase(email)
                .map(Utilisateur::getId)
                .orElseThrow(() -> new UtilisateurNotFoundException(email));
    }

    public ExportDonneesUtilisateur exporterDonnees(UUID utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new UtilisateurNotFoundException(utilisateurId));

        List<ExportDonneesUtilisateur.SessionExportee> sessions = sessionRepository.findByCreateurId(utilisateurId).stream()
                .map(this::exporterSession)
                .toList();

        List<ExportDonneesUtilisateur.EngagementExporte> engagements = exportateursProduits.stream()
                .flatMap(exportateur -> exportateur.exporterEngagements(utilisateurId).stream())
                .toList();

        ExportDonneesUtilisateur.EmpreinteVocaleExportee empreinte = empreinteVocaleRepository.findByUtilisateurId(utilisateurId)
                .map(this::exporterEmpreinte)
                .orElse(new ExportDonneesUtilisateur.EmpreinteVocaleExportee(false, null));

        List<ExportDonneesUtilisateur.SeanceTutoratExportee> seancesTutorat = exportateursProduits.stream()
                .flatMap(exportateur -> exportateur.exporterSeancesTutorat(utilisateurId).stream())
                .toList();

        List<ExportDonneesUtilisateur.MaitriseNotionExportee> maitrises = exportateursProduits.stream()
                .flatMap(exportateur -> exportateur.exporterMaitrises(utilisateurId).stream())
                .toList();

        List<ExportDonneesUtilisateur.TentativeQcmExportee> tentativesQcm = exportateursProduits.stream()
                .flatMap(exportateur -> exportateur.exporterTentativesQcm(utilisateurId).stream())
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
                utilisateur.getModule(), sessions, engagements, empreinte, seancesTutorat, maitrises, tentativesQcm, couloirs
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

        // Au plus un produit repond present pour une session donnee (Ecole XOR
        // Entreprise) -- voir ExportateurDonneesUtilisateurPort.
        String compteRendu = exportateursProduits.stream()
                .map(exportateur -> exportateur.exporterCompteRenduSession(session.getId()))
                .flatMap(Optional::stream)
                .findFirst().orElse(null);

        String resumeCours = exportateursProduits.stream()
                .map(exportateur -> exportateur.exporterResumeCoursSession(session.getId()))
                .flatMap(Optional::stream)
                .findFirst().orElse(null);

        return new ExportDonneesUtilisateur.SessionExportee(
                session.getId(), session.getTitre(), session.getDateCreation(), session.getCouloirId(),
                transcriptionComplete, resumes, compteRendu, resumeCours
        );
    }

    private ExportDonneesUtilisateur.EmpreinteVocaleExportee exporterEmpreinte(EmpreinteVocale empreinte) {
        return new ExportDonneesUtilisateur.EmpreinteVocaleExportee(true, empreinte.getDateConsentement());
    }
}

package com.memoria.ecole.tuteurvocal;

import com.memoria.core.couloir.CouloirService;
import com.memoria.core.couloir.PasMembreDuCouloirException;
import com.memoria.core.transcription.TranscripteurPort;
import com.memoria.ecole.notion.NiveauMaitrise;
import com.memoria.ecole.notion.Notion;
import com.memoria.ecole.notion.NotionService;
import com.memoria.ecole.seance.Seance;
import com.memoria.ecole.seance.SeanceService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Orchestration du dialogue tour par tour : STT (reutilise tel quel) -> IA
// (evaluation de maitrise + prochaine repartie) -> mise a jour de la
// maitrise -> avance de notion si maitrisee. La synthese vocale (TTS) N'EST
// PAS appelee ici : elle est resynthetisee a la demande par
// obtenirAudioDuTour, cote endpoint audio dedie -- voir
// docs/phases/phase-9-tuteur-vocal.md.
@Service
public class TuteurVocalService {

    private final SeanceTutoratRepository seanceTutoratRepository;
    private final TourDialogueTutoratRepository tourDialogueTutoratRepository;
    private final SeanceService seanceService;
    private final NotionService notionService;
    private final CouloirService couloirService;
    private final TranscripteurPort transcripteur;
    private final SynthetiseurVocalPort synthetiseurVocal;
    private final GenerateurTourTuteurPort generateurTourTuteur;

    public TuteurVocalService(
            SeanceTutoratRepository seanceTutoratRepository,
            TourDialogueTutoratRepository tourDialogueTutoratRepository,
            SeanceService seanceService,
            NotionService notionService,
            CouloirService couloirService,
            TranscripteurPort transcripteur,
            SynthetiseurVocalPort synthetiseurVocal,
            GenerateurTourTuteurPort generateurTourTuteur
    ) {
        this.seanceTutoratRepository = seanceTutoratRepository;
        this.tourDialogueTutoratRepository = tourDialogueTutoratRepository;
        this.seanceService = seanceService;
        this.notionService = notionService;
        this.couloirService = couloirService;
        this.transcripteur = transcripteur;
        this.synthetiseurVocal = synthetiseurVocal;
        this.generateurTourTuteur = generateurTourTuteur;
    }

    @Transactional
    public ResultatTour demarrerTutorat(UUID seanceId, UUID utilisateurId, boolean modeExercice) {
        Seance seance = seanceService.obtenirSeance(seanceId);
        if (!couloirService.estMembre(seance.getCouloirId(), utilisateurId)) {
            throw new PasMembreDuCouloirException(seance.getCouloirId(), utilisateurId);
        }

        // Idempotent : reprendre une seance de tutorat deja en cours plutot
        // que d'en creer une deuxieme -- meme esprit que rejoindreCouloir.
        Optional<SeanceTutorat> existante = seanceTutoratRepository
                .findBySeanceIdAndUtilisateurIdAndStatut(seanceId, utilisateurId, StatutSeanceTutorat.EN_COURS);
        if (existante.isPresent()) {
            SeanceTutorat seanceTutorat = existante.get();
            TourDialogueTutorat dernierTour = dernierTour(seanceTutorat.getId());
            return versResultat(seanceTutorat, dernierTour, seanceTutorat.getNotionCouranteId() != null
                    ? notionService.obtenirNiveauMaitrise(seanceTutorat.getNotionCouranteId(), utilisateurId)
                    : NiveauMaitrise.MAITRISEE);
        }

        List<Notion> notionsOrdonnees = seanceService.listerNotionsDeSeance(seanceId);
        ModeTutorat mode = modeExercice ? ModeTutorat.EXERCICE : ModeTutorat.EXPLICATION;
        Optional<UUID> premiereNotionId = choisirProchaineNotion(notionsOrdonnees, utilisateurId);

        SeanceTutorat seanceTutorat = seanceTutoratRepository.save(
                new SeanceTutorat(seanceId, utilisateurId, premiereNotionId.orElse(null), modeExercice)
        );

        if (premiereNotionId.isEmpty()) {
            // Aucune notion, ou toutes deja maitrisees avant meme de commencer.
            seanceTutorat.terminer();
            seanceTutoratRepository.save(seanceTutorat);
            return new ResultatTour(seanceTutorat.getId(), null, "Toutes les notions de cette seance sont deja maitrisees.",
                    null, NiveauMaitrise.MAITRISEE, true);
        }

        Notion notion = notionService.obtenirNotion(premiereNotionId.get());
        var contexte = new GenerateurTourTuteurPort.ContexteTour(notion.getTerme(), notion.getDefinition(), List.of(), null, mode);
        GenerateurTourTuteurPort.TourTuteurGenere genere = appellerGenerateur(contexte);

        TourDialogueTutorat tour = tourDialogueTutoratRepository.save(
                new TourDialogueTutorat(seanceTutorat.getId(), notion.getId(), Locuteur.TUTEUR, genere.texteTuteur())
        );

        return new ResultatTour(seanceTutorat.getId(), tour.getId(), genere.texteTuteur(), notion.getId(),
                NiveauMaitrise.NON_ABORDEE, false);
    }

    @Transactional
    public ResultatTour soumettreReponse(UUID seanceTutoratId, byte[] audioReponse, UUID utilisateurId) {
        SeanceTutorat seanceTutorat = obtenirEtVerifierProprietaire(seanceTutoratId, utilisateurId);
        if (seanceTutorat.getStatut() != StatutSeanceTutorat.EN_COURS) {
            throw new SeanceTutoratNonActiveException(seanceTutoratId);
        }

        UUID notionCouranteId = seanceTutorat.getNotionCouranteId();
        Notion notionCourante = notionService.obtenirNotion(notionCouranteId);
        ModeTutorat mode = seanceTutorat.isModeExercice() ? ModeTutorat.EXERCICE : ModeTutorat.EXPLICATION;

        String texteEtudiant = transcrire(audioReponse);

        List<GenerateurTourTuteurPort.TourHistorique> historique = tourDialogueTutoratRepository
                .findBySeanceTutoratIdAndNotionIdOrderByDateCreationAsc(seanceTutoratId, notionCouranteId).stream()
                .map(tour -> new GenerateurTourTuteurPort.TourHistorique(tour.getLocuteur(), tour.getTexte()))
                .toList();

        tourDialogueTutoratRepository.save(new TourDialogueTutorat(seanceTutoratId, notionCouranteId, Locuteur.ETUDIANT, texteEtudiant));

        var contexte = new GenerateurTourTuteurPort.ContexteTour(
                notionCourante.getTerme(), notionCourante.getDefinition(), historique, texteEtudiant, mode
        );
        GenerateurTourTuteurPort.TourTuteurGenere genere = appellerGenerateur(contexte);

        notionService.mettreAJourMaitrise(notionCouranteId, utilisateurId, genere.evaluationMaitrise());

        String texteAEnvoyer = genere.texteTuteur();
        UUID notionPourTour = notionCouranteId;
        boolean seanceTerminee = false;

        if (genere.notionMaitrisee()) {
            List<Notion> notionsOrdonnees = seanceService.listerNotionsDeSeance(seanceTutorat.getSeanceId());
            Optional<UUID> prochaineNotionId = choisirProchaineNotion(notionsOrdonnees, utilisateurId);
            if (prochaineNotionId.isPresent()) {
                notionPourTour = prochaineNotionId.get();
                seanceTutorat.avancerNotion(notionPourTour);
                Notion nouvelleNotion = notionService.obtenirNotion(notionPourTour);
                var contexteOuverture = new GenerateurTourTuteurPort.ContexteTour(
                        nouvelleNotion.getTerme(), nouvelleNotion.getDefinition(), List.of(), null, mode
                );
                GenerateurTourTuteurPort.TourTuteurGenere ouverture = appellerGenerateur(contexteOuverture);
                texteAEnvoyer = genere.texteTuteur() + " " + ouverture.texteTuteur();
            } else {
                seanceTutorat.terminer();
                seanceTerminee = true;
            }
        }

        TourDialogueTutorat tourTuteur = tourDialogueTutoratRepository.save(
                new TourDialogueTutorat(seanceTutoratId, notionPourTour, Locuteur.TUTEUR, texteAEnvoyer)
        );
        seanceTutoratRepository.save(seanceTutorat);

        return new ResultatTour(seanceTutoratId, tourTuteur.getId(), texteAEnvoyer, seanceTutorat.getNotionCouranteId(),
                genere.evaluationMaitrise(), seanceTerminee);
    }

    public SeanceTutorat obtenirEtatTutorat(UUID seanceTutoratId, UUID utilisateurId) {
        return obtenirEtVerifierProprietaire(seanceTutoratId, utilisateurId);
    }

    public List<TourDialogueTutorat> listerTours(UUID seanceTutoratId) {
        return tourDialogueTutoratRepository.findBySeanceTutoratIdOrderByDateCreationAsc(seanceTutoratId);
    }

    @Transactional
    public SeanceTutorat arreterTutorat(UUID seanceTutoratId, UUID utilisateurId) {
        SeanceTutorat seanceTutorat = obtenirEtVerifierProprietaire(seanceTutoratId, utilisateurId);
        seanceTutorat.terminer();
        return seanceTutoratRepository.save(seanceTutorat);
    }

    // Aucun audio n'est persiste (voir TourDialogueTutorat) : resynthetise a
    // chaque appel, cout negligeable pour un texte deja court.
    public byte[] obtenirAudioDuTour(UUID tourId) {
        TourDialogueTutorat tour = tourDialogueTutoratRepository.findById(tourId)
                .orElseThrow(() -> new TourDialogueTutoratNotFoundException(tourId));
        return synthetiseurVocal.synthetiser(tour.getTexte());
    }

    private SeanceTutorat obtenirEtVerifierProprietaire(UUID seanceTutoratId, UUID utilisateurId) {
        SeanceTutorat seanceTutorat = seanceTutoratRepository.findById(seanceTutoratId)
                .orElseThrow(() -> new SeanceTutoratNotFoundException(seanceTutoratId));
        if (!seanceTutorat.getUtilisateurId().equals(utilisateurId)) {
            throw new AccesTutoratRefuseException(seanceTutoratId, utilisateurId);
        }
        return seanceTutorat;
    }

    private Optional<UUID> choisirProchaineNotion(List<Notion> notionsOrdonnees, UUID utilisateurId) {
        return notionsOrdonnees.stream()
                .filter(notion -> notionService.obtenirNiveauMaitrise(notion.getId(), utilisateurId) != NiveauMaitrise.MAITRISEE)
                .map(Notion::getId)
                .findFirst();
    }

    private String transcrire(byte[] audio) {
        try {
            return transcripteur.transcrire(audio).texteComplet();
        } catch (RuntimeException e) {
            throw new TraitementTourTutoratException("Echec de la transcription de la reponse", e);
        }
    }

    private GenerateurTourTuteurPort.TourTuteurGenere appellerGenerateur(GenerateurTourTuteurPort.ContexteTour contexte) {
        try {
            return generateurTourTuteur.genererTour(contexte);
        } catch (GenerationTourTuteurException e) {
            throw new TraitementTourTutoratException("Echec de la generation du tour du tuteur", e);
        }
    }

    private TourDialogueTutorat dernierTour(UUID seanceTutoratId) {
        List<TourDialogueTutorat> tours = listerTours(seanceTutoratId);
        return tours.isEmpty() ? null : tours.get(tours.size() - 1);
    }

    private ResultatTour versResultat(SeanceTutorat seanceTutorat, TourDialogueTutorat dernierTour, NiveauMaitrise niveauMaitrise) {
        return new ResultatTour(
                seanceTutorat.getId(),
                dernierTour != null ? dernierTour.getId() : null,
                dernierTour != null ? dernierTour.getTexte() : "",
                seanceTutorat.getNotionCouranteId(),
                niveauMaitrise,
                seanceTutorat.getStatut() == StatutSeanceTutorat.TERMINEE
        );
    }
}

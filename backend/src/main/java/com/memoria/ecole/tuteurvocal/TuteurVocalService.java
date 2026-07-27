package com.memoria.ecole.tuteurvocal;

import com.memoria.core.couloir.CouloirService;
import com.memoria.core.couloir.PasMembreDuCouloirException;
import com.memoria.core.transcription.TranscripteurPort;
import com.memoria.ecole.matiere.Matiere;
import com.memoria.ecole.matiere.MatiereService;
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
import java.util.stream.Collectors;

// Orchestration du dialogue tour par tour : STT (reutilise tel quel) -> IA
// (evaluation de maitrise + prochaine repartie) -> mise a jour de la
// maitrise -> avance de notion si maitrisee. La synthese vocale (TTS) N'EST
// PAS appelee ici : elle est resynthetisee a la demande par
// obtenirAudioDuTour, cote endpoint audio dedie -- voir
// docs/phases/phase-9-tuteur-vocal.md.
@Service
public class TuteurVocalService {

    // Tient lieu de "notionDefinition" en mode LIBRE, ou aucune notion n'est
    // rattachee. Complete par les notions validees de la matiere quand il y
    // en a (phase 18 : fiches -> candidats -> validation enseignant), sinon
    // ce texte generique reste le seul contexte -- voir
    // docs/phases/phase-19-mode-conversation-libre.md.
    private static final String DEFINITION_MODE_LIBRE =
            "Discussion libre sur cette matiere, reponds aux questions de l'etudiant en le guidant progressivement.";

    private final SeanceTutoratRepository seanceTutoratRepository;
    private final TourDialogueTutoratRepository tourDialogueTutoratRepository;
    private final SeanceService seanceService;
    private final NotionService notionService;
    private final MatiereService matiereService;
    private final CouloirService couloirService;
    private final TranscripteurPort transcripteur;
    private final SynthetiseurVocalPort synthetiseurVocal;
    private final GenerateurTourTuteurPort generateurTourTuteur;

    public TuteurVocalService(
            SeanceTutoratRepository seanceTutoratRepository,
            TourDialogueTutoratRepository tourDialogueTutoratRepository,
            SeanceService seanceService,
            NotionService notionService,
            MatiereService matiereService,
            CouloirService couloirService,
            TranscripteurPort transcripteur,
            SynthetiseurVocalPort synthetiseurVocal,
            GenerateurTourTuteurPort generateurTourTuteur
    ) {
        this.seanceTutoratRepository = seanceTutoratRepository;
        this.tourDialogueTutoratRepository = tourDialogueTutoratRepository;
        this.seanceService = seanceService;
        this.notionService = notionService;
        this.matiereService = matiereService;
        this.couloirService = couloirService;
        this.transcripteur = transcripteur;
        this.synthetiseurVocal = synthetiseurVocal;
        this.generateurTourTuteur = generateurTourTuteur;
    }

    @Transactional
    public ResultatTour demarrerTutorat(UUID seanceId, UUID utilisateurId, ModeTutorat mode) {
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
            NiveauMaitrise niveauMaitrise = seanceTutorat.getMode() == ModeTutorat.LIBRE
                    ? null
                    : seanceTutorat.getNotionCouranteId() != null
                            ? notionService.obtenirNiveauMaitrise(seanceTutorat.getNotionCouranteId(), utilisateurId)
                            : NiveauMaitrise.MAITRISEE;
            return versResultat(seanceTutorat, dernierTour, niveauMaitrise);
        }

        if (mode == ModeTutorat.LIBRE) {
            // Pas de notion a resoudre, pas de premier tour genere : le
            // tuteur attend que l'etudiant parle en premier (voir
            // docs/phases/phase-19-mode-conversation-libre.md).
            SeanceTutorat seanceTutorat = seanceTutoratRepository.save(
                    new SeanceTutorat(seanceId, utilisateurId, null, ModeTutorat.LIBRE)
            );
            return new ResultatTour(seanceTutorat.getId(), null, "", null, null, false);
        }

        List<Notion> notionsOrdonnees = seanceService.listerNotionsDeSeance(seanceId);
        Optional<UUID> premiereNotionId = choisirProchaineNotion(notionsOrdonnees, utilisateurId);

        SeanceTutorat seanceTutorat = seanceTutoratRepository.save(
                new SeanceTutorat(seanceId, utilisateurId, premiereNotionId.orElse(null), mode)
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

        if (seanceTutorat.getMode() == ModeTutorat.LIBRE) {
            return soumettreReponseLibre(seanceTutorat, audioReponse);
        }

        UUID notionCouranteId = seanceTutorat.getNotionCouranteId();
        Notion notionCourante = notionService.obtenirNotion(notionCouranteId);
        ModeTutorat mode = seanceTutorat.getMode();

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

    // Pas de notion a evaluer : historique complet (pas filtre par notion,
    // toutes les autres requetes de ce service le sont), aucun appel a
    // mettreAJourMaitrise, jamais de fin automatique -- voir
    // docs/phases/phase-19-mode-conversation-libre.md.
    private ResultatTour soumettreReponseLibre(SeanceTutorat seanceTutorat, byte[] audioReponse) {
        UUID seanceTutoratId = seanceTutorat.getId();
        String texteEtudiant = transcrire(audioReponse);

        List<GenerateurTourTuteurPort.TourHistorique> historique = tourDialogueTutoratRepository
                .findBySeanceTutoratIdOrderByDateCreationAsc(seanceTutoratId).stream()
                .map(tour -> new GenerateurTourTuteurPort.TourHistorique(tour.getLocuteur(), tour.getTexte()))
                .toList();

        tourDialogueTutoratRepository.save(new TourDialogueTutorat(seanceTutoratId, null, Locuteur.ETUDIANT, texteEtudiant));

        Seance seance = seanceService.obtenirSeance(seanceTutorat.getSeanceId());
        Matiere matiere = matiereService.obtenirMatiere(seance.getMatiereId());

        var contexte = new GenerateurTourTuteurPort.ContexteTour(
                matiere.getNom(), construireContexteMatiere(matiere.getId()), historique, texteEtudiant, ModeTutorat.LIBRE
        );
        GenerateurTourTuteurPort.TourTuteurGenere genere = appellerGenerateur(contexte);

        TourDialogueTutorat tourTuteur = tourDialogueTutoratRepository.save(
                new TourDialogueTutorat(seanceTutoratId, null, Locuteur.TUTEUR, genere.texteTuteur())
        );

        return new ResultatTour(seanceTutoratId, tourTuteur.getId(), genere.texteTuteur(), null, null, false);
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

    // Notions validees de la matiere (phase 18 : fiches -> candidats ->
    // validation enseignant, seules des notions humainement confirmees
    // alimentent le tuteur -- jamais le texte brut d'un document, coherent
    // avec la doctrine de tracabilite/traabilite du projet). Se degrade vers
    // le texte generique si la matiere n'a encore aucune notion.
    private String construireContexteMatiere(UUID matiereId) {
        List<Notion> notions = notionService.listerNotionsParMatiere(matiereId);
        if (notions.isEmpty()) {
            return DEFINITION_MODE_LIBRE;
        }
        String connaissances = notions.stream()
                .map(notion -> "- " + notion.getTerme() + " : " + notion.getDefinition())
                .collect(Collectors.joining("\n"));
        return DEFINITION_MODE_LIBRE + "\n\nNotions au programme de cette matiere (appuie-toi dessus si pertinent) :\n" + connaissances;
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

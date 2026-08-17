package com.memoria.ecole.tuteurvocal;

import com.memoria.core.couloir.CouloirService;
import com.memoria.core.couloir.PasMembreDuCouloirException;
import com.memoria.core.document.StatutDocument;
import com.memoria.core.transcription.TranscripteurPort;
import com.memoria.ecole.exercice.ExercicePapier;
import com.memoria.ecole.exercice.ExercicePapierRepository;
import com.memoria.ecole.exercice.TravailPapierMatiere;
import com.memoria.ecole.exercice.TravailPapierMatiereRepository;
import com.memoria.ecole.matiere.AgregateurContenuMatiereService;
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

// Orchestration du dialogue tour par tour : STT ou saisie texte -> IA
// (evaluation de maitrise + prochaine repartie) -> mise a jour de la
// maitrise -> avance de notion si maitrisee (EXPLICATION) ou apres
// NOMBRE_EXERCICES_PAR_NOTION exercices (EXERCICE) -> bascule automatique
// EXPLICATION -> EXERCICE une fois toutes les notions maitrisees, sur la
// meme SeanceTutorat. La synthese vocale (TTS) N'EST PAS appelee ici : elle
// est resynthetisee a la demande par obtenirAudioDuTour, cote endpoint audio
// dedie -- voir docs/phases/phase-9-tuteur-vocal.md.
@Service
public class TuteurVocalService {

    // Tient lieu de "notionDefinition" en mode LIBRE, ou aucune notion n'est
    // rattachee. Complete par les notions validees de la matiere quand il y
    // en a (phase 18 : fiches -> candidats -> validation enseignant), sinon
    // ce texte generique reste le seul contexte -- voir
    // docs/phases/phase-19-mode-conversation-libre.md.
    private static final String DEFINITION_MODE_LIBRE =
            "Discussion libre sur cette matiere, reponds aux questions de l'etudiant en le guidant progressivement.";

    // Critere de fin propre au mode EXERCICE, independant de l'evaluation de
    // maitrise du LLM (utilisee uniquement en EXPLICATION) : chaque notion
    // est exercee ce nombre de fois avant de passer a la suivante.
    private static final int NOMBRE_EXERCICES_PAR_NOTION = 3;

    // Textes fixes (pas generes par le LLM, pour la fiabilite) annoncant la
    // bascule automatique vers le mode exercices -- deux variantes selon
    // qu'elle survient en cours de conversation (feedback de la derniere
    // notion deja dans texteAEnvoyer) ou des le demarrage (aucun tour
    // precedent, l'annonce ouvre la conversation).
    private static final String ANNONCE_TRANSITION_EXERCICE =
            "Tu maitrises toutes les notions ! Passons maintenant aux exercices pour verifier que tu sais les appliquer.";
    private static final String ANNONCE_TRANSITION_EXERCICE_DEPART =
            "Tu maitrises deja toutes les notions de cette seance ! Passons directement aux exercices pour verifier que tu sais les appliquer.";

    private final SeanceTutoratRepository seanceTutoratRepository;
    private final TourDialogueTutoratRepository tourDialogueTutoratRepository;
    private final SeanceService seanceService;
    private final NotionService notionService;
    private final MatiereService matiereService;
    private final CouloirService couloirService;
    private final TranscripteurPort transcripteur;
    private final SynthetiseurVocalPort synthetiseurVocal;
    private final GenerateurTourTuteurPort generateurTourTuteur;
    private final AgregateurContenuMatiereService agregateurContenuMatiereService;
    private final TravailPapierMatiereRepository travailPapierMatiereRepository;
    private final ExercicePapierRepository exercicePapierRepository;

    public TuteurVocalService(
            SeanceTutoratRepository seanceTutoratRepository,
            TourDialogueTutoratRepository tourDialogueTutoratRepository,
            SeanceService seanceService,
            NotionService notionService,
            MatiereService matiereService,
            CouloirService couloirService,
            TranscripteurPort transcripteur,
            SynthetiseurVocalPort synthetiseurVocal,
            GenerateurTourTuteurPort generateurTourTuteur,
            AgregateurContenuMatiereService agregateurContenuMatiereService,
            TravailPapierMatiereRepository travailPapierMatiereRepository,
            ExercicePapierRepository exercicePapierRepository
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
        this.travailPapierMatiereRepository = travailPapierMatiereRepository;
        this.exercicePapierRepository = exercicePapierRepository;
        this.agregateurContenuMatiereService = agregateurContenuMatiereService;
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
        if (notionsOrdonnees.isEmpty()) {
            return terminerSansNotion(seanceId, utilisateurId, mode);
        }

        // Mode EXERCICE : aucun filtre de maitrise, les exercices couvrent
        // integralement les notions cochees depuis la premiere, meme deja
        // maitrisees.
        if (mode == ModeTutorat.EXERCICE) {
            Notion premiereNotion = notionsOrdonnees.get(0);
            SeanceTutorat seanceTutorat = seanceTutoratRepository.save(
                    new SeanceTutorat(seanceId, utilisateurId, premiereNotion.getId(), mode)
            );
            return demarrerPremierTour(seanceTutorat, premiereNotion, mode, null);
        }

        // Mode EXPLICATION : reprend a la premiere notion pas encore
        // maitrisee (permet de reprendre une seance interrompue sans
        // re-expliquer ce qui l'est deja).
        Optional<UUID> premiereNotionId = choisirProchaineNotion(notionsOrdonnees, utilisateurId);
        if (premiereNotionId.isEmpty()) {
            // Toutes deja maitrisees avant meme de commencer : enchaine
            // directement sur le mode exercices plutot que de terminer.
            Notion premiereNotionExercice = notionsOrdonnees.get(0);
            SeanceTutorat seanceTutorat = seanceTutoratRepository.save(
                    new SeanceTutorat(seanceId, utilisateurId, premiereNotionExercice.getId(), ModeTutorat.EXERCICE)
            );
            return demarrerPremierTour(seanceTutorat, premiereNotionExercice, ModeTutorat.EXERCICE, ANNONCE_TRANSITION_EXERCICE_DEPART);
        }

        SeanceTutorat seanceTutorat = seanceTutoratRepository.save(
                new SeanceTutorat(seanceId, utilisateurId, premiereNotionId.get(), mode)
        );
        Notion notion = notionService.obtenirNotion(premiereNotionId.get());
        return demarrerPremierTour(seanceTutorat, notion, mode, null);
    }

    @Transactional
    public ResultatTour soumettreReponse(UUID seanceTutoratId, byte[] audioReponse, UUID utilisateurId) {
        SeanceTutorat seanceTutorat = obtenirEtVerifierProprietaire(seanceTutoratId, utilisateurId);
        verifierEnCours(seanceTutorat);
        return traiterReponse(seanceTutorat, transcrire(audioReponse), utilisateurId);
    }

    // Alternative a la reponse vocale : meme traitement de bout en bout,
    // simplement sans passer par la transcription -- l'etudiant peut
    // repondre au clavier plutot qu'au micro.
    @Transactional
    public ResultatTour soumettreReponseTexte(UUID seanceTutoratId, String texteEtudiant, UUID utilisateurId) {
        SeanceTutorat seanceTutorat = obtenirEtVerifierProprietaire(seanceTutoratId, utilisateurId);
        verifierEnCours(seanceTutorat);
        return traiterReponse(seanceTutorat, texteEtudiant, utilisateurId);
    }

    private void verifierEnCours(SeanceTutorat seanceTutorat) {
        if (seanceTutorat.getStatut() != StatutSeanceTutorat.EN_COURS) {
            throw new SeanceTutoratNonActiveException(seanceTutorat.getId());
        }
    }

    private ResultatTour traiterReponse(SeanceTutorat seanceTutorat, String texteEtudiant, UUID utilisateurId) {
        if (seanceTutorat.getMode() == ModeTutorat.LIBRE) {
            return traiterReponseLibre(seanceTutorat, texteEtudiant);
        }
        return traiterReponseStructuree(seanceTutorat, texteEtudiant, utilisateurId);
    }

    private ResultatTour traiterReponseStructuree(SeanceTutorat seanceTutorat, String texteEtudiant, UUID utilisateurId) {
        UUID seanceTutoratId = seanceTutorat.getId();
        UUID notionCouranteId = seanceTutorat.getNotionCouranteId();
        Notion notionCourante = notionService.obtenirNotion(notionCouranteId);
        ModeTutorat mode = seanceTutorat.getMode();

        List<TourDialogueTutorat> toursNotion = tourDialogueTutoratRepository
                .findBySeanceTutoratIdAndNotionIdOrderByDateCreationAsc(seanceTutoratId, notionCouranteId);
        List<GenerateurTourTuteurPort.TourHistorique> historique = toursNotion.stream()
                .map(tour -> new GenerateurTourTuteurPort.TourHistorique(tour.getLocuteur(), tour.getTexte()))
                .toList();

        tourDialogueTutoratRepository.save(new TourDialogueTutorat(seanceTutoratId, notionCouranteId, Locuteur.ETUDIANT, texteEtudiant, mode));

        // EXPLICATION : c'est le LLM qui decide qu'une notion est comprise
        // (doitAvancer connu seulement apres l'appel au generateur, voir plus
        // bas). EXERCICE : critere de fin propre, independant de cette
        // evaluation -- NOMBRE_EXERCICES_PAR_NOTION exercices poses sur cette
        // notion (le tour courant, pas encore persiste, compte pour un de
        // plus) -- calculable AVANT l'appel au generateur, ce qui permet de
        // prevenir le modele que ce tour est le dernier exercice sur cette
        // notion (voir ContexteTour.dernierExercice) pour qu'il ne propose
        // pas un exercice suivant que l'etudiant n'aura jamais l'occasion de
        // faire. Filtre explicitement par TourDialogueTutorat.mode ==
        // EXERCICE (pas seulement par notion) : une notion peut avoir ete
        // visitee une premiere fois en EXPLICATION avant la bascule
        // automatique, ses tours de l'epoque ne doivent pas compter comme
        // des exercices.
        //
        // PAS de "+1" ici (contrairement a une premiere version) : ce tour
        // en cours EVALUE la reponse au Nieme exercice deja pose, il n'en
        // pose pas lui-meme un nouveau tant qu'on n'a pas atteint le compte.
        // Avec "+1", seuls NOMBRE_EXERCICES_PAR_NOTION-1 exercices etaient
        // reellement poses avant la fin (verifie en conditions reelles) :
        // le tour d'ouverture (transition ou demarrage direct) pose deja le
        // premier exercice et compte pour 1, donc la comparaison sans "+1"
        // suffit a garantir exactement NOMBRE_EXERCICES_PAR_NOTION exercices
        // poses avant le dernier tour (evaluation sans nouvel exercice).
        boolean dernierExerciceDeCetteNotion = mode == ModeTutorat.EXERCICE
                && compterToursTuteurExercice(toursNotion) >= NOMBRE_EXERCICES_PAR_NOTION;

        var contexte = new GenerateurTourTuteurPort.ContexteTour(
                notionCourante.getTerme(), notionCourante.getDefinition(), historique, texteEtudiant, mode, dernierExerciceDeCetteNotion
        );
        GenerateurTourTuteurPort.TourTuteurGenere genere = appellerGenerateur(contexte);

        notionService.mettreAJourMaitrise(notionCouranteId, utilisateurId, genere.evaluationMaitrise());

        String texteAEnvoyer = genere.texteTuteur();
        UUID notionPourTour = notionCouranteId;
        boolean seanceTerminee = false;

        boolean doitAvancer = mode == ModeTutorat.EXERCICE ? dernierExerciceDeCetteNotion : genere.notionMaitrisee();

        if (doitAvancer) {
            List<Notion> notionsOrdonnees = seanceService.listerNotionsDeSeance(seanceTutorat.getSeanceId());
            Optional<UUID> prochaineNotionId = mode == ModeTutorat.EXERCICE
                    ? notionSuivanteParOrdre(notionsOrdonnees, notionCouranteId)
                    : choisirProchaineNotion(notionsOrdonnees, utilisateurId);

            if (prochaineNotionId.isPresent()) {
                notionPourTour = prochaineNotionId.get();
                seanceTutorat.avancerNotion(notionPourTour);
                Notion nouvelleNotion = notionService.obtenirNotion(notionPourTour);
                GenerateurTourTuteurPort.TourTuteurGenere ouverture = genererOuverture(nouvelleNotion, mode);
                texteAEnvoyer = genere.texteTuteur() + " " + ouverture.texteTuteur();
            } else if (mode == ModeTutorat.EXPLICATION) {
                // Toutes les notions maitrisees : bascule automatique vers
                // le mode exercices, sur les memes notions depuis le debut
                // (pas seulement celles restantes) -- voir contexte du plan.
                Notion premiereNotionExercice = notionsOrdonnees.get(0);
                seanceTutorat.passerEnModeExercice();
                seanceTutorat.avancerNotion(premiereNotionExercice.getId());
                GenerateurTourTuteurPort.TourTuteurGenere ouvertureExercice =
                        genererOuverture(premiereNotionExercice, ModeTutorat.EXERCICE);
                texteAEnvoyer = genere.texteTuteur() + " " + ANNONCE_TRANSITION_EXERCICE + " " + ouvertureExercice.texteTuteur();
                notionPourTour = premiereNotionExercice.getId();
            } else {
                seanceTutorat.terminer();
                seanceTerminee = true;
            }
        }

        // seanceTutorat.getMode() (pas la variable locale `mode`, capturee
        // avant une eventuelle bascule ci-dessus) : ce tour doit porter le
        // mode qui s'applique desormais, notamment quand cette meme
        // sauvegarde correspond a l'ouverture du premier exercice juste
        // apres la bascule automatique.
        TourDialogueTutorat tourTuteur = tourDialogueTutoratRepository.save(
                new TourDialogueTutorat(seanceTutoratId, notionPourTour, Locuteur.TUTEUR, texteAEnvoyer, seanceTutorat.getMode())
        );
        seanceTutoratRepository.save(seanceTutorat);

        return new ResultatTour(seanceTutoratId, tourTuteur.getId(), texteAEnvoyer, seanceTutorat.getNotionCouranteId(),
                genere.evaluationMaitrise(), seanceTerminee);
    }

    // Pas de notion a evaluer : historique complet (pas filtre par notion,
    // toutes les autres requetes de ce service le sont), aucun appel a
    // mettreAJourMaitrise, jamais de fin automatique -- voir
    // docs/phases/phase-19-mode-conversation-libre.md.
    private ResultatTour traiterReponseLibre(SeanceTutorat seanceTutorat, String texteEtudiant) {
        UUID seanceTutoratId = seanceTutorat.getId();

        List<GenerateurTourTuteurPort.TourHistorique> historique = tourDialogueTutoratRepository
                .findBySeanceTutoratIdOrderByDateCreationAsc(seanceTutoratId).stream()
                .map(tour -> new GenerateurTourTuteurPort.TourHistorique(tour.getLocuteur(), tour.getTexte()))
                .toList();

        tourDialogueTutoratRepository.save(new TourDialogueTutorat(seanceTutoratId, null, Locuteur.ETUDIANT, texteEtudiant, ModeTutorat.LIBRE));

        Seance seance = seanceService.obtenirSeance(seanceTutorat.getSeanceId());
        Matiere matiere = matiereService.obtenirMatiere(seance.getMatiereId());

        var contexte = new GenerateurTourTuteurPort.ContexteTour(
                matiere.getNom(), construireContexteMatiere(matiere.getId(), seanceTutorat.getUtilisateurId()), historique, texteEtudiant, ModeTutorat.LIBRE, false
        );
        GenerateurTourTuteurPort.TourTuteurGenere genere = appellerGenerateur(contexte);

        TourDialogueTutorat tourTuteur = tourDialogueTutoratRepository.save(
                new TourDialogueTutorat(seanceTutoratId, null, Locuteur.TUTEUR, genere.texteTuteur(), ModeTutorat.LIBRE)
        );

        return new ResultatTour(seanceTutoratId, tourTuteur.getId(), genere.texteTuteur(), null, null, false);
    }

    // Point d'entree "Tutorat" direct depuis le menu de navigation (pas besoin
    // de creer/choisir une seance au prealable) : delegue entierement a
    // demarrerTutorat (idempotence, reprise en cours, etc. deja geres) apres
    // avoir resolu/cree la seance partagee "Discussion libre" de la matiere.
    @Transactional
    public ResultatTour demarrerTutoratLibrePourMatiere(UUID matiereId, UUID utilisateurId) {
        Seance seance = seanceService.obtenirOuCreerSeanceDiscussionLibre(matiereId, utilisateurId);
        return demarrerTutorat(seance.getId(), utilisateurId, ModeTutorat.LIBRE);
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

    // Aucune notion rattachee a la seance : rien a faire, la seance se
    // termine immediatement (comme aujourd'hui). Distinct du cas "toutes
    // deja maitrisees" (celui-ci enchaine sur le mode exercices, voir
    // demarrerTutorat).
    private ResultatTour terminerSansNotion(UUID seanceId, UUID utilisateurId, ModeTutorat mode) {
        SeanceTutorat seanceTutorat = seanceTutoratRepository.save(new SeanceTutorat(seanceId, utilisateurId, null, mode));
        seanceTutorat.terminer();
        seanceTutoratRepository.save(seanceTutorat);
        return new ResultatTour(seanceTutorat.getId(), null, "Aucune notion associee a cette seance.", null, null, true);
    }

    private ResultatTour demarrerPremierTour(SeanceTutorat seanceTutorat, Notion notion, ModeTutorat mode, String prefixeAnnonce) {
        GenerateurTourTuteurPort.TourTuteurGenere ouverture = genererOuverture(notion, mode);
        String texte = prefixeAnnonce == null ? ouverture.texteTuteur() : prefixeAnnonce + " " + ouverture.texteTuteur();
        TourDialogueTutorat tour = tourDialogueTutoratRepository.save(
                new TourDialogueTutorat(seanceTutorat.getId(), notion.getId(), Locuteur.TUTEUR, texte, mode)
        );
        return new ResultatTour(seanceTutorat.getId(), tour.getId(), texte, notion.getId(), NiveauMaitrise.NON_ABORDEE, false);
    }

    private GenerateurTourTuteurPort.TourTuteurGenere genererOuverture(Notion notion, ModeTutorat mode) {
        var contexte = new GenerateurTourTuteurPort.ContexteTour(notion.getTerme(), notion.getDefinition(), List.of(), null, mode, false);
        return appellerGenerateur(contexte);
    }

    // Notions validees de la matiere (phase 18 : fiches -> candidats ->
    // validation enseignant, seules des notions humainement confirmees
    // alimentent le tuteur -- jamais le texte brut d'un document, coherent
    // avec la doctrine de tracabilite/traabilite du projet). Se degrade vers
    // le texte generique si la matiere n'a encore aucune notion.
    //
    // Phase 22c : elargit deliberement au contenu agrege de la matiere
    // (resumes de cours + documents, voir AgregateurContenuMatiereService) en
    // plus des notions validees -- accepte en connaissance de cause que du
    // contenu non filtre par un enseignant nourrisse aussi le tuteur, pour
    // une revision progressive sur l'ensemble de la matiere plutot que
    // seulement les notions explicitement validees. Voir
    // docs/phases/phase-22-tutorat-progressif.md.
    private String construireContexteMatiere(UUID matiereId, UUID utilisateurId) {
        List<Notion> notions = notionService.listerNotionsParMatiere(matiereId);
        StringBuilder contexte = new StringBuilder(DEFINITION_MODE_LIBRE);

        if (!notions.isEmpty()) {
            String connaissances = notions.stream()
                    .map(notion -> "- " + notion.getTerme() + " : " + notion.getDefinition())
                    .collect(Collectors.joining("\n"));
            contexte.append("\n\nNotions au programme de cette matiere (appuie-toi dessus si pertinent) :\n").append(connaissances);
        }

        String contenuAgrege = agregateurContenuMatiereService.agregerContenu(matiereId);
        if (!contenuAgrege.isBlank()) {
            contexte.append("\n\nContenu des cours et documents de cette matiere (resumes deja produits, appuie-toi dessus pour construire une revision progressive) :\n")
                    .append(contenuAgrege);
        }

        // Phase 22e : travaux papier soumis par CET etudiant uniquement (pas
        // ceux des autres) -- personnel, pas du contenu de cours a partager
        // avec toute la classe, contrairement au reste du contexte agrege.
        // Phase 24 : inclut aussi la correction deja generee (pas seulement le
        // texte brut) -- le tuteur doit pouvoir discuter de CE QUI A ETE
        // CORRIGE, pas juste retranscrire ce que l'etudiant a ecrit.
        // Phase 28 : chaque travail est decoupe en exercices individuels
        // (enonce reel + reponse + correction), plus fiable qu'un seul bloc
        // de texte reconstitue.
        String travauxPapier = travailPapierMatiereRepository
                .findByMatiereIdAndUtilisateurIdOrderByDateCreationDesc(matiereId, utilisateurId).stream()
                .filter(travail -> travail.getStatut() == StatutDocument.REUSSI)
                .map(travail -> {
                    List<ExercicePapier> exercices = exercicePapierRepository.findByTravailPapierIdOrderByOrdreAsc(travail.getId());
                    if (exercices.isEmpty()) {
                        return "";
                    }
                    return exercices.stream()
                            .map(exercice -> {
                                String bloc = "Enonce : " + exercice.getEnonce() + "\nReponse de l'etudiant : " + exercice.getReponseEtudiant();
                                if (exercice.getCorrectionSynthese() != null && !exercice.getCorrectionSynthese().isBlank()) {
                                    String pointsTexte = exercice.getPointsCorrection().stream()
                                            .map(point -> "- " + point.getSujet() + " : " + point.getConstat() + " " + point.getCorrectionAttendue())
                                            .collect(Collectors.joining("\n"));
                                    bloc += "\nCorrection deja donnee a l'etudiant (niveau " + exercice.getCorrectionNiveau() + ") : "
                                            + exercice.getCorrectionSynthese() + "\n" + pointsTexte;
                                }
                                return bloc;
                            })
                            .collect(Collectors.joining("\n\n"));
                })
                .filter(bloc -> !bloc.isBlank())
                .collect(Collectors.joining("\n\n"));
        if (!travauxPapier.isBlank()) {
            contexte.append("\n\nTravaux papier que cet etudiant a soumis, avec leur correction (il peut vouloir en discuter) :\n").append(travauxPapier);
        }

        return contexte.toString();
    }

    // Mode EXPLICATION uniquement : premiere notion pas encore maitrisee,
    // dans l'ordre de la seance.
    private Optional<UUID> choisirProchaineNotion(List<Notion> notionsOrdonnees, UUID utilisateurId) {
        return notionsOrdonnees.stream()
                .filter(notion -> notionService.obtenirNiveauMaitrise(notion.getId(), utilisateurId) != NiveauMaitrise.MAITRISEE)
                .map(Notion::getId)
                .findFirst();
    }

    // Mode EXERCICE uniquement : notion suivante par simple ordre de liste,
    // sans filtre de maitrise -- toutes les notions cochees sont exercees,
    // meme celles deja maitrisees.
    private Optional<UUID> notionSuivanteParOrdre(List<Notion> notionsOrdonnees, UUID notionCouranteId) {
        for (int i = 0; i < notionsOrdonnees.size(); i++) {
            if (notionsOrdonnees.get(i).getId().equals(notionCouranteId)) {
                return i + 1 < notionsOrdonnees.size() ? Optional.of(notionsOrdonnees.get(i + 1).getId()) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    // Filtre par TourDialogueTutorat.mode == EXERCICE (pas seulement par
    // notion) : voir le commentaire dans traiterReponseStructuree, une
    // notion peut avoir des tours d'une precedente phase EXPLICATION.
    private long compterToursTuteurExercice(List<TourDialogueTutorat> toursNotion) {
        return toursNotion.stream()
                .filter(tour -> tour.getLocuteur() == Locuteur.TUTEUR && tour.getMode() == ModeTutorat.EXERCICE)
                .count();
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

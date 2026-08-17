package com.memoria.ecole.notion;

import com.memoria.core.couloir.CouloirService;
import com.memoria.ecole.matiere.Matiere;
import com.memoria.ecole.matiere.MatiereService;
import com.memoria.ecole.seance.SeanceNotionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotionService {

    private final NotionRepository notionRepository;
    private final MaitriseNotionRepository maitriseNotionRepository;
    private final SeanceNotionRepository seanceNotionRepository;
    private final MatiereService matiereService;
    private final CouloirService couloirService;

    public NotionService(
            NotionRepository notionRepository,
            MaitriseNotionRepository maitriseNotionRepository,
            SeanceNotionRepository seanceNotionRepository,
            MatiereService matiereService,
            CouloirService couloirService
    ) {
        this.notionRepository = notionRepository;
        this.maitriseNotionRepository = maitriseNotionRepository;
        this.seanceNotionRepository = seanceNotionRepository;
        this.matiereService = matiereService;
        this.couloirService = couloirService;
    }

    public Notion creerNotion(UUID matiereId, String terme, String definition, int ordre, UUID utilisateurId) {
        Matiere matiere = matiereService.obtenirMatiere(matiereId);
        couloirService.verifierMembre(matiere.getCouloirId(), utilisateurId);
        return notionRepository.save(new Notion(matiereId, terme, definition, ordre));
    }

    // Chemin de creation issu de la validation d'une NotionCandidate (phase
    // 18) : meme verification de membership que creerNotion, avec en plus la
    // tracabilite vers le DocumentMatiere source.
    public Notion creerNotionValidee(
            UUID matiereId, String terme, String definition, int ordre, UUID documentSourceId, UUID utilisateurId
    ) {
        Matiere matiere = matiereService.obtenirMatiere(matiereId);
        couloirService.verifierMembre(matiere.getCouloirId(), utilisateurId);
        return notionRepository.save(new Notion(matiereId, terme, definition, ordre, documentSourceId));
    }

    public Notion obtenirNotion(UUID id) {
        return notionRepository.findById(id)
                .orElseThrow(() -> new NotionNotFoundException(id));
    }

    public List<Notion> listerNotionsParMatiere(UUID matiereId) {
        return notionRepository.findByMatiereIdOrderByOrdreAsc(matiereId);
    }

    // Deux suppressions derivees (maitrises, rattachements aux seances) avant
    // la notion elle-meme -- meme raison que CouloirService.supprimerCouloir
    // (jointures plates sans contrainte FK, donc a nettoyer explicitement pour
    // eviter des lignes orphelines qui feraient echouer listerNotionsDeSeance
    // avec un NotionNotFoundException).
    @Transactional
    public void supprimerNotion(UUID notionId, UUID utilisateurId) {
        Notion notion = obtenirNotion(notionId);
        Matiere matiere = matiereService.obtenirMatiere(notion.getMatiereId());
        couloirService.verifierMembre(matiere.getCouloirId(), utilisateurId);
        maitriseNotionRepository.deleteByNotionId(notionId);
        seanceNotionRepository.deleteByNotionId(notionId);
        notionRepository.deleteById(notionId);
    }

    public NiveauMaitrise obtenirNiveauMaitrise(UUID notionId, UUID utilisateurId) {
        return maitriseNotionRepository.findByNotionIdAndUtilisateurId(notionId, utilisateurId)
                .map(MaitriseNotion::getNiveau)
                .orElse(NiveauMaitrise.NON_ABORDEE);
    }

    public Map<UUID, NiveauMaitrise> obtenirMaitrises(List<UUID> notionIds, UUID utilisateurId) {
        Map<UUID, NiveauMaitrise> resultat = new HashMap<>();
        notionIds.forEach(id -> resultat.put(id, NiveauMaitrise.NON_ABORDEE));
        maitriseNotionRepository.findByUtilisateurIdAndNotionIdIn(utilisateurId, notionIds)
                .forEach(maitrise -> resultat.put(maitrise.getNotionId(), maitrise.getNiveau()));
        return resultat;
    }

    // Cree la ligne de suivi au premier tour concernant cette notion pour cet
    // etudiant (comme rejoindreCouloir cree la ligne MembreCouloir au besoin) :
    // pas de creation explicite en amont necessaire cote appelant.
    public MaitriseNotion mettreAJourMaitrise(UUID notionId, UUID utilisateurId, NiveauMaitrise niveau) {
        MaitriseNotion maitrise = maitriseNotionRepository.findByNotionIdAndUtilisateurId(notionId, utilisateurId)
                .orElseGet(() -> new MaitriseNotion(notionId, utilisateurId));
        maitrise.mettreAJour(niveau);
        return maitriseNotionRepository.save(maitrise);
    }
}

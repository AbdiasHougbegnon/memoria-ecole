package com.memoria.ecole.seance;

import com.memoria.core.couloir.Couloir;
import com.memoria.core.couloir.CouloirService;
import com.memoria.core.couloir.PasProprietaireDuCouloirException;
import com.memoria.ecole.matiere.Matiere;
import com.memoria.ecole.matiere.MatiereService;
import com.memoria.ecole.notion.Notion;
import com.memoria.ecole.notion.NotionService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SeanceService {

    private final SeanceRepository seanceRepository;
    private final SeanceNotionRepository seanceNotionRepository;
    private final MatiereService matiereService;
    private final NotionService notionService;
    private final CouloirService couloirService;

    public SeanceService(
            SeanceRepository seanceRepository,
            SeanceNotionRepository seanceNotionRepository,
            MatiereService matiereService,
            NotionService notionService,
            CouloirService couloirService
    ) {
        this.seanceRepository = seanceRepository;
        this.seanceNotionRepository = seanceNotionRepository;
        this.matiereService = matiereService;
        this.notionService = notionService;
        this.couloirService = couloirService;
    }

    public Seance creerSeance(String titre, UUID matiereId, UUID utilisateurId) {
        Matiere matiere = matiereService.obtenirMatiere(matiereId);
        verifierProprietaireDuCouloir(matiere.getCouloirId(), utilisateurId);
        return seanceRepository.save(new Seance(titre, matiereId, matiere.getCouloirId()));
    }

    public Seance obtenirSeance(UUID id) {
        return seanceRepository.findById(id)
                .orElseThrow(() -> new SeanceNotFoundException(id));
    }

    public List<Seance> listerSeancesParMatiere(UUID matiereId) {
        return seanceRepository.findByMatiereId(matiereId);
    }

    // Remplace entierement l'ensemble des notions rattachees (supprime puis
    // recree), l'ordre etant celui de la liste recue -- plus simple qu'un
    // diff incremental pour ce premier increment. @Transactional necessaire :
    // deleteBySeanceId est une requete derivee (pas heritee de JpaRepository),
    // meme raison que CouloirService.supprimerCouloir.
    @Transactional
    public void rattacherNotions(UUID seanceId, List<UUID> notionIds, UUID utilisateurId) {
        Seance seance = obtenirSeance(seanceId);
        verifierProprietaireDuCouloir(seance.getCouloirId(), utilisateurId);
        seanceNotionRepository.deleteBySeanceId(seanceId);
        for (int i = 0; i < notionIds.size(); i++) {
            seanceNotionRepository.save(new SeanceNotion(seanceId, notionIds.get(i), i));
        }
    }

    public List<Notion> listerNotionsDeSeance(UUID seanceId) {
        return seanceNotionRepository.findBySeanceIdOrderByOrdreAsc(seanceId).stream()
                .map(seanceNotion -> notionService.obtenirNotion(seanceNotion.getNotionId()))
                .toList();
    }

    private void verifierProprietaireDuCouloir(UUID couloirId, UUID utilisateurId) {
        Couloir couloir = couloirService.obtenirCouloir(couloirId);
        if (!couloir.getProprietaireId().equals(utilisateurId)) {
            throw new PasProprietaireDuCouloirException(couloirId, utilisateurId);
        }
    }
}

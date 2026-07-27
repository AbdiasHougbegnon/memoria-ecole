package com.memoria.ecole.notion;

import com.memoria.core.couloir.CouloirService;
import com.memoria.ecole.matiere.Matiere;
import com.memoria.ecole.matiere.MatiereService;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// Etape de validation humaine obligatoire entre l'extraction IA (phase 18,
// GenerateurNotionsDepuisDocumentPort) et le suivi de maitrise reel des
// etudiants -- decision actee avec l'utilisateur : une extraction IA peut
// halluciner, pas d'injection directe en base sans filtre enseignant.
@Service
public class NotionCandidateService {

    private final NotionCandidateRepository notionCandidateRepository;
    private final NotionRepository notionRepository;
    private final NotionService notionService;
    private final MatiereService matiereService;
    private final CouloirService couloirService;

    public NotionCandidateService(
            NotionCandidateRepository notionCandidateRepository,
            NotionRepository notionRepository,
            NotionService notionService,
            MatiereService matiereService,
            CouloirService couloirService
    ) {
        this.notionCandidateRepository = notionCandidateRepository;
        this.notionRepository = notionRepository;
        this.notionService = notionService;
        this.matiereService = matiereService;
        this.couloirService = couloirService;
    }

    public List<NotionCandidate> listerCandidates(UUID matiereId) {
        matiereService.obtenirMatiere(matiereId);
        return notionCandidateRepository.findByMatiereIdOrderByDateCreationAsc(matiereId);
    }

    // La verification de propriete est deleguee entierement a
    // NotionService.creerNotionValidee (pas de duplication ici) : si
    // l'utilisateur n'est pas proprietaire, la creation de la Notion echoue
    // avant toute mutation de la candidate.
    public Notion validerCandidate(UUID candidateId, String termeEdite, String definitionEditee, UUID utilisateurId) {
        NotionCandidate candidate = obtenirCandidate(candidateId);
        int ordre = notionRepository.findByMatiereIdOrderByOrdreAsc(candidate.getMatiereId()).size();

        Notion notion = notionService.creerNotionValidee(
                candidate.getMatiereId(), termeEdite, definitionEditee, ordre, candidate.getDocumentMatiereId(), utilisateurId
        );

        candidate.marquerValidee();
        notionCandidateRepository.save(candidate);
        return notion;
    }

    public NotionCandidate rejeterCandidate(UUID candidateId, UUID utilisateurId) {
        NotionCandidate candidate = obtenirCandidate(candidateId);
        Matiere matiere = matiereService.obtenirMatiere(candidate.getMatiereId());
        couloirService.verifierProprietaireDuCouloir(matiere.getCouloirId(), utilisateurId);

        candidate.marquerRejetee();
        return notionCandidateRepository.save(candidate);
    }

    private NotionCandidate obtenirCandidate(UUID id) {
        return notionCandidateRepository.findById(id)
                .orElseThrow(() -> new NotionCandidateNotFoundException(id));
    }
}

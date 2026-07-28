package com.memoria.ecole.matiere;

import com.memoria.core.document.StatutDocument;
import com.memoria.ecole.document.DocumentMatiere;
import com.memoria.ecole.document.DocumentMatiereRepository;
import com.memoria.ecole.resumecours.NotionCours;
import com.memoria.ecole.resumecours.ResumeCours;
import com.memoria.ecole.resumecours.ResumeCoursRepository;
import com.memoria.ecole.resumecours.StatutResumeCours;
import com.memoria.ecole.session.ContexteScolaireSession;
import com.memoria.ecole.session.ContexteScolaireSessionRepository;

import org.springframework.stereotype.Service;

import java.util.UUID;

// Phase 22c : agrege tout le contenu pedagogique deja produit pour une
// matiere -- resumes de cours REUSSI des sessions qui lui sont rattachees, et
// texte extrait des documents REUSSI -- pour nourrir le QCM de matiere
// (QcmMatiereService) et le tuteur en mode libre (TuteurVocalService).
// Elargit deliberement la phase 19 (notions validees uniquement) : accepte en
// connaissance de cause que du contenu non filtre par un enseignant (resume
// IA, texte de document brut) alimente desormais aussi le tuteur/QCM de
// matiere -- voir docs/phases/phase-22-tutorat-progressif.md.
@Service
public class AgregateurContenuMatiereService {

    private final ContexteScolaireSessionRepository contexteScolaireSessionRepository;
    private final ResumeCoursRepository resumeCoursRepository;
    private final DocumentMatiereRepository documentMatiereRepository;

    public AgregateurContenuMatiereService(
            ContexteScolaireSessionRepository contexteScolaireSessionRepository,
            ResumeCoursRepository resumeCoursRepository,
            DocumentMatiereRepository documentMatiereRepository
    ) {
        this.contexteScolaireSessionRepository = contexteScolaireSessionRepository;
        this.resumeCoursRepository = resumeCoursRepository;
        this.documentMatiereRepository = documentMatiereRepository;
    }

    public String agregerContenu(UUID matiereId) {
        StringBuilder texte = new StringBuilder();

        for (ContexteScolaireSession contexte : contexteScolaireSessionRepository.findByMatiereId(matiereId)) {
            resumeCoursRepository.findBySessionId(contexte.getSessionId())
                    .filter(rc -> rc.getStatut() == StatutResumeCours.REUSSI)
                    .ifPresent(rc -> ajouterResumeCours(texte, rc));
        }

        for (DocumentMatiere document : documentMatiereRepository.findByMatiereIdOrderByDateCreationAsc(matiereId)) {
            if (document.getStatut() == StatutDocument.REUSSI && document.getTexteExtrait() != null) {
                texte.append(document.getTexteExtrait()).append("\n\n");
            }
        }

        return texte.toString();
    }

    private void ajouterResumeCours(StringBuilder texte, ResumeCours resumeCours) {
        if (resumeCours.getSynthese() != null) {
            texte.append(resumeCours.getSynthese()).append("\n");
        }
        for (NotionCours notion : resumeCours.getNotions()) {
            texte.append("- ").append(notion.getTerme()).append(" : ").append(notion.getDefinition()).append("\n");
        }
        texte.append("\n");
    }
}

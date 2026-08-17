package com.memoria.ecole.matiere;

import com.memoria.core.document.StatutDocument;
import com.memoria.core.document.TypeDocument;
import com.memoria.ecole.document.DocumentMatiere;
import com.memoria.ecole.document.DocumentMatiereRepository;
import com.memoria.ecole.resumecours.NotionCours;
import com.memoria.ecole.resumecours.ResumeCours;
import com.memoria.ecole.resumecours.ResumeCoursRepository;
import com.memoria.ecole.resumecours.StatutResumeCours;
import com.memoria.ecole.session.ContexteScolaireSession;
import com.memoria.ecole.session.ContexteScolaireSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgregateurContenuMatiereServiceTest {

    @Mock private ContexteScolaireSessionRepository contexteScolaireSessionRepository;
    @Mock private ResumeCoursRepository resumeCoursRepository;
    @Mock private DocumentMatiereRepository documentMatiereRepository;

    private AgregateurContenuMatiereService service;

    @BeforeEach
    void setUp() {
        service = new AgregateurContenuMatiereService(
                contexteScolaireSessionRepository, resumeCoursRepository, documentMatiereRepository
        );
    }

    @Test
    void agregerContenu_inclut_les_resumes_reussis_des_sessions_rattachees() {
        UUID matiereId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ContexteScolaireSession contexte = new ContexteScolaireSession(sessionId, matiereId);
        ResumeCours resume = new ResumeCours(
                sessionId, "Synthese du cours sur les listes.",
                List.of(new NotionCours("Liste chainee", "Structure lineaire")),
                List.of(), List.of(0), StatutResumeCours.REUSSI
        );
        when(contexteScolaireSessionRepository.findByMatiereId(matiereId)).thenReturn(List.of(contexte));
        when(resumeCoursRepository.findBySessionId(sessionId)).thenReturn(Optional.of(resume));
        when(documentMatiereRepository.findByMatiereIdOrderByDateCreationAsc(matiereId)).thenReturn(List.of());

        String contenu = service.agregerContenu(matiereId);

        assertThat(contenu).contains("Synthese du cours sur les listes.");
        assertThat(contenu).contains("Liste chainee : Structure lineaire");
    }

    @Test
    void agregerContenu_ignore_les_resumes_en_echec() {
        UUID matiereId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ContexteScolaireSession contexte = new ContexteScolaireSession(sessionId, matiereId);
        ResumeCours resumeEchec = new ResumeCours(sessionId, null, List.of(), List.of(), List.of(), StatutResumeCours.ECHEC);
        when(contexteScolaireSessionRepository.findByMatiereId(matiereId)).thenReturn(List.of(contexte));
        when(resumeCoursRepository.findBySessionId(sessionId)).thenReturn(Optional.of(resumeEchec));
        when(documentMatiereRepository.findByMatiereIdOrderByDateCreationAsc(matiereId)).thenReturn(List.of());

        String contenu = service.agregerContenu(matiereId);

        assertThat(contenu).isBlank();
    }

    @Test
    void agregerContenu_inclut_le_texte_extrait_des_documents_reussis() {
        UUID matiereId = UUID.randomUUID();
        DocumentMatiere document = new DocumentMatiere(matiereId, TypeDocument.PDF, "cours.pdf", "chemin/cours.pdf", 4096);
        document.marquerReussi("Contenu extrait du PDF sur les piles.");
        when(contexteScolaireSessionRepository.findByMatiereId(matiereId)).thenReturn(List.of());
        when(documentMatiereRepository.findByMatiereIdOrderByDateCreationAsc(matiereId)).thenReturn(List.of(document));

        String contenu = service.agregerContenu(matiereId);

        assertThat(contenu).contains("Contenu extrait du PDF sur les piles.");
    }

    @Test
    void agregerContenu_ignore_les_documents_en_echec() {
        UUID matiereId = UUID.randomUUID();
        DocumentMatiere document = new DocumentMatiere(matiereId, TypeDocument.PDF, "cours.pdf", "chemin/cours.pdf", 4096);
        document.marquerEchec();
        when(contexteScolaireSessionRepository.findByMatiereId(matiereId)).thenReturn(List.of());
        when(documentMatiereRepository.findByMatiereIdOrderByDateCreationAsc(matiereId)).thenReturn(List.of(document));

        String contenu = service.agregerContenu(matiereId);

        assertThat(contenu).isBlank();
    }
}

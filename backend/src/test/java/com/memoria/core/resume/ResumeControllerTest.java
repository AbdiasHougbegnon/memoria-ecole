package com.memoria.core.resume;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResumeController.class)
class ResumeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ResumeService resumeService;

    @Test
    void obtenirResume_retourne_204_quand_aucun_resume_nexiste() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(resumeService.obtenirResume(sessionId, ResumeType.DETAILLE)).thenThrow(new ResumeNotFoundException(sessionId));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/resumes/{type}", sessionId, "DETAILLE"))
                .andExpect(status().isNoContent());
    }

    @Test
    void genererResume_retourne_le_resume_genere_pour_le_type_demande() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Resume resume = new Resume(sessionId, ResumeType.ACTIONS, "Contexte.", List.of("Faire X"), List.of(0), ResumeStatut.REUSSI);
        when(resumeService.obtenirOuGenererResume(sessionId, ResumeType.ACTIONS)).thenReturn(resume);

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/resumes/{type}", sessionId, "ACTIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ACTIONS"))
                .andExpect(jsonPath("$.pointsCles[0]").value("Faire X"));
    }
}

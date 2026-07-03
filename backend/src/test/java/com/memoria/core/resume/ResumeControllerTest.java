package com.memoria.core.resume;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        when(resumeService.obtenirResume(sessionId)).thenThrow(new ResumeNotFoundException(sessionId));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/resume", sessionId))
                .andExpect(status().isNoContent());
    }
}

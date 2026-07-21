package com.memoria.core.transcription;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/transcriptions")
public class TranscriptionController {

    private final TranscriptionService transcriptionService;

    public TranscriptionController(TranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    @GetMapping
    public List<TranscriptionResponse> obtenirTranscriptions(@PathVariable UUID sessionId) {
        return transcriptionService.obtenirTranscriptionsAvecIdentification(sessionId);
    }
}

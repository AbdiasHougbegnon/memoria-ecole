package com.memoria.core.audio;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/chunks")
public class AudioChunkController {

    private final AudioChunkService audioChunkService;

    public AudioChunkController(AudioChunkService audioChunkService) {
        this.audioChunkService = audioChunkService;
    }

    @PutMapping("/{numeroSequence}")
    public ResponseEntity<EnregistrerChunkResponse> enregistrerChunk(
            @PathVariable UUID sessionId,
            @PathVariable int numeroSequence,
            @RequestBody byte[] donnees
    ) {
        ResultatEnregistrementChunk resultat = audioChunkService.enregistrerChunk(sessionId, numeroSequence, donnees);
        HttpStatus statut = resultat.dejaRecu() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(statut).body(EnregistrerChunkResponse.depuis(resultat));
    }

    @GetMapping
    public List<Integer> listerNumerosRecus(@PathVariable UUID sessionId) {
        return audioChunkService.listerNumerosRecus(sessionId);
    }
}

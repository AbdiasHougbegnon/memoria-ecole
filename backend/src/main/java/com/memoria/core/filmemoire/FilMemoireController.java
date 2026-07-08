package com.memoria.core.filmemoire;

import com.memoria.core.session.SessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fils-memoire")
public class FilMemoireController {

    private final FilMemoireService filMemoireService;
    private final SessionService sessionService;

    public FilMemoireController(FilMemoireService filMemoireService, SessionService sessionService) {
        this.filMemoireService = filMemoireService;
        this.sessionService = sessionService;
    }

    @GetMapping
    public List<FilMemoireResponse> listerFils() {
        return filMemoireService.listerFils().stream()
                .map(fil -> new FilMemoireResponse(
                        fil.getId(),
                        fil.getNom(),
                        fil.getResumeCumulatif(),
                        fil.getSessionIds().stream()
                                .map(sessionService::obtenirSession)
                                .map(SessionSommaireResponse::depuis)
                                .toList(),
                        fil.getDateCreation(),
                        fil.getDateMiseAJour()
                ))
                .toList();
    }
}

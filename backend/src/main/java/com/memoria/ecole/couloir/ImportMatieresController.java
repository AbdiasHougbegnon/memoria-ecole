package com.memoria.ecole.couloir;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ecole/import-matieres")
public class ImportMatieresController {

    private final ImportMatieresService importMatieresService;

    public ImportMatieresController(ImportMatieresService importMatieresService) {
        this.importMatieresService = importMatieresService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RapportImportMatieres importer(
            @RequestParam("fichier") MultipartFile fichier,
            @AuthenticationPrincipal UUID utilisateurId
    ) throws IOException {
        return importMatieresService.importer(fichier.getBytes(), utilisateurId);
    }
}

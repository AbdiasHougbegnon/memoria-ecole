package com.memoria.entreprise.tableaudebord;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/entreprise/tableau-de-bord")
public class TableauDeBordEntrepriseController {

    private final TableauDeBordEntrepriseService tableauDeBordEntrepriseService;

    public TableauDeBordEntrepriseController(TableauDeBordEntrepriseService tableauDeBordEntrepriseService) {
        this.tableauDeBordEntrepriseService = tableauDeBordEntrepriseService;
    }

    @GetMapping
    public TableauDeBordEntrepriseResponse obtenir() {
        return tableauDeBordEntrepriseService.obtenirTableauDeBord();
    }
}

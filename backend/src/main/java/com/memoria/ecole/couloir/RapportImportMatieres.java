package com.memoria.ecole.couloir;

import java.util.List;

public record RapportImportMatieres(
        int couloirsCrees,
        int couloirsExistants,
        int matieresCreees,
        int matieresExistantes,
        List<ErreurImport> erreurs
) {
}

package com.memoria.ecole.qcm;

import java.util.UUID;

// Le QCM de matiere se genere a partir des resumes de cours et des documents
// deja disponibles pour la matiere (voir AgregateurContenuMatiereService) --
// il faut qu'au moins un des deux existe et ait reussi.
public class AucunContenuMatiereDisponibleException extends RuntimeException {

    public AucunContenuMatiereDisponibleException(UUID matiereId) {
        super("Aucun contenu disponible pour la matiere " + matiereId + " : rien pour generer un QCM");
    }
}

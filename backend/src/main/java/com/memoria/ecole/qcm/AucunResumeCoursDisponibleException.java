package com.memoria.ecole.qcm;

import java.util.UUID;

// Le QCM se genere a partir du resume de cours deja produit (synthese + notions),
// pas de la transcription brute -- cf. docs/phases/phase-15-qcm-revision.md. Il
// faut donc qu'un ResumeCours REUSSI existe avant de pouvoir generer un QCM.
public class AucunResumeCoursDisponibleException extends RuntimeException {

    public AucunResumeCoursDisponibleException(UUID sessionId) {
        super("Aucun resume de cours reussi pour la session " + sessionId + " : rien pour generer un QCM");
    }
}

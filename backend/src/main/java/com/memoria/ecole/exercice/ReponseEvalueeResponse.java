package com.memoria.ecole.exercice;

import com.memoria.ecole.notion.NiveauMaitrise;

public record ReponseEvalueeResponse(String reponse, NiveauMaitrise niveau, String retour) {

    public static ReponseEvalueeResponse depuis(ReponseEvaluee reponse) {
        return new ReponseEvalueeResponse(reponse.getReponse(), reponse.getNiveau(), reponse.getRetour());
    }
}

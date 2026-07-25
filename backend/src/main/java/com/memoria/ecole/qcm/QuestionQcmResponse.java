package com.memoria.ecole.qcm;

import java.util.List;

public record QuestionQcmResponse(String enonce, List<String> choix, int reponseCorrecte, String explication) {

    public static QuestionQcmResponse depuis(QuestionQcm question) {
        return new QuestionQcmResponse(
                question.getEnonce(),
                List.of(question.getChoixA(), question.getChoixB(), question.getChoixC(), question.getChoixD()),
                question.getIndexReponseCorrecte(),
                question.getExplication()
        );
    }
}

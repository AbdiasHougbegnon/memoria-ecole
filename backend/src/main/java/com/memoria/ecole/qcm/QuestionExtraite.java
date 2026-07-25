package com.memoria.ecole.qcm;

import java.util.List;

public record QuestionExtraite(String enonce, List<String> choix, int indexReponseCorrecte, String explication) {
}

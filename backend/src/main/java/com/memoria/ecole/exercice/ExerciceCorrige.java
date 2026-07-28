package com.memoria.ecole.exercice;

import com.memoria.ecole.notion.NiveauMaitrise;

import java.util.List;

public record ExerciceCorrige(
        String enonce, String reponseEtudiant, NiveauMaitrise niveau, String syntheseCorrection, List<PointCorrection> points
) {
}

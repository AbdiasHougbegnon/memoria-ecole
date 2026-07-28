package com.memoria.ecole.exercice;

import com.memoria.ecole.notion.NiveauMaitrise;

import java.util.List;

public record CorrectionTravailPapier(NiveauMaitrise niveau, String syntheseGlobale, List<PointCorrection> points) {
}

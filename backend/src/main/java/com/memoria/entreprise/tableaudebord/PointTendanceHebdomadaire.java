package com.memoria.entreprise.tableaudebord;

import java.time.LocalDate;

// debutSemaine = lundi de la semaine ISO concernee. Agrege, comme le reste
// de ce tableau de bord -- aucun champ ne remonte a un engagement precis.
public record PointTendanceHebdomadaire(LocalDate debutSemaine, long crees, long termines) {
}

package com.memoria.core.filmemoire;

import java.util.List;

public interface GenerateurFilMemoirePort {

    // Decide si le resume d'une session rejoint l'un des fils candidats
    // (deja pre-filtres par similarite d'embedding) ou si elle doit demarrer
    // un nouveau fil -- et produit dans les deux cas le texte du resume
    // cumulatif a jour.
    DecisionFilMemoire deciderFil(String resumeSession, List<CandidatFilMemoire> candidats);
}

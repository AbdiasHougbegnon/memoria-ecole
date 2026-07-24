package com.memoria.ecole.tuteurvocal;

import com.memoria.ecole.notion.NiveauMaitrise;

import java.util.List;

// Detail d'infrastructure remplacable (Azure OpenAI aujourd'hui, un autre
// fournisseur demain). Porte toute la logique pedagogique du tuteur
// ("change d'approche, analogies, reformulation") comme prompt engineering
// dans l'adaptateur, pas comme une machine a etats ici -- voir limites
// documentees dans docs/phases/phase-9-tuteur-vocal.md.
public interface GenerateurTourTuteurPort {

    TourTuteurGenere genererTour(ContexteTour contexte);

    record ContexteTour(
            String notionTerme,
            String notionDefinition,
            List<TourHistorique> historique,
            String derniereReponseEtudiant,
            ModeTutorat mode
    ) {
    }

    record TourHistorique(Locuteur locuteur, String texte) {
    }

    record TourTuteurGenere(String texteTuteur, NiveauMaitrise evaluationMaitrise, boolean notionMaitrisee) {
    }
}

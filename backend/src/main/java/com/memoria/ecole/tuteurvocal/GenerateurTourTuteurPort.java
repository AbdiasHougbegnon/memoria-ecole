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

    // dernierExercice : uniquement significatif en mode EXERCICE, vrai quand
    // ce tour correspond au dernier exercice autorise sur cette notion
    // (NOMBRE_EXERCICES_PAR_NOTION atteint, connu a l'avance cote
    // TuteurVocalService independamment de la reponse de l'etudiant) --
    // evite que le modele propose un nouvel exercice que l'etudiant n'aura
    // jamais l'occasion de faire, puisque Java passe a la notion suivante ou
    // termine la seance juste apres ce tour.
    record ContexteTour(
            String notionTerme,
            String notionDefinition,
            List<TourHistorique> historique,
            String derniereReponseEtudiant,
            ModeTutorat mode,
            boolean dernierExercice
    ) {
    }

    record TourHistorique(Locuteur locuteur, String texte) {
    }

    record TourTuteurGenere(String texteTuteur, NiveauMaitrise evaluationMaitrise, boolean notionMaitrisee) {
    }
}

package com.memoria.ecole.document;

import java.util.List;

// Detail d'infrastructure remplacable (Azure OpenAI aujourd'hui, un autre
// fournisseur demain). Vocabulaire Ecole (notions candidates issues d'une
// fiche de cours) : ce port n'a pas sa place dans le moteur.
public interface GenerateurNotionsDepuisDocumentPort {

    List<CandidatNotionGenere> genererNotionsCandidates(String texteDocument);

    record CandidatNotionGenere(String terme, String definition) {
    }
}

package com.memoria.core.recherche;

import java.util.List;

public interface GenerateurEmbeddingPort {

    // Un embedding (vecteur) par texte en entree, dans le meme ordre -- un
    // seul appel HTTP pour toute la liste (discipline de couts : jamais un
    // appel par segment).
    List<float[]> genererEmbeddings(List<String> textes);
}

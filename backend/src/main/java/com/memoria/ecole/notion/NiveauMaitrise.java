package com.memoria.ecole.notion;

// Enumeration qualitative plutot qu'un score numerique 0-100 : l'evaluation
// d'un LLM tour par tour ("cette reponse montre-t-elle une comprehension
// solide ?") est intrinsequement qualitative -- un score numerique precis
// serait une fausse precision que le modele ne peut pas honnetement produire.
// Mappe directement sur la condition d'arret du master prompt ("ne lache pas
// une notion tant qu'elle n'est pas comprise" = ne pas avancer avant MAITRISEE).
public enum NiveauMaitrise {
    NON_ABORDEE,
    EN_COURS,
    MAITRISEE
}

package com.memoria;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Place volontairement un niveau au-dessus de com.memoria.core : le scan par
// defaut de Spring Boot (composants, entites JPA, repositories) part du
// package de cette classe et descend -- ca couvre ainsi a la fois le moteur
// (com.memoria.core) et la couche Entreprise (com.memoria.entreprise) sans
// annotations de scan manuelles, qui casseraient les tests @WebMvcTest (elles
// contournent le filtrage des tranches de test et forcent le chargement de
// l'infrastructure JPA complete dans un contexte qui n'en a pas).
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}

package com.memoria.ecole.couloir;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContexteScolaireCouloirRepository extends JpaRepository<ContexteScolaireCouloir, UUID> {

    Optional<ContexteScolaireCouloir> findByCouloirId(UUID couloirId);

    // Un parametre null pour specialite est rendu par Spring Data en "IS NULL",
    // ce qui permet de matcher les filieres sans specialite (colonne nullable).
    // Reutilise en phase 17b pour matcher un etudiant a son couloir a l'inscription.
    Optional<ContexteScolaireCouloir> findByAnneeAcademiqueAndFiliereAndSpecialite(
            String anneeAcademique, String filiere, String specialite
    );
}

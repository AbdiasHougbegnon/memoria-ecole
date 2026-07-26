package com.memoria.ecole.couloir;

import com.memoria.core.auth.AuthResponse;
import com.memoria.core.auth.AuthService;
import com.memoria.core.auth.ModuleMemoria;
import com.memoria.core.couloir.CouloirService;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// Inscription Ecole : au lieu de rejoindre un couloir explicitement (flux
// generique core/couloir), l'etudiant choisit une classe (annee academique /
// filiere / specialite) resolue en couloir via ContexteScolaireCouloirRepository
// (peuplee par l'import en masse, phase-17a). Bloque explicitement si la
// classe n'existe pas encore -- l'admin doit l'avoir importee au prealable,
// voir docs/phases/phase-17a-import-matieres.md -- plutot que de creer un
// compte orphelin ou de replier silencieusement vers "rejoindre un couloir".
// Reutilise AuthService/CouloirService (core) tels quels : aucun vocabulaire
// Ecole ne remonte dans le moteur, c'est cette classe qui orchestre.
@Service
public class InscriptionEcoleService {

    private final AuthService authService;
    private final CouloirService couloirService;
    private final ContexteScolaireCouloirRepository contexteScolaireCouloirRepository;

    public InscriptionEcoleService(
            AuthService authService,
            CouloirService couloirService,
            ContexteScolaireCouloirRepository contexteScolaireCouloirRepository
    ) {
        this.authService = authService;
        this.couloirService = couloirService;
        this.contexteScolaireCouloirRepository = contexteScolaireCouloirRepository;
    }

    public AuthResponse inscrire(String email, String motDePasse, String anneeAcademique, String filiere, String specialite) {
        String specialiteNormalisee = normaliser(specialite);
        UUID couloirId = contexteScolaireCouloirRepository
                .findByAnneeAcademiqueAndFiliereAndSpecialite(anneeAcademique, filiere, specialiteNormalisee)
                .map(ContexteScolaireCouloir::getCouloirId)
                .orElseThrow(() -> new ClasseIntrouvableException(anneeAcademique, filiere, specialiteNormalisee));

        AuthResponse reponse = authService.inscrire(email, motDePasse, ModuleMemoria.ECOLE);
        couloirService.rejoindreCouloir(couloirId, reponse.utilisateurId());
        return reponse;
    }

    public List<OptionInscriptionResponse> listerOptionsInscription() {
        return contexteScolaireCouloirRepository.findAll().stream()
                .map(OptionInscriptionResponse::depuis)
                .distinct()
                .toList();
    }

    private String normaliser(String specialite) {
        return (specialite == null || specialite.isBlank()) ? null : specialite;
    }
}

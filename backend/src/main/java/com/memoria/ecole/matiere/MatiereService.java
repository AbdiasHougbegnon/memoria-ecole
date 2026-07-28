package com.memoria.ecole.matiere;

import com.memoria.core.couloir.CouloirService;
import com.memoria.core.couloir.PasMembreDuCouloirException;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// Seul le proprietaire du couloir (enseignant) peut creer une Matiere pour ce
// couloir -- reutilise CouloirService/PasProprietaireDuCouloirException tels
// quels plutot que de dupliquer une notion de role enseignant/eleve, qui
// n'existe pas ailleurs dans le projet (voir docs/phases/phase-9-tuteur-vocal.md).
@Service
public class MatiereService {

    private final MatiereRepository matiereRepository;
    private final CouloirService couloirService;

    public MatiereService(MatiereRepository matiereRepository, CouloirService couloirService) {
        this.matiereRepository = matiereRepository;
        this.couloirService = couloirService;
    }

    public Matiere creerMatiere(String nom, UUID couloirId, UUID utilisateurId) {
        couloirService.verifierProprietaireDuCouloir(couloirId, utilisateurId);
        return matiereRepository.save(new Matiere(nom, couloirId, utilisateurId));
    }

    public Matiere obtenirMatiere(UUID id) {
        return matiereRepository.findById(id)
                .orElseThrow(() -> new MatiereNotFoundException(id));
    }

    public List<Matiere> listerMatieresParCouloir(UUID couloirId) {
        return matiereRepository.findByCouloirId(couloirId);
    }

    // Membre OU proprietaire du couloir (contrairement a creerMatiere, reserve
    // au proprietaire) : consulter/reviser une matiere est ouvert a tout le
    // couloir, seule la modification du contenu pedagogique est reservee a
    // l'enseignant. Partage par QcmMatiereService et ExerciceSaisieLibreService
    // (phase 22c/22d) pour eviter de dupliquer ce controle une troisieme fois.
    public void verifierMembreDuCouloir(UUID matiereId, UUID utilisateurId) {
        Matiere matiere = obtenirMatiere(matiereId);
        if (!couloirService.estMembre(matiere.getCouloirId(), utilisateurId)) {
            throw new PasMembreDuCouloirException(matiere.getCouloirId(), utilisateurId);
        }
    }
}

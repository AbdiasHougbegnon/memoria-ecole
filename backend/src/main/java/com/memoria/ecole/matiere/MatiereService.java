package com.memoria.ecole.matiere;

import com.memoria.core.couloir.CouloirService;

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
}

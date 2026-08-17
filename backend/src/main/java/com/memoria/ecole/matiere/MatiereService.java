package com.memoria.ecole.matiere;

import com.memoria.core.couloir.CouloirService;
import com.memoria.core.couloir.PasMembreDuCouloirException;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// Tout membre du couloir peut creer une Matiere pour ce couloir -- aucun role
// enseignant/eleve dans le projet, tous les membres ont les memes droits
// (voir CouloirService.verifierMembre).
@Service
public class MatiereService {

    private final MatiereRepository matiereRepository;
    private final CouloirService couloirService;

    public MatiereService(MatiereRepository matiereRepository, CouloirService couloirService) {
        this.matiereRepository = matiereRepository;
        this.couloirService = couloirService;
    }

    public Matiere creerMatiere(String nom, UUID couloirId, UUID utilisateurId) {
        couloirService.verifierMembre(couloirId, utilisateurId);
        return matiereRepository.save(new Matiere(nom, couloirId, utilisateurId));
    }

    public Matiere obtenirMatiere(UUID id) {
        return matiereRepository.findById(id)
                .orElseThrow(() -> new MatiereNotFoundException(id));
    }

    public List<Matiere> listerMatieresParCouloir(UUID couloirId) {
        return matiereRepository.findByCouloirId(couloirId);
    }

    // Vue transverse "mes matieres" (tous couloirs confondus) pour les entrees
    // de menu Revision/Tutorat -- avant cet increment, une matiere n'etait
    // accessible qu'en passant par son couloir, ce qui obligeait l'etudiant a
    // se souvenir de quel couloir contenait quelle matiere avant meme de
    // pouvoir reviser. Reutilise listerMesCouloirs, pas de nouvelle requete
    // repository necessaire.
    public List<Matiere> listerMesMatieres(UUID utilisateurId) {
        return couloirService.listerMesCouloirs(utilisateurId).stream()
                .flatMap(couloir -> matiereRepository.findByCouloirId(couloir.getId()).stream())
                .toList();
    }

    // Variante de CouloirService.verifierMembre qui prend une matiereId plutot
    // qu'une couloirId en entree. Partage par QcmMatiereService et
    // ExerciceSaisieLibreService (phase 22c/22d) pour eviter de dupliquer ce
    // controle une troisieme fois.
    public void verifierMembreDuCouloir(UUID matiereId, UUID utilisateurId) {
        Matiere matiere = obtenirMatiere(matiereId);
        if (!couloirService.estMembre(matiere.getCouloirId(), utilisateurId)) {
            throw new PasMembreDuCouloirException(matiere.getCouloirId(), utilisateurId);
        }
    }
}

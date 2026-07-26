package com.memoria.ecole.couloir;

import com.memoria.core.couloir.Couloir;
import com.memoria.core.couloir.CouloirService;
import com.memoria.ecole.matiere.Matiere;
import com.memoria.ecole.matiere.MatiereService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Un administrateur (au sens : n'importe quel utilisateur authentifie --
// symetrique a CouloirController.creerCouloir qui ne verifie deja aucun
// role, voir docs/phases/phase-17a-import-matieres.md) uploade un CSV
// listant les matieres de toute une promotion. Ce service regroupe les
// lignes par (annee, filiere, specialite), cree un Couloir + un
// ContexteScolaireCouloir par groupe distinct puis une Matiere par ligne --
// en reutilisant CouloirService/MatiereService existants tels quels, jamais
// en les contournant. Idempotent au niveau du triplet et du nom de matiere :
// relancer le meme fichier deux fois ne duplique rien, complete seulement ce
// qui manque.
@Service
public class ImportMatieresService {

    private static final int COLONNE_ANNEE = 0;
    private static final int COLONNE_FILIERE = 1;
    private static final int COLONNE_SPECIALITE = 2;
    private static final int COLONNE_MATIERE = 3;
    private static final int NB_COLONNES_ATTENDUES = 4;

    private final CouloirService couloirService;
    private final MatiereService matiereService;
    private final ContexteScolaireCouloirRepository contexteScolaireCouloirRepository;

    public ImportMatieresService(
            CouloirService couloirService,
            MatiereService matiereService,
            ContexteScolaireCouloirRepository contexteScolaireCouloirRepository
    ) {
        this.couloirService = couloirService;
        this.matiereService = matiereService;
        this.contexteScolaireCouloirRepository = contexteScolaireCouloirRepository;
    }

    @Transactional
    public RapportImportMatieres importer(byte[] contenuCsv, UUID utilisateurId) {
        List<LigneImport> lignes = new ArrayList<>();
        List<ErreurImport> erreurs = new ArrayList<>();
        lireLignes(contenuCsv, lignes, erreurs);

        Map<TripletFiliere, List<LigneImport>> groupes = new LinkedHashMap<>();
        for (LigneImport ligne : lignes) {
            groupes.computeIfAbsent(ligne.triplet(), t -> new ArrayList<>()).add(ligne);
        }

        int couloirsCrees = 0;
        int couloirsExistants = 0;
        int matieresCreees = 0;
        int matieresExistantes = 0;

        for (Map.Entry<TripletFiliere, List<LigneImport>> entree : groupes.entrySet()) {
            TripletFiliere triplet = entree.getKey();
            UUID couloirId;
            Optional<ContexteScolaireCouloir> contexteExistant = contexteScolaireCouloirRepository
                    .findByAnneeAcademiqueAndFiliereAndSpecialite(triplet.anneeAcademique(), triplet.filiere(), triplet.specialite());
            if (contexteExistant.isPresent()) {
                couloirId = contexteExistant.get().getCouloirId();
                couloirsExistants++;
            } else {
                Couloir couloir = couloirService.creerCouloir(triplet.nomCouloirGenere(), utilisateurId);
                contexteScolaireCouloirRepository.save(
                        new ContexteScolaireCouloir(couloir.getId(), triplet.anneeAcademique(), triplet.filiere(), triplet.specialite())
                );
                couloirId = couloir.getId();
                couloirsCrees++;
            }

            Set<String> matieresDejaPresentes = new HashSet<>();
            for (Matiere matiere : matiereService.listerMatieresParCouloir(couloirId)) {
                matieresDejaPresentes.add(matiere.getNom());
            }

            for (LigneImport ligne : entree.getValue()) {
                if (matieresDejaPresentes.contains(ligne.nomMatiere())) {
                    matieresExistantes++;
                    continue;
                }
                matiereService.creerMatiere(ligne.nomMatiere(), couloirId, utilisateurId);
                matieresDejaPresentes.add(ligne.nomMatiere());
                matieresCreees++;
            }
        }

        return new RapportImportMatieres(couloirsCrees, couloirsExistants, matieresCreees, matieresExistantes, erreurs);
    }

    private void lireLignes(byte[] contenuCsv, List<LigneImport> lignes, List<ErreurImport> erreurs) {
        try (BufferedReader lecteur = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(contenuCsv), StandardCharsets.UTF_8))) {
            String ligneEntete = lecteur.readLine();
            if (ligneEntete == null) {
                erreurs.add(new ErreurImport(1, "fichier vide"));
                return;
            }
            String ligneBrute;
            int numeroLigne = 1;
            while ((ligneBrute = lecteur.readLine()) != null) {
                numeroLigne++;
                if (ligneBrute.isBlank()) {
                    continue;
                }
                List<String> valeurs = parserLigneCsv(ligneBrute);
                if (valeurs.size() < NB_COLONNES_ATTENDUES) {
                    erreurs.add(new ErreurImport(numeroLigne,
                            "colonnes manquantes (attendu : annee_academique,filiere,specialite,nom_matiere)"));
                    continue;
                }
                String annee = valeurs.get(COLONNE_ANNEE).trim();
                String filiere = valeurs.get(COLONNE_FILIERE).trim();
                String specialite = valeurs.get(COLONNE_SPECIALITE).trim();
                String nomMatiere = valeurs.get(COLONNE_MATIERE).trim();
                if (annee.isEmpty() || filiere.isEmpty() || nomMatiere.isEmpty()) {
                    erreurs.add(new ErreurImport(numeroLigne, "annee_academique, filiere et nom_matiere sont obligatoires"));
                    continue;
                }
                lignes.add(new LigneImport(annee, filiere, specialite.isEmpty() ? null : specialite, nomMatiere));
            }
        } catch (IOException e) {
            erreurs.add(new ErreurImport(0, "lecture du fichier impossible : " + e.getMessage()));
        }
    }

    // Gere le cas simple d'un champ contenant une virgule entre guillemets
    // (ex. un nom de matiere) -- pas de prise en charge de guillemets
    // multi-lignes, hors-scope pour un CSV plat destine a etre edite dans
    // Excel par un administratif.
    private static List<String> parserLigneCsv(String ligne) {
        List<String> valeurs = new ArrayList<>();
        StringBuilder courant = new StringBuilder();
        boolean dansGuillemets = false;
        for (int i = 0; i < ligne.length(); i++) {
            char c = ligne.charAt(i);
            if (c == '"') {
                dansGuillemets = !dansGuillemets;
            } else if (c == ',' && !dansGuillemets) {
                valeurs.add(courant.toString());
                courant.setLength(0);
            } else {
                courant.append(c);
            }
        }
        valeurs.add(courant.toString());
        return valeurs;
    }

    private record LigneImport(String anneeAcademique, String filiere, String specialite, String nomMatiere) {
        TripletFiliere triplet() {
            return new TripletFiliere(anneeAcademique, filiere, specialite);
        }
    }

    private record TripletFiliere(String anneeAcademique, String filiere, String specialite) {
        String nomCouloirGenere() {
            return filiere + (specialite != null ? " - " + specialite : "") + " - " + anneeAcademique;
        }
    }
}

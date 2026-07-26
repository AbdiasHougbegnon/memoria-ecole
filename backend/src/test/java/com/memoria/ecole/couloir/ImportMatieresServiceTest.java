package com.memoria.ecole.couloir;

import com.memoria.core.couloir.Couloir;
import com.memoria.core.couloir.CouloirService;
import com.memoria.ecole.matiere.Matiere;
import com.memoria.ecole.matiere.MatiereService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportMatieresServiceTest {

    @Mock
    private CouloirService couloirService;

    @Mock
    private MatiereService matiereService;

    @Mock
    private ContexteScolaireCouloirRepository contexteScolaireCouloirRepository;

    private ImportMatieresService importMatieresService;

    @BeforeEach
    void setUp() {
        importMatieresService = new ImportMatieresService(couloirService, matiereService, contexteScolaireCouloirRepository);
    }

    @Test
    void importer_groupe_les_lignes_par_triplet_et_cree_un_seul_couloir_pour_deux_matieres() {
        UUID utilisateurId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        String csv = """
                annee_academique,filiere,specialite,nom_matiere
                2026-2027,Informatique,Genie Logiciel,Algorithmique
                2026-2027,Informatique,Genie Logiciel,Bases de donnees
                """;
        when(contexteScolaireCouloirRepository.findByAnneeAcademiqueAndFiliereAndSpecialite(
                "2026-2027", "Informatique", "Genie Logiciel")).thenReturn(Optional.empty());
        Couloir couloir = new Couloir("Informatique - Genie Logiciel - 2026-2027", utilisateurId);
        when(couloirService.creerCouloir(any(), eq(utilisateurId))).thenReturn(couloir);
        when(matiereService.listerMatieresParCouloir(couloir.getId())).thenReturn(List.of());

        RapportImportMatieres rapport = importMatieresService.importer(csv.getBytes(StandardCharsets.UTF_8), utilisateurId);

        assertThat(rapport.couloirsCrees()).isEqualTo(1);
        assertThat(rapport.matieresCreees()).isEqualTo(2);
        assertThat(rapport.erreurs()).isEmpty();
        verify(couloirService, times(1)).creerCouloir(any(), eq(utilisateurId));
        verify(matiereService).creerMatiere("Algorithmique", couloir.getId(), utilisateurId);
        verify(matiereService).creerMatiere("Bases de donnees", couloir.getId(), utilisateurId);
    }

    @Test
    void importer_reutilise_le_couloir_existant_si_le_triplet_est_deja_importe() {
        UUID utilisateurId = UUID.randomUUID();
        UUID couloirExistantId = UUID.randomUUID();
        String csv = """
                annee_academique,filiere,specialite,nom_matiere
                2026-2027,Informatique,Genie Logiciel,Algorithmique
                """;
        ContexteScolaireCouloir contexte = new ContexteScolaireCouloir(couloirExistantId, "2026-2027", "Informatique", "Genie Logiciel");
        when(contexteScolaireCouloirRepository.findByAnneeAcademiqueAndFiliereAndSpecialite(
                "2026-2027", "Informatique", "Genie Logiciel")).thenReturn(Optional.of(contexte));
        when(matiereService.listerMatieresParCouloir(couloirExistantId)).thenReturn(List.of());

        RapportImportMatieres rapport = importMatieresService.importer(csv.getBytes(StandardCharsets.UTF_8), utilisateurId);

        assertThat(rapport.couloirsCrees()).isEqualTo(0);
        assertThat(rapport.couloirsExistants()).isEqualTo(1);
        verify(couloirService, never()).creerCouloir(any(), any());
        verify(matiereService).creerMatiere("Algorithmique", couloirExistantId, utilisateurId);
    }

    @Test
    void importer_ne_recree_pas_une_matiere_deja_presente_dans_le_couloir() {
        UUID utilisateurId = UUID.randomUUID();
        UUID couloirExistantId = UUID.randomUUID();
        String csv = """
                annee_academique,filiere,specialite,nom_matiere
                2026-2027,Informatique,Genie Logiciel,Algorithmique
                """;
        ContexteScolaireCouloir contexte = new ContexteScolaireCouloir(couloirExistantId, "2026-2027", "Informatique", "Genie Logiciel");
        when(contexteScolaireCouloirRepository.findByAnneeAcademiqueAndFiliereAndSpecialite(
                "2026-2027", "Informatique", "Genie Logiciel")).thenReturn(Optional.of(contexte));
        Matiere matiereExistante = new Matiere("Algorithmique", couloirExistantId, utilisateurId);
        when(matiereService.listerMatieresParCouloir(couloirExistantId)).thenReturn(List.of(matiereExistante));

        RapportImportMatieres rapport = importMatieresService.importer(csv.getBytes(StandardCharsets.UTF_8), utilisateurId);

        assertThat(rapport.matieresCreees()).isEqualTo(0);
        assertThat(rapport.matieresExistantes()).isEqualTo(1);
        verify(matiereService, never()).creerMatiere(any(), any(), any());
    }

    @Test
    void importer_accepte_une_specialite_vide_et_la_traite_comme_nulle() {
        UUID utilisateurId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        String csv = """
                annee_academique,filiere,specialite,nom_matiere
                2026-2027,Droit,,Introduction au droit
                """;
        when(contexteScolaireCouloirRepository.findByAnneeAcademiqueAndFiliereAndSpecialite(
                eq("2026-2027"), eq("Droit"), isNull())).thenReturn(Optional.empty());
        Couloir couloir = new Couloir("Droit - 2026-2027", utilisateurId);
        when(couloirService.creerCouloir(any(), eq(utilisateurId))).thenReturn(couloir);
        when(matiereService.listerMatieresParCouloir(couloir.getId())).thenReturn(List.of());

        RapportImportMatieres rapport = importMatieresService.importer(csv.getBytes(StandardCharsets.UTF_8), utilisateurId);

        assertThat(rapport.couloirsCrees()).isEqualTo(1);
        assertThat(rapport.erreurs()).isEmpty();
    }

    @Test
    void importer_signale_les_lignes_incompletes_sans_bloquer_les_autres() {
        UUID utilisateurId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        String csv = """
                annee_academique,filiere,specialite,nom_matiere
                2026-2027,Informatique
                2026-2027,Informatique,Genie Logiciel,Algorithmique
                """;
        when(contexteScolaireCouloirRepository.findByAnneeAcademiqueAndFiliereAndSpecialite(
                "2026-2027", "Informatique", "Genie Logiciel")).thenReturn(Optional.empty());
        Couloir couloir = new Couloir("Informatique - Genie Logiciel - 2026-2027", utilisateurId);
        when(couloirService.creerCouloir(any(), eq(utilisateurId))).thenReturn(couloir);
        when(matiereService.listerMatieresParCouloir(couloir.getId())).thenReturn(List.of());

        RapportImportMatieres rapport = importMatieresService.importer(csv.getBytes(StandardCharsets.UTF_8), utilisateurId);

        assertThat(rapport.erreurs()).hasSize(1);
        assertThat(rapport.erreurs().get(0).numeroLigne()).isEqualTo(2);
        assertThat(rapport.matieresCreees()).isEqualTo(1);
    }

    @Test
    void importer_signale_un_fichier_vide() {
        RapportImportMatieres rapport = importMatieresService.importer(new byte[0], UUID.randomUUID());

        assertThat(rapport.erreurs()).hasSize(1);
        assertThat(rapport.couloirsCrees()).isEqualTo(0);
        verify(couloirService, never()).creerCouloir(any(), any());
    }
}

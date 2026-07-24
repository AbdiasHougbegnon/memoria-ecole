package com.memoria.ecole.notion;

import com.memoria.core.couloir.Couloir;
import com.memoria.core.couloir.CouloirService;
import com.memoria.core.couloir.PasProprietaireDuCouloirException;
import com.memoria.ecole.matiere.Matiere;
import com.memoria.ecole.matiere.MatiereService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotionServiceTest {

    @Mock
    private NotionRepository notionRepository;

    @Mock
    private MaitriseNotionRepository maitriseNotionRepository;

    @Mock
    private MatiereService matiereService;

    @Mock
    private CouloirService couloirService;

    private NotionService notionService;

    @BeforeEach
    void setUp() {
        notionService = new NotionService(notionRepository, maitriseNotionRepository, matiereService, couloirService);
    }

    @Test
    void creerNotion_sauvegarde_la_notion_si_proprietaire_du_couloir() {
        UUID proprietaireId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        Matiere matiere = new Matiere("Mathematiques", couloirId, proprietaireId);
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(matiere);
        when(couloirService.obtenirCouloir(couloirId)).thenReturn(new Couloir("Ing1-SI EPISEN", proprietaireId));
        when(notionRepository.save(any(Notion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Notion notion = notionService.creerNotion(matiereId, "Derivees", "Taux de variation instantane", 1, proprietaireId);

        assertThat(notion.getTerme()).isEqualTo("Derivees");
        assertThat(notion.getMatiereId()).isEqualTo(matiereId);
        assertThat(notion.getOrdre()).isEqualTo(1);
    }

    @Test
    void creerNotion_leve_une_exception_si_pas_proprietaire_du_couloir() {
        UUID proprietaireId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        Matiere matiere = new Matiere("Mathematiques", couloirId, proprietaireId);
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(matiere);
        when(couloirService.obtenirCouloir(couloirId)).thenReturn(new Couloir("Ing1-SI EPISEN", proprietaireId));

        assertThatThrownBy(() -> notionService.creerNotion(matiereId, "Derivees", "def", 1, UUID.randomUUID()))
                .isInstanceOf(PasProprietaireDuCouloirException.class);
        verify(notionRepository, never()).save(any());
    }

    @Test
    void obtenirNotion_leve_une_exception_si_introuvable() {
        UUID id = UUID.randomUUID();
        when(notionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notionService.obtenirNotion(id))
                .isInstanceOf(NotionNotFoundException.class);
    }

    @Test
    void obtenirNiveauMaitrise_retourne_non_abordee_si_aucune_ligne() {
        UUID notionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(maitriseNotionRepository.findByNotionIdAndUtilisateurId(notionId, utilisateurId)).thenReturn(Optional.empty());

        NiveauMaitrise resultat = notionService.obtenirNiveauMaitrise(notionId, utilisateurId);

        assertThat(resultat).isEqualTo(NiveauMaitrise.NON_ABORDEE);
    }

    @Test
    void obtenirNiveauMaitrise_retourne_le_niveau_existant() {
        UUID notionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        MaitriseNotion maitrise = new MaitriseNotion(notionId, utilisateurId);
        maitrise.mettreAJour(NiveauMaitrise.MAITRISEE);
        when(maitriseNotionRepository.findByNotionIdAndUtilisateurId(notionId, utilisateurId)).thenReturn(Optional.of(maitrise));

        NiveauMaitrise resultat = notionService.obtenirNiveauMaitrise(notionId, utilisateurId);

        assertThat(resultat).isEqualTo(NiveauMaitrise.MAITRISEE);
    }

    @Test
    void obtenirMaitrises_retourne_non_abordee_par_defaut_pour_les_notions_sans_ligne() {
        UUID utilisateurId = UUID.randomUUID();
        UUID notion1 = UUID.randomUUID();
        UUID notion2 = UUID.randomUUID();
        MaitriseNotion maitriseNotion1 = new MaitriseNotion(notion1, utilisateurId);
        maitriseNotion1.mettreAJour(NiveauMaitrise.EN_COURS);
        when(maitriseNotionRepository.findByUtilisateurIdAndNotionIdIn(utilisateurId, List.of(notion1, notion2)))
                .thenReturn(List.of(maitriseNotion1));

        Map<UUID, NiveauMaitrise> resultat = notionService.obtenirMaitrises(List.of(notion1, notion2), utilisateurId);

        assertThat(resultat).containsEntry(notion1, NiveauMaitrise.EN_COURS);
        assertThat(resultat).containsEntry(notion2, NiveauMaitrise.NON_ABORDEE);
    }

    @Test
    void mettreAJourMaitrise_cree_la_ligne_si_absente() {
        UUID notionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(maitriseNotionRepository.findByNotionIdAndUtilisateurId(notionId, utilisateurId)).thenReturn(Optional.empty());
        when(maitriseNotionRepository.save(any(MaitriseNotion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MaitriseNotion resultat = notionService.mettreAJourMaitrise(notionId, utilisateurId, NiveauMaitrise.EN_COURS);

        assertThat(resultat.getNiveau()).isEqualTo(NiveauMaitrise.EN_COURS);
        assertThat(resultat.getNombreTentatives()).isEqualTo(1);
    }

    @Test
    void mettreAJourMaitrise_met_a_jour_la_ligne_existante_et_incremente_les_tentatives() {
        UUID notionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        MaitriseNotion existante = new MaitriseNotion(notionId, utilisateurId);
        existante.mettreAJour(NiveauMaitrise.EN_COURS);
        when(maitriseNotionRepository.findByNotionIdAndUtilisateurId(notionId, utilisateurId)).thenReturn(Optional.of(existante));
        when(maitriseNotionRepository.save(any(MaitriseNotion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MaitriseNotion resultat = notionService.mettreAJourMaitrise(notionId, utilisateurId, NiveauMaitrise.MAITRISEE);

        assertThat(resultat.getNiveau()).isEqualTo(NiveauMaitrise.MAITRISEE);
        assertThat(resultat.getNombreTentatives()).isEqualTo(2);
    }
}

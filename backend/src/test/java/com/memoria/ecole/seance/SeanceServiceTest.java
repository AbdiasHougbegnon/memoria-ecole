package com.memoria.ecole.seance;

import com.memoria.core.couloir.CouloirService;
import com.memoria.core.couloir.PasMembreDuCouloirException;
import com.memoria.ecole.matiere.Matiere;
import com.memoria.ecole.matiere.MatiereService;
import com.memoria.ecole.notion.Notion;
import com.memoria.ecole.notion.NotionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeanceServiceTest {

    @Mock
    private SeanceRepository seanceRepository;

    @Mock
    private SeanceNotionRepository seanceNotionRepository;

    @Mock
    private MatiereService matiereService;

    @Mock
    private NotionService notionService;

    @Mock
    private CouloirService couloirService;

    private SeanceService seanceService;

    @BeforeEach
    void setUp() {
        seanceService = new SeanceService(seanceRepository, seanceNotionRepository, matiereService, notionService, couloirService);
    }

    @Test
    void creerSeance_sauvegarde_la_seance_avec_le_couloir_denormalise_si_membre() {
        UUID membreId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        Matiere matiere = new Matiere("Mathematiques", couloirId, UUID.randomUUID());
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(matiere);
        when(seanceRepository.save(any(Seance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Seance seance = seanceService.creerSeance("Cours 1 : derivees", matiereId, membreId);

        assertThat(seance.getTitre()).isEqualTo("Cours 1 : derivees");
        assertThat(seance.getMatiereId()).isEqualTo(matiereId);
        assertThat(seance.getCouloirId()).isEqualTo(couloirId);
    }

    @Test
    void creerSeance_leve_une_exception_si_pas_membre_du_couloir() {
        UUID couloirId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Matiere matiere = new Matiere("Mathematiques", couloirId, UUID.randomUUID());
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(matiere);
        doThrow(new PasMembreDuCouloirException(couloirId, utilisateurId))
                .when(couloirService).verifierMembre(couloirId, utilisateurId);

        assertThatThrownBy(() -> seanceService.creerSeance("Cours 1", matiereId, utilisateurId))
                .isInstanceOf(PasMembreDuCouloirException.class);
        verify(seanceRepository, never()).save(any());
    }

    @Test
    void obtenirSeance_leve_une_exception_si_introuvable() {
        UUID id = UUID.randomUUID();
        when(seanceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seanceService.obtenirSeance(id))
                .isInstanceOf(SeanceNotFoundException.class);
    }

    @Test
    void rattacherNotions_remplace_les_notions_existantes_dans_lordre_donne() {
        UUID proprietaireId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        Seance seance = new Seance("Cours 1", UUID.randomUUID(), couloirId);
        UUID notion1 = UUID.randomUUID();
        UUID notion2 = UUID.randomUUID();
        when(seanceRepository.findById(seance.getId())).thenReturn(Optional.of(seance));

        seanceService.rattacherNotions(seance.getId(), List.of(notion1, notion2), proprietaireId);

        verify(seanceNotionRepository).deleteBySeanceId(seance.getId());
        ArgumentCaptor<SeanceNotion> captor = ArgumentCaptor.forClass(SeanceNotion.class);
        verify(seanceNotionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<SeanceNotion> sauvegardees = captor.getAllValues();
        assertThat(sauvegardees.get(0).getNotionId()).isEqualTo(notion1);
        assertThat(sauvegardees.get(0).getOrdre()).isEqualTo(0);
        assertThat(sauvegardees.get(1).getNotionId()).isEqualTo(notion2);
        assertThat(sauvegardees.get(1).getOrdre()).isEqualTo(1);
    }

    // Regression : sans flush() explicite entre la suppression et la
    // reinsertion, Hibernate execute les INSERT en attente avant les DELETE
    // en attente au sein d'un meme flush (ordre fixe de son ActionQueue),
    // deleteBySeanceId n'etant pas une requete bulk mais un "find puis
    // remove" differe -- reinserer une notion deja rattachee violait alors
    // la contrainte unique (seance_id, notion_id). Voir
    // docs/phases/phase-16-corrections-tuteur-vocal.md.
    @Test
    void rattacherNotions_vide_le_cache_hibernate_entre_suppression_et_reinsertion() {
        UUID proprietaireId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        Seance seance = new Seance("Cours 1", UUID.randomUUID(), couloirId);
        UUID notionDejaRattachee = UUID.randomUUID();
        when(seanceRepository.findById(seance.getId())).thenReturn(Optional.of(seance));

        seanceService.rattacherNotions(seance.getId(), List.of(notionDejaRattachee), proprietaireId);

        org.mockito.InOrder ordre = org.mockito.Mockito.inOrder(seanceNotionRepository);
        ordre.verify(seanceNotionRepository).deleteBySeanceId(seance.getId());
        ordre.verify(seanceNotionRepository).flush();
        ordre.verify(seanceNotionRepository).save(any());
    }

    @Test
    void rattacherNotions_leve_une_exception_si_pas_membre_du_couloir() {
        UUID couloirId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Seance seance = new Seance("Cours 1", UUID.randomUUID(), couloirId);
        when(seanceRepository.findById(seance.getId())).thenReturn(Optional.of(seance));
        doThrow(new PasMembreDuCouloirException(couloirId, utilisateurId))
                .when(couloirService).verifierMembre(couloirId, utilisateurId);

        assertThatThrownBy(() -> seanceService.rattacherNotions(seance.getId(), List.of(UUID.randomUUID()), utilisateurId))
                .isInstanceOf(PasMembreDuCouloirException.class);
        verify(seanceNotionRepository, never()).deleteBySeanceId(any());
    }

    @Test
    void supprimerSeance_nettoie_les_rattachements_puis_supprime_si_membre() {
        UUID membreId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        Seance seance = new Seance("Cours 1", UUID.randomUUID(), couloirId);
        when(seanceRepository.findById(seance.getId())).thenReturn(Optional.of(seance));

        seanceService.supprimerSeance(seance.getId(), membreId);

        verify(couloirService).verifierMembre(couloirId, membreId);
        verify(seanceNotionRepository).deleteBySeanceId(seance.getId());
        verify(seanceRepository).deleteById(seance.getId());
    }

    @Test
    void supprimerSeance_leve_une_exception_si_pas_membre_du_couloir() {
        UUID couloirId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Seance seance = new Seance("Cours 1", UUID.randomUUID(), couloirId);
        when(seanceRepository.findById(seance.getId())).thenReturn(Optional.of(seance));
        doThrow(new PasMembreDuCouloirException(couloirId, utilisateurId))
                .when(couloirService).verifierMembre(couloirId, utilisateurId);

        assertThatThrownBy(() -> seanceService.supprimerSeance(seance.getId(), utilisateurId))
                .isInstanceOf(PasMembreDuCouloirException.class);
        verify(seanceRepository, never()).deleteById(any());
    }

    @Test
    void obtenirOuCreerSeanceDiscussionLibre_reutilise_la_seance_existante_si_elle_existe_deja() {
        UUID matiereId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        UUID membreId = UUID.randomUUID();
        Matiere matiere = new Matiere("Mathematiques", couloirId, UUID.randomUUID());
        Seance existante = new Seance("Discussion libre", matiereId, couloirId);
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(matiere);
        when(seanceRepository.findByMatiereIdAndTitre(matiereId, "Discussion libre")).thenReturn(Optional.of(existante));

        Seance resultat = seanceService.obtenirOuCreerSeanceDiscussionLibre(matiereId, membreId);

        assertThat(resultat).isSameAs(existante);
        verify(matiereService).verifierMembreDuCouloir(matiereId, membreId);
        verify(seanceRepository, never()).save(any());
    }

    @Test
    void obtenirOuCreerSeanceDiscussionLibre_en_cree_une_si_aucune_nexiste() {
        UUID matiereId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        UUID membreId = UUID.randomUUID();
        Matiere matiere = new Matiere("Mathematiques", couloirId, UUID.randomUUID());
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(matiere);
        when(seanceRepository.findByMatiereIdAndTitre(matiereId, "Discussion libre")).thenReturn(Optional.empty());
        when(seanceRepository.save(any(Seance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Seance resultat = seanceService.obtenirOuCreerSeanceDiscussionLibre(matiereId, membreId);

        assertThat(resultat.getTitre()).isEqualTo("Discussion libre");
        assertThat(resultat.getMatiereId()).isEqualTo(matiereId);
        assertThat(resultat.getCouloirId()).isEqualTo(couloirId);
    }

    @Test
    void listerNotionsDeSeance_retourne_les_notions_dans_lordre() {
        UUID seanceId = UUID.randomUUID();
        UUID notion1Id = UUID.randomUUID();
        UUID notion2Id = UUID.randomUUID();
        SeanceNotion sn1 = new SeanceNotion(seanceId, notion1Id, 0);
        SeanceNotion sn2 = new SeanceNotion(seanceId, notion2Id, 1);
        Notion notion1 = new Notion(UUID.randomUUID(), "Derivees", "def1", 0);
        Notion notion2 = new Notion(UUID.randomUUID(), "Integrales", "def2", 1);
        when(seanceNotionRepository.findBySeanceIdOrderByOrdreAsc(seanceId)).thenReturn(List.of(sn1, sn2));
        when(notionService.obtenirNotion(notion1Id)).thenReturn(notion1);
        when(notionService.obtenirNotion(notion2Id)).thenReturn(notion2);

        List<Notion> resultat = seanceService.listerNotionsDeSeance(seanceId);

        assertThat(resultat).containsExactly(notion1, notion2);
    }
}

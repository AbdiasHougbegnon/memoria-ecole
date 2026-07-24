package com.memoria.ecole.seance;

import com.memoria.core.couloir.Couloir;
import com.memoria.core.couloir.CouloirService;
import com.memoria.core.couloir.PasProprietaireDuCouloirException;
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
    void creerSeance_sauvegarde_la_seance_avec_le_couloir_denormalise_si_proprietaire() {
        UUID proprietaireId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        Matiere matiere = new Matiere("Mathematiques", couloirId, proprietaireId);
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(matiere);
        when(couloirService.obtenirCouloir(couloirId)).thenReturn(new Couloir("Ing1-SI EPISEN", proprietaireId));
        when(seanceRepository.save(any(Seance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Seance seance = seanceService.creerSeance("Cours 1 : derivees", matiereId, proprietaireId);

        assertThat(seance.getTitre()).isEqualTo("Cours 1 : derivees");
        assertThat(seance.getMatiereId()).isEqualTo(matiereId);
        assertThat(seance.getCouloirId()).isEqualTo(couloirId);
    }

    @Test
    void creerSeance_leve_une_exception_si_pas_proprietaire_du_couloir() {
        UUID proprietaireId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        Matiere matiere = new Matiere("Mathematiques", couloirId, proprietaireId);
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(matiere);
        when(couloirService.obtenirCouloir(couloirId)).thenReturn(new Couloir("Ing1-SI EPISEN", proprietaireId));

        assertThatThrownBy(() -> seanceService.creerSeance("Cours 1", matiereId, UUID.randomUUID()))
                .isInstanceOf(PasProprietaireDuCouloirException.class);
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
        when(couloirService.obtenirCouloir(couloirId)).thenReturn(new Couloir("Ing1-SI EPISEN", proprietaireId));

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

    @Test
    void rattacherNotions_leve_une_exception_si_pas_proprietaire_du_couloir() {
        UUID proprietaireId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        Seance seance = new Seance("Cours 1", UUID.randomUUID(), couloirId);
        when(seanceRepository.findById(seance.getId())).thenReturn(Optional.of(seance));
        when(couloirService.obtenirCouloir(couloirId)).thenReturn(new Couloir("Ing1-SI EPISEN", proprietaireId));

        assertThatThrownBy(() -> seanceService.rattacherNotions(seance.getId(), List.of(UUID.randomUUID()), UUID.randomUUID()))
                .isInstanceOf(PasProprietaireDuCouloirException.class);
        verify(seanceNotionRepository, never()).deleteBySeanceId(any());
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

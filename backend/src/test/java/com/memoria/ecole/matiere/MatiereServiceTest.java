package com.memoria.ecole.matiere;

import com.memoria.core.couloir.CouloirService;
import com.memoria.core.couloir.PasMembreDuCouloirException;
import com.memoria.core.couloir.PasProprietaireDuCouloirException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class MatiereServiceTest {

    @Mock
    private MatiereRepository matiereRepository;

    @Mock
    private CouloirService couloirService;

    private MatiereService matiereService;

    @BeforeEach
    void setUp() {
        matiereService = new MatiereService(matiereRepository, couloirService);
    }

    @Test
    void creerMatiere_sauvegarde_la_matiere_si_proprietaire_du_couloir() {
        UUID proprietaireId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        when(matiereRepository.save(any(Matiere.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Matiere matiere = matiereService.creerMatiere("Mathematiques", couloirId, proprietaireId);

        assertThat(matiere.getNom()).isEqualTo("Mathematiques");
        assertThat(matiere.getCouloirId()).isEqualTo(couloirId);
        assertThat(matiere.getCreateurId()).isEqualTo(proprietaireId);
        verify(couloirService).verifierProprietaireDuCouloir(couloirId, proprietaireId);
    }

    @Test
    void creerMatiere_leve_une_exception_si_pas_proprietaire_du_couloir() {
        UUID couloirId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        doThrow(new PasProprietaireDuCouloirException(couloirId, utilisateurId))
                .when(couloirService).verifierProprietaireDuCouloir(couloirId, utilisateurId);

        assertThatThrownBy(() -> matiereService.creerMatiere("Mathematiques", couloirId, utilisateurId))
                .isInstanceOf(PasProprietaireDuCouloirException.class);
        verify(matiereRepository, never()).save(any());
    }

    @Test
    void obtenirMatiere_leve_une_exception_si_introuvable() {
        UUID id = UUID.randomUUID();
        when(matiereRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matiereService.obtenirMatiere(id))
                .isInstanceOf(MatiereNotFoundException.class);
    }

    @Test
    void listerMatieresParCouloir_retourne_les_matieres_du_couloir() {
        UUID couloirId = UUID.randomUUID();
        Matiere matiere = new Matiere("Mathematiques", couloirId, UUID.randomUUID());
        when(matiereRepository.findByCouloirId(couloirId)).thenReturn(List.of(matiere));

        List<Matiere> resultat = matiereService.listerMatieresParCouloir(couloirId);

        assertThat(resultat).containsExactly(matiere);
    }

    @Test
    void verifierMembreDuCouloir_ne_leve_rien_si_lutilisateur_est_membre() {
        UUID couloirId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Matiere matiere = new Matiere("Mathematiques", couloirId, UUID.randomUUID());
        when(matiereRepository.findById(matiere.getId())).thenReturn(Optional.of(matiere));
        when(couloirService.estMembre(couloirId, utilisateurId)).thenReturn(true);

        matiereService.verifierMembreDuCouloir(matiere.getId(), utilisateurId);
    }

    @Test
    void verifierMembreDuCouloir_leve_une_exception_si_lutilisateur_nest_pas_membre() {
        UUID couloirId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Matiere matiere = new Matiere("Mathematiques", couloirId, UUID.randomUUID());
        when(matiereRepository.findById(matiere.getId())).thenReturn(Optional.of(matiere));
        when(couloirService.estMembre(couloirId, utilisateurId)).thenReturn(false);

        assertThatThrownBy(() -> matiereService.verifierMembreDuCouloir(matiere.getId(), utilisateurId))
                .isInstanceOf(PasMembreDuCouloirException.class);
    }
}

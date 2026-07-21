package com.memoria.core.couloir;

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
class CouloirServiceTest {

    @Mock
    private CouloirRepository couloirRepository;

    @Mock
    private MembreCouloirRepository membreCouloirRepository;

    private CouloirService couloirService;

    @BeforeEach
    void setUp() {
        couloirService = new CouloirService(couloirRepository, membreCouloirRepository);
    }

    @Test
    void creerCouloir_sauvegarde_le_couloir_et_ajoute_le_proprietaire_comme_membre() {
        UUID proprietaireId = UUID.randomUUID();
        when(couloirRepository.save(any(Couloir.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Couloir couloir = couloirService.creerCouloir("Ing1-SI EPISEN", proprietaireId);

        assertThat(couloir.getNom()).isEqualTo("Ing1-SI EPISEN");
        assertThat(couloir.getProprietaireId()).isEqualTo(proprietaireId);

        ArgumentCaptor<MembreCouloir> captor = ArgumentCaptor.forClass(MembreCouloir.class);
        verify(membreCouloirRepository).save(captor.capture());
        assertThat(captor.getValue().getCouloirId()).isEqualTo(couloir.getId());
        assertThat(captor.getValue().getUtilisateurId()).isEqualTo(proprietaireId);
    }

    @Test
    void rejoindreCouloir_ajoute_un_nouveau_membre() {
        UUID couloirId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", UUID.randomUUID());
        when(couloirRepository.findById(couloirId)).thenReturn(Optional.of(couloir));
        when(membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloirId, utilisateurId)).thenReturn(false);

        Couloir resultat = couloirService.rejoindreCouloir(couloirId, utilisateurId);

        assertThat(resultat).isSameAs(couloir);
        verify(membreCouloirRepository).save(any(MembreCouloir.class));
    }

    @Test
    void rejoindreCouloir_est_idempotent_si_deja_membre() {
        UUID couloirId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", UUID.randomUUID());
        when(couloirRepository.findById(couloirId)).thenReturn(Optional.of(couloir));
        when(membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloirId, utilisateurId)).thenReturn(true);

        couloirService.rejoindreCouloir(couloirId, utilisateurId);

        verify(membreCouloirRepository, never()).save(any());
    }

    @Test
    void rejoindreCouloir_leve_une_exception_si_le_couloir_est_introuvable() {
        UUID couloirId = UUID.randomUUID();
        when(couloirRepository.findById(couloirId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couloirService.rejoindreCouloir(couloirId, UUID.randomUUID()))
                .isInstanceOf(CouloirNotFoundException.class);
    }

    @Test
    void listerMesCouloirs_retourne_les_couloirs_dont_lutilisateur_est_membre() {
        UUID utilisateurId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", UUID.randomUUID());
        MembreCouloir membre = new MembreCouloir(couloir.getId(), utilisateurId);
        when(membreCouloirRepository.findByUtilisateurId(utilisateurId)).thenReturn(List.of(membre));
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));

        List<Couloir> resultat = couloirService.listerMesCouloirs(utilisateurId);

        assertThat(resultat).containsExactly(couloir);
    }

    @Test
    void obtenirCouloir_leve_une_exception_si_introuvable() {
        UUID id = UUID.randomUUID();
        when(couloirRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couloirService.obtenirCouloir(id))
                .isInstanceOf(CouloirNotFoundException.class);
    }

    @Test
    void renommerCouloir_change_le_nom_si_proprietaire() {
        UUID proprietaireId = UUID.randomUUID();
        Couloir couloir = new Couloir("Ancien nom", proprietaireId);
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));
        when(couloirRepository.save(any(Couloir.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Couloir resultat = couloirService.renommerCouloir(couloir.getId(), "Nouveau nom", proprietaireId);

        assertThat(resultat.getNom()).isEqualTo("Nouveau nom");
    }

    @Test
    void renommerCouloir_leve_une_exception_si_pas_proprietaire() {
        UUID proprietaireId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", proprietaireId);
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));

        assertThatThrownBy(() -> couloirService.renommerCouloir(couloir.getId(), "Nouveau nom", UUID.randomUUID()))
                .isInstanceOf(PasProprietaireDuCouloirException.class);
    }

    @Test
    void supprimerCouloir_supprime_le_couloir_et_ses_membres_si_proprietaire() {
        UUID proprietaireId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", proprietaireId);
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));

        couloirService.supprimerCouloir(couloir.getId(), proprietaireId);

        verify(membreCouloirRepository).deleteByCouloirId(couloir.getId());
        verify(couloirRepository).deleteById(couloir.getId());
    }

    @Test
    void supprimerCouloir_leve_une_exception_si_pas_proprietaire() {
        UUID proprietaireId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", proprietaireId);
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));

        assertThatThrownBy(() -> couloirService.supprimerCouloir(couloir.getId(), UUID.randomUUID()))
                .isInstanceOf(PasProprietaireDuCouloirException.class);
        verify(couloirRepository, never()).deleteById(any());
    }

    @Test
    void retirerMembre_supprime_ladhesion_si_proprietaire() {
        UUID proprietaireId = UUID.randomUUID();
        UUID membreId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", proprietaireId);
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));

        couloirService.retirerMembre(couloir.getId(), membreId, proprietaireId);

        verify(membreCouloirRepository).deleteByCouloirIdAndUtilisateurId(couloir.getId(), membreId);
    }

    @Test
    void retirerMembre_leve_une_exception_si_pas_proprietaire() {
        UUID proprietaireId = UUID.randomUUID();
        UUID membreId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", proprietaireId);
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));

        assertThatThrownBy(() -> couloirService.retirerMembre(couloir.getId(), membreId, UUID.randomUUID()))
                .isInstanceOf(PasProprietaireDuCouloirException.class);
    }

    @Test
    void retirerMembre_leve_une_exception_si_on_tente_de_retirer_le_proprietaire() {
        UUID proprietaireId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", proprietaireId);
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));

        assertThatThrownBy(() -> couloirService.retirerMembre(couloir.getId(), proprietaireId, proprietaireId))
                .isInstanceOf(ProprietaireNePeutPasSeRetirerException.class);
        verify(membreCouloirRepository, never()).deleteByCouloirIdAndUtilisateurId(any(), any());
    }

    @Test
    void quitterCouloir_supprime_ladhesion_si_membre_non_proprietaire() {
        UUID proprietaireId = UUID.randomUUID();
        UUID membreId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", proprietaireId);
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));

        couloirService.quitterCouloir(couloir.getId(), membreId);

        verify(membreCouloirRepository).deleteByCouloirIdAndUtilisateurId(couloir.getId(), membreId);
    }

    @Test
    void quitterCouloir_leve_une_exception_si_le_proprietaire_tente_de_quitter() {
        UUID proprietaireId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", proprietaireId);
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));

        assertThatThrownBy(() -> couloirService.quitterCouloir(couloir.getId(), proprietaireId))
                .isInstanceOf(ProprietaireNePeutPasSeRetirerException.class);
        verify(membreCouloirRepository, never()).deleteByCouloirIdAndUtilisateurId(any(), any());
    }

    @Test
    void listerMembres_retourne_les_membres_du_couloir() {
        UUID couloirId = UUID.randomUUID();
        MembreCouloir membre = new MembreCouloir(couloirId, UUID.randomUUID());
        when(membreCouloirRepository.findByCouloirId(couloirId)).thenReturn(List.of(membre));

        List<MembreCouloir> resultat = couloirService.listerMembres(couloirId);

        assertThat(resultat).containsExactly(membre);
    }
}

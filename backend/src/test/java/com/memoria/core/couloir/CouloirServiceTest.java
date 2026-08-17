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
    void renommerCouloir_change_le_nom_si_membre() {
        UUID createurId = UUID.randomUUID();
        Couloir couloir = new Couloir("Ancien nom", createurId);
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));
        when(membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloir.getId(), createurId)).thenReturn(true);
        when(couloirRepository.save(any(Couloir.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Couloir resultat = couloirService.renommerCouloir(couloir.getId(), "Nouveau nom", createurId);

        assertThat(resultat.getNom()).isEqualTo("Nouveau nom");
    }

    @Test
    void renommerCouloir_leve_une_exception_si_pas_membre() {
        UUID createurId = UUID.randomUUID();
        UUID autreUtilisateurId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", createurId);
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));
        when(membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloir.getId(), autreUtilisateurId)).thenReturn(false);

        assertThatThrownBy(() -> couloirService.renommerCouloir(couloir.getId(), "Nouveau nom", autreUtilisateurId))
                .isInstanceOf(PasMembreDuCouloirException.class);
    }

    @Test
    void supprimerCouloir_supprime_le_couloir_et_ses_membres_si_membre() {
        UUID createurId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", createurId);
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));
        when(membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloir.getId(), createurId)).thenReturn(true);

        couloirService.supprimerCouloir(couloir.getId(), createurId);

        verify(membreCouloirRepository).deleteByCouloirId(couloir.getId());
        verify(couloirRepository).deleteById(couloir.getId());
    }

    @Test
    void supprimerCouloir_leve_une_exception_si_pas_membre() {
        UUID createurId = UUID.randomUUID();
        UUID autreUtilisateurId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", createurId);
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));
        when(membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloir.getId(), autreUtilisateurId)).thenReturn(false);

        assertThatThrownBy(() -> couloirService.supprimerCouloir(couloir.getId(), autreUtilisateurId))
                .isInstanceOf(PasMembreDuCouloirException.class);
        verify(couloirRepository, never()).deleteById(any());
    }

    @Test
    void retirerMembre_supprime_ladhesion_si_lappelant_est_membre() {
        UUID createurId = UUID.randomUUID();
        UUID membreId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", createurId);
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));
        when(membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloir.getId(), createurId)).thenReturn(true);

        couloirService.retirerMembre(couloir.getId(), membreId, createurId);

        verify(membreCouloirRepository).deleteByCouloirIdAndUtilisateurId(couloir.getId(), membreId);
    }

    @Test
    void retirerMembre_leve_une_exception_si_lappelant_nest_pas_membre() {
        UUID createurId = UUID.randomUUID();
        UUID membreId = UUID.randomUUID();
        UUID autreUtilisateurId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", createurId);
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));
        when(membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloir.getId(), autreUtilisateurId)).thenReturn(false);

        assertThatThrownBy(() -> couloirService.retirerMembre(couloir.getId(), membreId, autreUtilisateurId))
                .isInstanceOf(PasMembreDuCouloirException.class);
    }

    // Egalite totale entre membres : aucun statut protege, y compris celui
    // qui a cree le couloir (voir Couloir.proprietaireId, pure metadonnee).
    @Test
    void retirerMembre_peut_retirer_le_createur_du_couloir() {
        UUID createurId = UUID.randomUUID();
        UUID autreMembreId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", createurId);
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));
        when(membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloir.getId(), autreMembreId)).thenReturn(true);

        couloirService.retirerMembre(couloir.getId(), createurId, autreMembreId);

        verify(membreCouloirRepository).deleteByCouloirIdAndUtilisateurId(couloir.getId(), createurId);
    }

    @Test
    void quitterCouloir_supprime_ladhesion() {
        UUID couloirId = UUID.randomUUID();
        UUID membreId = UUID.randomUUID();

        couloirService.quitterCouloir(couloirId, membreId);

        verify(membreCouloirRepository).deleteByCouloirIdAndUtilisateurId(couloirId, membreId);
    }

    @Test
    void listerMembres_retourne_les_membres_du_couloir() {
        UUID couloirId = UUID.randomUUID();
        MembreCouloir membre = new MembreCouloir(couloirId, UUID.randomUUID());
        when(membreCouloirRepository.findByCouloirId(couloirId)).thenReturn(List.of(membre));

        List<MembreCouloir> resultat = couloirService.listerMembres(couloirId);

        assertThat(resultat).containsExactly(membre);
    }

    // Point unique de verification reutilise par les services Ecole
    // (Matiere/Notion/Seance/NotionCandidate/DocumentMatiere) -- voir audit
    // du 2026-07-27, ces services dupliquaient chacun la meme logique avant.
    @Test
    void verifierMembre_ne_leve_rien_si_membre() {
        UUID utilisateurId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", UUID.randomUUID());
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));
        when(membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloir.getId(), utilisateurId)).thenReturn(true);

        couloirService.verifierMembre(couloir.getId(), utilisateurId);
    }

    @Test
    void verifierMembre_leve_une_exception_si_pas_membre() {
        UUID utilisateurId = UUID.randomUUID();
        Couloir couloir = new Couloir("Classe", UUID.randomUUID());
        when(couloirRepository.findById(couloir.getId())).thenReturn(Optional.of(couloir));
        when(membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloir.getId(), utilisateurId)).thenReturn(false);

        assertThatThrownBy(() -> couloirService.verifierMembre(couloir.getId(), utilisateurId))
                .isInstanceOf(PasMembreDuCouloirException.class);
    }
}

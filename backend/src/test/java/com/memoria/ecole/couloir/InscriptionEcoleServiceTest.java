package com.memoria.ecole.couloir;

import com.memoria.core.auth.AuthResponse;
import com.memoria.core.auth.AuthService;
import com.memoria.core.auth.ModuleMemoria;
import com.memoria.core.couloir.CouloirService;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InscriptionEcoleServiceTest {

    @Mock
    private AuthService authService;

    @Mock
    private CouloirService couloirService;

    @Mock
    private ContexteScolaireCouloirRepository contexteScolaireCouloirRepository;

    private InscriptionEcoleService inscriptionEcoleService;

    @BeforeEach
    void setUp() {
        inscriptionEcoleService = new InscriptionEcoleService(authService, couloirService, contexteScolaireCouloirRepository);
    }

    @Test
    void inscrire_resout_le_couloir_et_le_rejoint_automatiquement() {
        UUID couloirId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        ContexteScolaireCouloir contexte = new ContexteScolaireCouloir(couloirId, "2026-2027", "Informatique", "Genie Logiciel");
        AuthResponse reponseAttendue = new AuthResponse("token", utilisateurId, "etu@test.local", ModuleMemoria.ECOLE, false);
        when(contexteScolaireCouloirRepository.findByAnneeAcademiqueAndFiliereAndSpecialite(
                "2026-2027", "Informatique", "Genie Logiciel")).thenReturn(Optional.of(contexte));
        when(authService.inscrire("etu@test.local", "motdepasse123", ModuleMemoria.ECOLE)).thenReturn(reponseAttendue);

        AuthResponse resultat = inscriptionEcoleService.inscrire("etu@test.local", "motdepasse123", "2026-2027", "Informatique", "Genie Logiciel");

        assertThat(resultat).isEqualTo(reponseAttendue);
        verify(couloirService).rejoindreCouloir(couloirId, utilisateurId);
    }

    @Test
    void inscrire_normalise_une_specialite_vide_en_null_pour_la_recherche() {
        UUID couloirId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        ContexteScolaireCouloir contexte = new ContexteScolaireCouloir(couloirId, "2026-2027", "Droit", null);
        when(contexteScolaireCouloirRepository.findByAnneeAcademiqueAndFiliereAndSpecialite(
                eq("2026-2027"), eq("Droit"), isNull())).thenReturn(Optional.of(contexte));
        when(authService.inscrire("etu@test.local", "motdepasse123", ModuleMemoria.ECOLE))
                .thenReturn(new AuthResponse("token", utilisateurId, "etu@test.local", ModuleMemoria.ECOLE, false));

        inscriptionEcoleService.inscrire("etu@test.local", "motdepasse123", "2026-2027", "Droit", "");

        verify(couloirService).rejoindreCouloir(couloirId, utilisateurId);
    }

    @Test
    void inscrire_leve_une_exception_si_la_classe_est_introuvable() {
        when(contexteScolaireCouloirRepository.findByAnneeAcademiqueAndFiliereAndSpecialite(
                "2026-2027", "Informatique", "Genie Logiciel")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inscriptionEcoleService.inscrire(
                "etu@test.local", "motdepasse123", "2026-2027", "Informatique", "Genie Logiciel"))
                .isInstanceOf(ClasseIntrouvableException.class);
        verify(authService, never()).inscrire(anyString(), anyString(), any(ModuleMemoria.class));
    }

    @Test
    void listerOptionsInscription_retourne_les_triplets_disponibles() {
        UUID couloir1 = UUID.randomUUID();
        UUID couloir2 = UUID.randomUUID();
        when(contexteScolaireCouloirRepository.findAll()).thenReturn(List.of(
                new ContexteScolaireCouloir(couloir1, "2026-2027", "Informatique", "Genie Logiciel"),
                new ContexteScolaireCouloir(couloir2, "2026-2027", "Droit", null)
        ));

        List<OptionInscriptionResponse> resultat = inscriptionEcoleService.listerOptionsInscription();

        assertThat(resultat).containsExactlyInAnyOrder(
                new OptionInscriptionResponse("2026-2027", "Informatique", "Genie Logiciel"),
                new OptionInscriptionResponse("2026-2027", "Droit", null)
        );
    }
}

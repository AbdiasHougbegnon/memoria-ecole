package com.memoria.core.locuteur;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentificateurLocuteurSpeechBrainTest {

    // Port volontairement ferme (rien n'ecoute) : verifie la degradation
    // gracieuse sans dependre d'un vrai speaker-service en cours d'execution.
    private static final String URL_INJOIGNABLE = "http://127.0.0.1:1";

    @Test
    void identifier_renvoie_aucun_match_si_le_service_est_injoignable() {
        IdentificateurLocuteurSpeechBrain client = new IdentificateurLocuteurSpeechBrain(URL_INJOIGNABLE);

        ResultatIdentification resultat = client.identifier(new byte[]{1, 2, 3}, List.of("profil-A"));

        assertThat(resultat).isEqualTo(ResultatIdentification.aucunMatch());
    }

    @Test
    void identifier_renvoie_aucun_match_sans_appel_si_aucun_profil_candidat() {
        IdentificateurLocuteurSpeechBrain client = new IdentificateurLocuteurSpeechBrain(URL_INJOIGNABLE);

        ResultatIdentification resultat = client.identifier(new byte[]{1, 2, 3}, List.of());

        assertThat(resultat).isEqualTo(ResultatIdentification.aucunMatch());
    }

    @Test
    void enroller_leve_une_exception_si_le_service_est_injoignable() {
        IdentificateurLocuteurSpeechBrain client = new IdentificateurLocuteurSpeechBrain(URL_INJOIGNABLE);

        assertThatThrownBy(() -> client.enroller(new byte[]{1, 2, 3}))
                .isInstanceOf(IdentificationLocuteurException.class);
    }

    @Test
    void supprimerProfil_leve_une_exception_si_le_service_est_injoignable() {
        IdentificateurLocuteurSpeechBrain client = new IdentificateurLocuteurSpeechBrain(URL_INJOIGNABLE);

        assertThatThrownBy(() -> client.supprimerProfil("profil-123"))
                .isInstanceOf(IdentificationLocuteurException.class);
    }
}

package com.memoria.core.locuteur;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentificateurLocuteurAzureSpeechTest {

    @Test
    void identifier_renvoie_aucun_match_quand_azure_nest_pas_configure() {
        IdentificateurLocuteurAzureSpeech client = new IdentificateurLocuteurAzureSpeech("", "", "fr-FR");

        ResultatIdentification resultat = client.identifier(new byte[]{1, 2, 3}, List.of("profil-A"));

        assertThat(resultat.profilExterneIdReconnu()).isNull();
        assertThat(resultat.confiance()).isZero();
    }

    @Test
    void identifier_renvoie_aucun_match_sans_appel_si_aucun_profil_candidat() {
        IdentificateurLocuteurAzureSpeech client = new IdentificateurLocuteurAzureSpeech("cle", "region", "fr-FR");

        ResultatIdentification resultat = client.identifier(new byte[]{1, 2, 3}, List.of());

        assertThat(resultat).isEqualTo(ResultatIdentification.aucunMatch());
    }

    @Test
    void enroller_leve_une_exception_quand_azure_nest_pas_configure() {
        IdentificateurLocuteurAzureSpeech client = new IdentificateurLocuteurAzureSpeech("", "", "fr-FR");

        assertThatThrownBy(() -> client.enroller(new byte[]{1, 2, 3}))
                .isInstanceOf(IdentificationLocuteurException.class);
    }

    @Test
    void supprimerProfil_leve_une_exception_quand_azure_nest_pas_configure() {
        IdentificateurLocuteurAzureSpeech client = new IdentificateurLocuteurAzureSpeech("", "", "fr-FR");

        assertThatThrownBy(() -> client.supprimerProfil("profil-123"))
                .isInstanceOf(IdentificationLocuteurException.class);
    }
}

package com.memoria.core.cout;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

// SimpleMeterRegistry (fournie par micrometer-core, deja sur le classpath
// via micrometer-registry-prometheus) plutot qu'un mock : on veut inspecter
// de vraies valeurs de compteurs, pas seulement verifier des appels.
class CoutAzureServiceTest {

    @Test
    void enregistrerAppel_incremente_le_compteur_dappels_et_de_cout_par_service() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CoutAzureService service = new CoutAzureService(registry, 100.0, false, 0.01);

        service.enregistrerAppel(ServiceAzure.OPENAI_CHAT, 0.05);
        service.enregistrerAppel(ServiceAzure.OPENAI_CHAT, 0.03);
        service.enregistrerAppel(ServiceAzure.SPEECH_STT, 0.02);

        assertThat(registry.get("memoria.azure.appels.total").tag("service", "OPENAI_CHAT").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("memoria.azure.cout.euros.total").tag("service", "OPENAI_CHAT").counter().count()).isEqualTo(0.08);
        assertThat(registry.get("memoria.azure.appels.total").tag("service", "SPEECH_STT").counter().count()).isEqualTo(1.0);
    }

    @Test
    void enregistrerAppel_cumule_le_cout_mensuel_dans_la_jauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CoutAzureService service = new CoutAzureService(registry, 100.0, false, 0.01);

        service.enregistrerAppel(ServiceAzure.OPENAI_CHAT, 1.5);
        service.enregistrerAppel(ServiceAzure.SPEECH_TTS, 2.5);

        assertThat(service.coutMensuelActuelEuros()).isEqualTo(4.0);
        assertThat(registry.get("memoria.azure.cout.mensuel.euros").gauge().value()).isEqualTo(4.0);
    }

    @Test
    void enregistrerAppel_ne_bloque_pas_par_defaut_meme_au_dela_du_budget() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CoutAzureService service = new CoutAzureService(registry, 1.0, false, 0.01);

        assertThatCode(() -> service.enregistrerAppel(ServiceAzure.OPENAI_CHAT, 5.0)).doesNotThrowAnyException();
        assertThat(service.coutMensuelActuelEuros()).isEqualTo(5.0);
    }

    @Test
    void enregistrerAppel_leve_une_exception_en_mode_strict_au_dela_du_budget() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CoutAzureService service = new CoutAzureService(registry, 1.0, true, 0.01);

        assertThatThrownBy(() -> service.enregistrerAppel(ServiceAzure.OPENAI_CHAT, 5.0))
                .isInstanceOf(BudgetAzureDepasseException.class);
    }

    @Test
    void enregistrerAppel_en_mode_strict_najoute_pas_dexception_avant_le_budget() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CoutAzureService service = new CoutAzureService(registry, 100.0, true, 0.01);

        assertThatCode(() -> service.enregistrerAppel(ServiceAzure.OPENAI_CHAT, 5.0)).doesNotThrowAnyException();
    }

    @Test
    void le_budget_mensuel_configure_est_expose_comme_jauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new CoutAzureService(registry, 42.0, false, 0.01);

        assertThat(registry.get("memoria.azure.budget.mensuel.euros").gauge().value()).isEqualTo(42.0);
    }

    @Test
    void coutForfaitaireEuros_retourne_la_valeur_configuree() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CoutAzureService service = new CoutAzureService(registry, 100.0, false, 0.07);

        assertThat(service.coutForfaitaireEuros()).isEqualTo(0.07);
    }
}

package com.memoria.core.cout;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;

// Instrumentation centralisee des appels Azure IA -- brique "Maitrise des
// couts Azure" du master prompt. "Quotas par tenant" se traduit ici par un
// budget global de l'instance : le modele de deploiement du projet est une
// instance dediee par client (pas de multi-tenant interne), donc l'instance
// EST le tenant -- pas besoin d'une nouvelle entite Tenant pour honorer ce
// principe. Doctrine "outil d'aide, pas de surveillance" (deja appliquee
// ailleurs dans ce projet) : le depassement de budget est toujours logue,
// mais ne bloque aucun appel sauf activation explicite du mode strict.
@Service
public class CoutAzureService {

    private static final Logger LOG = LoggerFactory.getLogger(CoutAzureService.class);

    private final MeterRegistry meterRegistry;
    private final double budgetMensuelEuros;
    private final boolean modeStrict;
    private final double coutForfaitaireEuros;

    // Remis a zero au changement de mois calendaire, et au redemarrage de
    // l'appli (pas persiste) -- limite assumee, voir
    // docs/phases/phase-11-maitrise-couts-azure.md. Sert uniquement a la
    // decision de budget : les compteurs Micrometer ci-dessous restent
    // cumulatifs pour toujours, Grafana calcule la fenetre "ce mois-ci" via
    // increase(...) plutot que l'application ne gere elle-meme un reset.
    private final AtomicReference<YearMonth> moisCourant = new AtomicReference<>(YearMonth.now());
    private final DoubleAdder coutMensuelEuros = new DoubleAdder();

    public CoutAzureService(
            MeterRegistry meterRegistry,
            @Value("${memoria.cout.azure.budget-mensuel-euros:100}") double budgetMensuelEuros,
            @Value("${memoria.cout.azure.mode-strict:false}") boolean modeStrict,
            @Value("${memoria.cout.azure.forfait-par-appel-euros:0.01}") double coutForfaitaireEuros
    ) {
        this.meterRegistry = meterRegistry;
        this.budgetMensuelEuros = budgetMensuelEuros;
        this.modeStrict = modeStrict;
        this.coutForfaitaireEuros = coutForfaitaireEuros;

        Gauge.builder("memoria.azure.cout.mensuel.euros", this, CoutAzureService::coutMensuelActuelEuros)
                .description("Cout Azure IA estime pour le mois calendaire en cours (remis a zero au changement de mois et au redemarrage)")
                .register(meterRegistry);
        // Valeur constante exposee comme jauge (pas juste une propriete de
        // config) pour que Grafana affiche "cout actuel / budget" sans
        // dupliquer la valeur a la main dans le dashboard.
        Gauge.builder("memoria.azure.budget.mensuel.euros", () -> budgetMensuelEuros)
                .description("Budget Azure IA mensuel configure")
                .register(meterRegistry);
    }

    public void enregistrerAppel(ServiceAzure service, double coutEstimeEuros) {
        reinitialiserSiNouveauMois();

        Counter.builder("memoria.azure.appels.total")
                .description("Nombre d'appels vers un service Azure IA")
                .tag("service", service.name())
                .register(meterRegistry)
                .increment();

        Counter.builder("memoria.azure.cout.euros.total")
                .description("Cout Azure IA estime, cumule depuis le demarrage")
                .tag("service", service.name())
                .register(meterRegistry)
                .increment(coutEstimeEuros);

        coutMensuelEuros.add(coutEstimeEuros);

        double coutActuel = coutMensuelEuros.sum();
        if (coutActuel >= budgetMensuelEuros) {
            LOG.warn(
                    "Budget Azure mensuel depasse : {} euros estimes >= budget de {} euros (mode strict = {})",
                    coutActuel, budgetMensuelEuros, modeStrict
            );
            if (modeStrict) {
                throw new BudgetAzureDepasseException(coutActuel, budgetMensuelEuros);
            }
        }
    }

    public double coutForfaitaireEuros() {
        return coutForfaitaireEuros;
    }

    public double coutMensuelActuelEuros() {
        return coutMensuelEuros.sum();
    }

    private void reinitialiserSiNouveauMois() {
        YearMonth maintenant = YearMonth.now();
        YearMonth precedent = moisCourant.getAndUpdate(mois -> maintenant);
        if (!precedent.equals(maintenant)) {
            coutMensuelEuros.reset();
        }
    }
}

package com.memoria.core.cout;

// Levee uniquement si memoria.cout.azure.mode-strict=true (defaut : false,
// jamais bloquant) et que le budget mensuel est deja depasse. L'appelant
// degrade gracieusement, comme les autres cas d'indisponibilite Azure deja
// geres dans ce projet.
public class BudgetAzureDepasseException extends RuntimeException {

    public BudgetAzureDepasseException(double coutMensuelEuros, double budgetMensuelEuros) {
        super("Budget Azure mensuel depasse : " + coutMensuelEuros + " euros >= " + budgetMensuelEuros + " euros");
    }
}

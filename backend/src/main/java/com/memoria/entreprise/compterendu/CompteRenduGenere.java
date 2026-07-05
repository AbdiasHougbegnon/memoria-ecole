package com.memoria.entreprise.compterendu;

import java.util.List;

public record CompteRenduGenere(String synthese, List<String> decisions, List<ActionExtraite> actions) {
}

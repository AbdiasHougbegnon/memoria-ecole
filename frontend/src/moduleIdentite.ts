import type { ModuleMemoria } from './types'

// Une seule identite de marque (indigo/creme, cf. index.css) pour les deux
// modules -- suit le style du prototype (docs/maquette-initiale.html), qui
// distingue Ecole/Entreprise uniquement par le contenu de la navigation,
// jamais par la couleur.
export const IDENTITES_MODULE: Record<ModuleMemoria, {
  nomProduit: string
  descriptif: string
  libelleCouloirs: string
}> = {
  ENTREPRISE: {
    nomProduit: 'Memoria Entreprise',
    descriptif: 'Comptes rendus, engagements et decisions de vos reunions.',
    libelleCouloirs: "Couloirs d'equipe",
  },
  ECOLE: {
    nomProduit: 'Memoria Ecole',
    descriptif: 'Resumes de cours, notions et couloirs de classe.',
    libelleCouloirs: 'Couloirs de classe',
  },
}

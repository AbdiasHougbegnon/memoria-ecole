import type { ModuleMemoria } from './types'

// Table centralisee (au lieu de dupliquer libelles/couleurs dans ChoixModulePage,
// LoginPage et Layout) : une incoherence de couleur entre l'ecran de choix et
// l'app serait un bug produit visible, pas une simple divergence de libelle.
export const IDENTITES_MODULE: Record<ModuleMemoria, {
  nomProduit: string
  descriptif: string
  classeAccent: string
  classeBouton: string
}> = {
  ENTREPRISE: {
    nomProduit: 'Memoria Entreprise',
    descriptif: 'Comptes rendus, engagements et decisions de vos reunions.',
    classeAccent: 'text-indigo-700',
    classeBouton: 'bg-indigo-700 hover:bg-indigo-800',
  },
  ECOLE: {
    nomProduit: 'Memoria Ecole',
    descriptif: 'Resumes de cours, notions et couloirs de classe.',
    classeAccent: 'text-emerald-700',
    classeBouton: 'bg-emerald-700 hover:bg-emerald-800',
  },
}

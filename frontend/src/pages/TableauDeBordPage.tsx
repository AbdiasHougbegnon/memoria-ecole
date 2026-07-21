import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { obtenirTableauDeBordEntreprise } from '../api'
import type { StatutEngagement, TableauDeBordEntreprise } from '../types'

const ORDRE_STATUTS: StatutEngagement[] = ['EN_ATTENTE', 'CONFIRME', 'TERMINE', 'REJETE']

const LIBELLE_STATUT: Record<StatutEngagement, string> = {
  EN_ATTENTE: 'A confirmer',
  CONFIRME: 'Confirme',
  REJETE: 'Rejete',
  TERMINE: 'Termine',
}

// Memes teintes que les badges de statut sur la page Engagements (coherence
// categorielle dans toute l'appli), en remplissage plein -500 pour rester
// dans la bande de luminosite OKLCH validee (L 0.43-0.77) plutot que le -400
// trop clair des badges. Toujours accompagne d'un libelle visible (legende) :
// la regle de secours pour un contraste sous 3:1 sur fond clair.
const COULEUR_BARRE: Record<StatutEngagement, string> = {
  EN_ATTENTE: 'bg-amber-500',
  CONFIRME: 'bg-blue-500',
  TERMINE: 'bg-green-500',
  REJETE: 'bg-slate-400',
}

function StatTuile({ label, valeur }: { label: string; valeur: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-3xl font-semibold text-slate-900">{valeur}</p>
    </div>
  )
}

export function TableauDeBordPage() {
  const [donnees, setDonnees] = useState<TableauDeBordEntreprise | null>(null)
  const [chargement, setChargement] = useState(true)

  useEffect(() => {
    obtenirTableauDeBordEntreprise()
      .then(setDonnees)
      .finally(() => setChargement(false))
  }, [])

  if (chargement) {
    return <p className="p-6 text-center text-sm text-slate-500">Chargement...</p>
  }
  if (!donnees) {
    return <p className="p-6 text-center text-sm text-red-600">Impossible de charger le tableau de bord.</p>
  }

  const total = donnees.total
  const segments = ORDRE_STATUTS.map((statut) => ({
    statut,
    valeur: donnees.parStatut[statut] ?? 0,
  })).filter((s) => s.valeur > 0)

  return (
    <div className="mx-auto max-w-2xl px-4 py-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900">Tableau de bord</h1>
        <Link to="/engagements" className="text-sm text-slate-500 hover:text-slate-700">
          Voir les engagements
        </Link>
      </div>

      <div className="mb-8 grid grid-cols-3 gap-3">
        <StatTuile label="Engagements" valeur={String(total)} />
        <StatTuile label="Taux de completion" valeur={`${Math.round(donnees.tauxCompletion * 100)}%`} />
        <StatTuile label="En retard" valeur={String(donnees.enRetard)} />
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-4">
        <h2 className="mb-3 text-sm font-medium uppercase tracking-wide text-slate-500">Repartition par statut</h2>

        {total === 0 ? (
          <p className="text-sm text-slate-500">Aucun engagement pour le moment.</p>
        ) : (
          <>
            <div className="flex h-6 gap-[2px] overflow-hidden rounded-md">
              {segments.map(({ statut, valeur }) => (
                <div
                  key={statut}
                  className={COULEUR_BARRE[statut]}
                  style={{ width: `${(valeur / total) * 100}%` }}
                  title={`${LIBELLE_STATUT[statut]} : ${valeur}`}
                />
              ))}
            </div>

            <ul className="mt-4 flex flex-wrap gap-x-5 gap-y-2">
              {segments.map(({ statut, valeur }) => (
                <li key={statut} className="flex items-center gap-2 text-sm text-slate-600">
                  <span className={`h-3 w-3 rounded-sm ${COULEUR_BARRE[statut]}`} />
                  <span>
                    {LIBELLE_STATUT[statut]} <span className="text-slate-400">({valeur})</span>
                  </span>
                </li>
              ))}
            </ul>
          </>
        )}
      </div>

      <p className="mt-6 text-xs text-slate-400">
        Vue d'ensemble uniquement -- pour le detail par personne, chacun gere ses propres engagements.
      </p>
    </div>
  )
}

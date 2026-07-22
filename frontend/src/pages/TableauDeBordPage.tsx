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
// categorielle dans toute l'appli).
const COULEUR_BARRE: Record<StatutEngagement, string> = {
  EN_ATTENTE: 'var(--color-warn)',
  CONFIRME: 'var(--color-brand)',
  TERMINE: 'var(--color-ok)',
  REJETE: '#D6D0C6',
}

function StatTuile({ label, valeur }: { label: string; valeur: string }) {
  return (
    <div className="rounded-2xl border bg-white p-5" style={{ borderColor: 'var(--color-border-soft)' }}>
      <p className="text-xs font-bold uppercase tracking-wide" style={{ color: 'var(--color-ink-faint-2)' }}>{label}</p>
      <p className="mt-1.5 text-3xl font-bold tracking-tight">{valeur}</p>
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
    return <p className="p-6 text-center text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>
  }
  if (!donnees) {
    return <p className="p-6 text-center text-sm" style={{ color: '#B02631' }}>Impossible de charger le tableau de bord.</p>
  }

  const total = donnees.total
  const segments = ORDRE_STATUTS.map((statut) => ({
    statut,
    valeur: donnees.parStatut[statut] ?? 0,
  })).filter((s) => s.valeur > 0)

  return (
    <div className="mx-auto max-w-2xl px-8 py-10">
      <div className="mb-1 flex items-center justify-between">
        <h1 className="text-[28px] font-bold tracking-tight">Tableau de bord</h1>
        <Link to="/engagements" className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Voir les engagements</Link>
      </div>
      <p className="mb-7 text-sm" style={{ color: 'var(--color-ink-muted)' }}>
        Vue d'ensemble des engagements de l'equipe.
      </p>

      <div className="mb-6 grid grid-cols-3 gap-3">
        <StatTuile label="Engagements" valeur={String(total)} />
        <StatTuile label="Taux de completion" valeur={`${Math.round(donnees.tauxCompletion * 100)}%`} />
        <StatTuile label="En retard" valeur={String(donnees.enRetard)} />
      </div>

      <div className="rounded-2xl border bg-white p-5" style={{ borderColor: 'var(--color-border-soft)' }}>
        <h2 className="mb-3 text-xs font-bold uppercase tracking-wide" style={{ color: 'var(--color-ink-faint-2)' }}>Repartition par statut</h2>

        {total === 0 ? (
          <p className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Aucun engagement pour le moment.</p>
        ) : (
          <>
            <div className="flex h-6 gap-[2px] overflow-hidden rounded-md">
              {segments.map(({ statut, valeur }) => (
                <div
                  key={statut}
                  style={{ background: COULEUR_BARRE[statut], width: `${(valeur / total) * 100}%` }}
                  title={`${LIBELLE_STATUT[statut]} : ${valeur}`}
                />
              ))}
            </div>

            <ul className="mt-4 flex flex-wrap gap-x-5 gap-y-2">
              {segments.map(({ statut, valeur }) => (
                <li key={statut} className="flex items-center gap-2 text-sm">
                  <span className="h-3 w-3 rounded-sm" style={{ background: COULEUR_BARRE[statut] }} />
                  <span>
                    {LIBELLE_STATUT[statut]} <span style={{ color: 'var(--color-ink-faint)' }}>({valeur})</span>
                  </span>
                </li>
              ))}
            </ul>
          </>
        )}
      </div>

      <p className="mt-6 text-xs" style={{ color: 'var(--color-ink-faint)' }}>
        Vue d'ensemble uniquement -- pour le detail par personne, chacun gere ses propres engagements.
      </p>
    </div>
  )
}

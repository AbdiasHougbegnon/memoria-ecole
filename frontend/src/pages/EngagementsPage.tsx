import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { confirmerEngagement, listerEngagements, rejeterEngagement, terminerEngagement } from '../api'
import type { Engagement, StatutEngagement } from '../types'

const LIBELLE_STATUT: Record<StatutEngagement, string> = {
  EN_ATTENTE: 'A confirmer',
  CONFIRME: 'Confirme',
  REJETE: 'Rejete',
  TERMINE: 'Termine',
}

const COULEUR_STATUT: Record<StatutEngagement, string> = {
  EN_ATTENTE: 'bg-amber-100 text-amber-700',
  CONFIRME: 'bg-blue-100 text-blue-700',
  REJETE: 'bg-slate-200 text-slate-500',
  TERMINE: 'bg-green-100 text-green-700',
}

const FILTRES: { valeur: StatutEngagement | null; libelle: string }[] = [
  { valeur: null, libelle: 'Tous' },
  { valeur: 'EN_ATTENTE', libelle: 'A confirmer' },
  { valeur: 'CONFIRME', libelle: 'Confirmes' },
  { valeur: 'TERMINE', libelle: 'Termines' },
  { valeur: 'REJETE', libelle: 'Rejetes' },
]

export function EngagementsPage() {
  const [engagements, setEngagements] = useState<Engagement[]>([])
  const [chargement, setChargement] = useState(true)
  const [filtre, setFiltre] = useState<StatutEngagement | null>(null)
  const [enCours, setEnCours] = useState<string | null>(null)

  async function rafraichir() {
    setChargement(true)
    try {
      setEngagements(await listerEngagements(filtre ?? undefined))
    } finally {
      setChargement(false)
    }
  }

  useEffect(() => {
    void rafraichir()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filtre])

  async function agir(id: string, action: (id: string) => Promise<Engagement>) {
    setEnCours(id)
    try {
      const misAJour = await action(id)
      setEngagements((precedent) => precedent.map((e) => (e.id === id ? misAJour : e)))
    } finally {
      setEnCours(null)
    }
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900">Engagements</h1>
        <Link to="/" className="text-sm text-slate-500 hover:text-slate-700">
          Retour aux sessions
        </Link>
      </div>

      <div className="mb-4 flex flex-wrap gap-2">
        {FILTRES.map(({ valeur, libelle }) => (
          <button
            key={libelle}
            onClick={() => setFiltre(valeur)}
            className={`rounded-lg px-3 py-1 text-sm ${
              filtre === valeur ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            {libelle}
          </button>
        ))}
      </div>

      {chargement && <p className="text-sm text-slate-500">Chargement...</p>}
      {!chargement && engagements.length === 0 && (
        <p className="text-sm text-slate-500">
          Aucun engagement pour le moment. Ils sont extraits automatiquement quand tu generes le compte
          rendu complet d'une session.
        </p>
      )}

      <ul className="flex flex-col gap-3">
        {engagements.map((engagement) => (
          <li key={engagement.id} className="rounded-lg border border-slate-200 bg-white px-4 py-3">
            <div className="mb-1 flex items-start justify-between gap-2">
              <p className="text-sm text-slate-800">{engagement.description}</p>
              <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${COULEUR_STATUT[engagement.statut]}`}>
                {LIBELLE_STATUT[engagement.statut]}
              </span>
            </div>
            <div className="mb-2 flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-500">
              <Link to={`/sessions/${engagement.sessionId}`} className="hover:underline">
                {engagement.sessionTitre}
              </Link>
              {engagement.responsable && <span>Responsable : {engagement.responsable}</span>}
              {engagement.echeance && <span>Echeance : {engagement.echeance}</span>}
            </div>

            <div className="flex gap-2">
              {engagement.statut === 'EN_ATTENTE' && (
                <>
                  <button
                    disabled={enCours === engagement.id}
                    onClick={() => void agir(engagement.id, confirmerEngagement)}
                    className="rounded-lg bg-slate-900 px-3 py-1 text-xs font-medium text-white hover:bg-slate-700 disabled:opacity-50"
                  >
                    Confirmer
                  </button>
                  <button
                    disabled={enCours === engagement.id}
                    onClick={() => void agir(engagement.id, rejeterEngagement)}
                    className="rounded-lg bg-slate-100 px-3 py-1 text-xs text-slate-600 hover:bg-slate-200 disabled:opacity-50"
                  >
                    Rejeter
                  </button>
                </>
              )}
              {engagement.statut === 'CONFIRME' && (
                <button
                  disabled={enCours === engagement.id}
                  onClick={() => void agir(engagement.id, terminerEngagement)}
                  className="rounded-lg bg-slate-100 px-3 py-1 text-xs text-slate-600 hover:bg-slate-200 disabled:opacity-50"
                >
                  Marquer comme termine
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { obtenirCouloir, listerSessionsCouloir } from '../api'
import type { Couloir, Session } from '../types'

const LIBELLE_STATUT: Record<Session['statut'], string> = {
  EN_COURS: 'En cours',
  TERMINEE: 'Terminee',
  ERREUR: 'Erreur',
}

export function CouloirDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [couloir, setCouloir] = useState<Couloir | null>(null)
  const [sessions, setSessions] = useState<Session[]>([])
  const [chargement, setChargement] = useState(true)
  const [introuvable, setIntrouvable] = useState(false)

  useEffect(() => {
    if (!id) return
    Promise.all([obtenirCouloir(id), listerSessionsCouloir(id)])
      .then(([c, s]) => {
        setCouloir(c)
        setSessions(s)
      })
      .catch(() => setIntrouvable(true))
      .finally(() => setChargement(false))
  }, [id])

  if (introuvable) {
    return <p className="p-6 text-center text-sm text-red-600">Couloir introuvable.</p>
  }

  if (chargement || !couloir) {
    return <p className="p-6 text-center text-sm text-slate-500">Chargement...</p>
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-8">
      <Link to="/couloirs" className="mb-4 inline-block text-sm text-slate-500 hover:text-slate-700">
        ← Retour aux couloirs
      </Link>
      <h1 className="mb-1 text-2xl font-semibold text-slate-900">{couloir.nom}</h1>
      <p className="mb-6 text-sm text-slate-500">{couloir.nombreMembres} membre(s)</p>

      <h2 className="mb-3 text-sm font-medium uppercase tracking-wide text-slate-500">
        Sessions du couloir
      </h2>

      {sessions.length === 0 && (
        <p className="text-sm text-slate-500">Aucune session rattachee a ce couloir pour le moment.</p>
      )}

      <ul className="flex flex-col gap-2">
        {sessions.map((session) => (
          <li key={session.id}>
            <Link
              to={`/sessions/${session.id}`}
              className="flex items-center justify-between rounded-lg border border-slate-200 bg-white px-4 py-3 hover:border-slate-300 hover:bg-slate-50"
            >
              <div>
                <p className="font-medium text-slate-900">{session.titre}</p>
                <p className="text-xs text-slate-500">
                  {new Date(session.dateCreation).toLocaleString('fr-FR')}
                </p>
              </div>
              <span className="text-xs font-medium text-slate-500">
                {LIBELLE_STATUT[session.statut]}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}

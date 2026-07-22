import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listerSessions } from '../api'
import type { Session } from '../types'
import { Recorder } from '../components/Recorder'

const LIBELLE_STATUT: Record<Session['statut'], string> = {
  EN_COURS: 'En cours',
  TERMINEE: 'Terminee',
  ERREUR: 'Erreur',
}

export function SessionsListPage() {
  const [sessions, setSessions] = useState<Session[]>([])
  const [chargement, setChargement] = useState(true)
  const navigate = useNavigate()

  async function rafraichir() {
    setChargement(true)
    try {
      setSessions(await listerSessions())
    } finally {
      setChargement(false)
    }
  }

  useEffect(() => {
    void rafraichir()
  }, [])

  return (
    <div className="mx-auto max-w-2xl px-4 py-8">
      <Recorder
        onSessionTerminee={(id) => {
          void rafraichir()
          navigate(`/sessions/${id}`)
        }}
      />

      <h2 className="mt-8 mb-3 text-sm font-medium uppercase tracking-wide text-slate-500">
        Sessions
      </h2>

      {chargement && <p className="text-sm text-slate-500">Chargement...</p>}
      {!chargement && sessions.length === 0 && (
        <p className="text-sm text-slate-500">Aucune session pour le moment.</p>
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

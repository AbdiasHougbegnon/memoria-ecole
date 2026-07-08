import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listerFilsMemoire } from '../api'
import type { FilMemoire } from '../types'

export function FilsMemoirePage() {
  const [fils, setFils] = useState<FilMemoire[]>([])
  const [chargement, setChargement] = useState(true)

  useEffect(() => {
    listerFilsMemoire()
      .then(setFils)
      .finally(() => setChargement(false))
  }, [])

  return (
    <div className="mx-auto max-w-2xl px-4 py-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900">Fils de memoire</h1>
        <Link to="/" className="text-sm text-slate-500 hover:text-slate-700">
          Retour aux sessions
        </Link>
      </div>

      {chargement && <p className="text-sm text-slate-500">Chargement...</p>}
      {!chargement && fils.length === 0 && (
        <p className="text-sm text-slate-500">
          Aucun fil pour le moment. Les fils se forment automatiquement a la fin de chaque session.
        </p>
      )}

      <ul className="flex flex-col gap-4">
        {fils.map((fil) => (
          <li key={fil.id} className="rounded-lg border border-slate-200 bg-white px-4 py-3">
            <div className="mb-1 flex items-center justify-between">
              <h2 className="font-medium text-slate-900">{fil.nom}</h2>
              <span className="text-xs text-slate-400">
                mis a jour le {new Date(fil.dateMiseAJour).toLocaleDateString('fr-FR')}
              </span>
            </div>
            <p className="text-sm text-slate-700">{fil.resumeCumulatif}</p>
            <div className="mt-2 flex flex-wrap gap-2">
              {fil.sessions.map((session) => (
                <Link
                  key={session.id}
                  to={`/sessions/${session.id}`}
                  className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs text-slate-600 hover:border-slate-300 hover:bg-slate-100"
                >
                  {session.titre}
                </Link>
              ))}
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

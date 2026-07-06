import { useState } from 'react'
import { Link } from 'react-router-dom'
import { rechercher, reindexerHistorique } from '../api'
import type { RechercheResultat } from '../types'

function formaterTimestamp(millisecondes: number): string {
  const secondesTotal = Math.floor(millisecondes / 1000)
  const minutes = Math.floor(secondesTotal / 60)
  const secondes = secondesTotal % 60
  return `${minutes}:${secondes.toString().padStart(2, '0')}`
}

export function RecherchePage() {
  const [requete, setRequete] = useState('')
  const [resultats, setResultats] = useState<RechercheResultat[]>([])
  const [chargement, setChargement] = useState(false)
  const [aRecherche, setARecherche] = useState(false)
  const [reindexationLancee, setReindexationLancee] = useState(false)

  async function lancerRecherche(e: React.FormEvent) {
    e.preventDefault()
    if (!requete.trim()) {
      return
    }
    setChargement(true)
    setARecherche(true)
    try {
      setResultats(await rechercher(requete.trim()))
    } finally {
      setChargement(false)
    }
  }

  async function lancerReindexation() {
    await reindexerHistorique()
    setReindexationLancee(true)
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900">Recherche</h1>
        <Link to="/" className="text-sm text-slate-500 hover:text-slate-700">
          Retour aux sessions
        </Link>
      </div>

      <form onSubmit={lancerRecherche} className="mb-2 flex gap-2">
        <input
          type="text"
          value={requete}
          onChange={(e) => setRequete(e.target.value)}
          placeholder="Rechercher dans tout l'historique (ex. quand a-t-on decide de reporter le projet ?)"
          className="flex-1 rounded-lg border border-slate-300 px-4 py-2 text-sm focus:border-slate-500 focus:outline-none"
        />
        <button
          type="submit"
          disabled={chargement}
          className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
        >
          {chargement ? 'Recherche...' : 'Rechercher'}
        </button>
      </form>

      <p className="mb-6 text-xs text-slate-400">
        Les sessions terminees avant l'existence de la recherche ne sont pas indexees automatiquement.{' '}
        {reindexationLancee ? (
          <span>Reindexation en cours en arriere-plan, reessaie dans quelques minutes.</span>
        ) : (
          <button type="button" onClick={() => void lancerReindexation()} className="underline hover:text-slate-600">
            Reindexer l'historique
          </button>
        )}
      </p>

      {aRecherche && !chargement && resultats.length === 0 && (
        <p className="mb-4 text-sm text-slate-500">Aucun resultat.</p>
      )}

      <ul className="flex flex-col gap-3">
        {resultats.map((resultat, index) => (
          <li key={index} className="rounded-lg border border-slate-200 bg-white px-4 py-3">
            <div className="mb-1 flex items-center justify-between">
              <Link
                to={`/sessions/${resultat.sessionId}`}
                className="text-sm font-medium text-slate-900 hover:underline"
              >
                {resultat.titreSession}
              </Link>
              <span className="text-xs text-slate-400">
                {new Date(resultat.dateSession).toLocaleDateString('fr-FR')}
              </span>
            </div>
            <p className="text-sm text-slate-800">{resultat.texte}</p>
            <p className="mt-1 text-xs text-slate-500">
              Intervenant {resultat.locuteur} - {formaterTimestamp(resultat.offsetMillisecondes)}
            </p>
          </li>
        ))}
      </ul>
    </div>
  )
}

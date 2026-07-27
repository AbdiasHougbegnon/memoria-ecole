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
  const [erreur, setErreur] = useState<string | null>(null)

  async function lancerRecherche(e: React.FormEvent) {
    e.preventDefault()
    if (!requete.trim()) {
      return
    }
    setErreur(null)
    setChargement(true)
    setARecherche(true)
    try {
      setResultats(await rechercher(requete.trim()))
    } catch {
      setErreur('La recherche a echoue. Reessaie dans un instant.')
    } finally {
      setChargement(false)
    }
  }

  async function lancerReindexation() {
    setErreur(null)
    try {
      await reindexerHistorique()
      setReindexationLancee(true)
    } catch {
      setErreur("Impossible de lancer la reindexation.")
    }
  }

  return (
    <div className="mx-auto max-w-[760px] px-8 py-12">
      <div className="flex flex-col items-center text-center">
        <span className="mb-4 flex h-[46px] w-[46px] items-center justify-center rounded-2xl" style={{ background: 'var(--color-brand-wash)' }}>
          <svg width="22" height="22" viewBox="0 0 16 16" fill="none">
            <path d="M8 1.5 L9.4 5.6 L13.5 7 L9.4 8.4 L8 12.5 L6.6 8.4 L2.5 7 L6.6 5.6 Z" fill="var(--color-brand)" />
          </svg>
        </span>
        <h1 className="text-[26px] font-bold tracking-tight">Recherche semantique</h1>
        <p className="mt-2 max-w-md text-sm" style={{ color: 'var(--color-ink-muted)' }}>
          Posez une question en langage naturel. Memoria retrouve le passage exact et l'horodatage dans tout
          votre historique.
        </p>
      </div>

      <form
        onSubmit={lancerRecherche}
        className="my-7 flex items-center gap-2.5 rounded-2xl border bg-white p-1.5 pl-4 shadow-sm"
        style={{ borderColor: 'var(--color-border-soft)' }}
      >
        <svg width="19" height="19" viewBox="0 0 18 18" fill="none" style={{ flex: 'none' }}>
          <circle cx="8" cy="8" r="5" stroke="var(--color-ink-faint)" strokeWidth="1.8" />
          <line x1="11.8" y1="11.8" x2="15.5" y2="15.5" stroke="var(--color-ink-faint)" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
        <input
          type="text"
          value={requete}
          onChange={(e) => setRequete(e.target.value)}
          placeholder="ex. quand a-t-on decide de reporter le projet ?"
          className="flex-1 border-none bg-transparent py-2 text-[15px] outline-none"
        />
        <button
          type="submit"
          disabled={chargement}
          className="flex-none rounded-lg px-4 py-2.5 text-[13.5px] font-semibold text-white disabled:opacity-50"
          style={{ background: 'var(--color-brand)' }}
        >
          {chargement ? 'Recherche...' : 'Rechercher'}
        </button>
      </form>

      <p className="mb-6 text-center text-xs" style={{ color: 'var(--color-ink-faint)' }}>
        Les sessions terminees avant l'existence de la recherche ne sont pas indexees automatiquement.{' '}
        {reindexationLancee ? (
          <span>Reindexation en cours en arriere-plan, reessaie dans quelques minutes.</span>
        ) : (
          <button type="button" onClick={() => void lancerReindexation()} className="underline">
            Reindexer l'historique
          </button>
        )}
      </p>

      {erreur && <p className="mb-6 text-center text-sm" style={{ color: '#B02631' }}>{erreur}</p>}

      {aRecherche && !chargement && resultats.length === 0 && (
        <p className="text-center text-sm" style={{ color: 'var(--color-ink-muted)' }}>Aucun resultat.</p>
      )}

      <div className="flex flex-col gap-2.5">
        {resultats.map((resultat, index) => (
          <Link
            key={index}
            to={`/sessions/${resultat.sessionId}`}
            className="flex flex-col gap-2 rounded-2xl border bg-white px-4 py-3.5 transition-all hover:-translate-y-px hover:shadow-md"
            style={{ borderColor: 'var(--color-border-soft)' }}
          >
            <div className="flex items-center justify-between gap-3">
              <span className="text-[13.5px] font-semibold">{resultat.titreSession}</span>
              <span className="flex flex-none items-center gap-2">
                <span className="text-[11px]" style={{ color: 'var(--color-ink-faint)' }}>
                  {new Date(resultat.dateSession).toLocaleDateString('fr-FR')}
                </span>
                <span className="rounded px-2 py-0.5 text-[11px]" style={{ background: 'var(--color-brand-wash)', color: 'var(--color-brand)', fontFamily: 'var(--font-mono)' }}>
                  &#9656; {formaterTimestamp(resultat.offsetMillisecondes)}
                </span>
              </span>
            </div>
            <div className="border-l-2 pl-3 text-[13px] italic leading-relaxed" style={{ borderColor: 'var(--color-border-soft)', color: 'var(--color-ink-muted)' }}>
              &laquo; {resultat.texte} &raquo;
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}

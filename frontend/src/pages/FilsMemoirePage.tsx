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
    <div className="mx-auto max-w-[900px] px-8 py-10">
      <h1 className="text-[28px] font-bold tracking-tight">Fils de memoire</h1>
      <p className="mt-1.5 text-sm" style={{ color: 'var(--color-ink-muted)' }}>
        Regroupements automatiques de sessions par sujet.
      </p>

      {chargement && <p className="mt-8 text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>}
      {!chargement && fils.length === 0 && (
        <p className="mt-8 text-sm" style={{ color: 'var(--color-ink-muted)' }}>
          Aucun fil pour le moment. Les fils se forment automatiquement a la fin de chaque session.
        </p>
      )}

      <div className="mt-7 flex flex-col gap-8">
        {fils.map((fil) => (
          <div key={fil.id}>
            <div className="mb-2.5 flex items-center gap-2.5">
              <span className="inline-block h-[9px] w-[9px] rounded-[3px]" style={{ background: 'var(--color-brand)' }} />
              <span className="text-xs" style={{ fontFamily: 'var(--font-mono)', color: 'var(--color-ink-faint)' }}>
                mis a jour le {new Date(fil.dateMiseAJour).toLocaleDateString('fr-FR')}
              </span>
            </div>
            <h2 className="text-xl font-bold tracking-tight">{fil.nom}</h2>

            <div
              className="mt-3.5 rounded-2xl border p-5"
              style={{ borderColor: '#E4E2F6', background: 'linear-gradient(180deg,#F6F5FE,#FBFAFE)' }}
            >
              <div className="mb-2.5 flex items-center gap-2">
                <span className="flex h-6 w-6 items-center justify-center rounded-lg" style={{ background: 'var(--color-brand-wash)' }}>
                  <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
                    <path d="M8 1.5 L9.4 5.6 L13.5 7 L9.4 8.4 L8 12.5 L6.6 8.4 L2.5 7 L6.6 5.6 Z" fill="var(--color-brand)" />
                  </svg>
                </span>
                <h3 className="text-sm font-bold">Resume cumulatif</h3>
              </div>
              <p className="text-sm leading-relaxed">{fil.resumeCumulatif}</p>
            </div>

            <div className="relative mt-5 pl-6">
              <div className="absolute bottom-2 left-1.5 top-2 w-[2px]" style={{ background: 'var(--color-border-soft)' }} />
              <div className="flex flex-col gap-3">
                {fil.sessions.map((session) => (
                  <div key={session.id} className="relative">
                    <span
                      className="absolute -left-6 top-4 h-[11px] w-[11px] rounded-full border-[2.5px] bg-white"
                      style={{ borderColor: 'var(--color-brand)' }}
                    />
                    <Link
                      to={`/sessions/${session.id}`}
                      className="block rounded-xl border bg-white px-4 py-3 text-sm font-semibold transition-all hover:-translate-y-px hover:shadow-md"
                      style={{ borderColor: 'var(--color-border-soft)' }}
                    >
                      {session.titre}
                    </Link>
                  </div>
                ))}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

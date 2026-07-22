import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listerSessions } from '../api'
import type { Session } from '../types'
import { Recorder } from '../components/Recorder'

const GROUPES: { statut: Session['statut']; label: string; dot: string }[] = [
  { statut: 'EN_COURS', label: 'En cours', dot: 'var(--color-live)' },
  { statut: 'TERMINEE', label: 'Terminees', dot: 'var(--color-ok)' },
  { statut: 'ERREUR', label: 'Erreur', dot: 'var(--color-warn)' },
]

function CarteSession({ session }: { session: Session }) {
  const enDirect = session.statut === 'EN_COURS'
  return (
    <Link
      to={`/sessions/${session.id}`}
      className="flex flex-col gap-3 rounded-2xl border bg-white px-[18px] py-4 transition-all hover:-translate-y-px hover:shadow-md"
      style={{ borderColor: enDirect ? '#F1C7C9' : 'var(--color-border-soft)' }}
    >
      <div className="flex items-center justify-between gap-3">
        {enDirect ? (
          <span
            className="flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11.5px] font-bold"
            style={{ background: 'var(--color-live-wash)', color: 'var(--color-live)' }}
          >
            <span className="inline-block h-1.5 w-1.5 rounded-full" style={{ background: 'var(--color-live)', animation: 'mem-pulse 1.4s ease-in-out infinite' }} />
            EN DIRECT
          </span>
        ) : (
          <span className="text-xs" style={{ fontFamily: 'var(--font-mono)', color: 'var(--color-ink-muted)' }}>
            {new Date(session.dateCreation).toLocaleDateString('fr-FR')}
          </span>
        )}
        <span className="text-xs" style={{ fontFamily: 'var(--font-mono)', color: 'var(--color-ink-faint)' }}>
          {new Date(session.dateCreation).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}
        </span>
      </div>
      <div className="text-[16px] font-semibold leading-tight">{session.titre}</div>
    </Link>
  )
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

  const groupes = GROUPES
    .map((g) => ({ ...g, items: sessions.filter((s) => s.statut === g.statut) }))
    .filter((g) => g.items.length > 0)

  return (
    <div className="mx-auto max-w-2xl px-8 py-10">
      <h1 className="mb-1 text-[28px] font-bold tracking-tight">Sessions</h1>
      <p className="mb-7 text-sm" style={{ color: 'var(--color-ink-muted)' }}>
        Vos enregistrements, transcrits et resumes automatiquement.
      </p>

      <Recorder
        onSessionTerminee={(id) => {
          void rafraichir()
          navigate(`/sessions/${id}`)
        }}
      />

      {chargement && <p className="mt-9 text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>}
      {!chargement && sessions.length === 0 && (
        <p className="mt-9 text-sm" style={{ color: 'var(--color-ink-muted)' }}>Aucune session pour le moment.</p>
      )}

      {groupes.map((groupe) => (
        <div key={groupe.statut} className="mt-9">
          <div className="mb-3 flex items-center gap-2.5">
            <span className="inline-block h-[7px] w-[7px] rounded-full" style={{ background: groupe.dot }} />
            <h2 className="text-xs font-bold uppercase tracking-wide" style={{ color: 'var(--color-ink)' }}>{groupe.label}</h2>
            <span className="text-xs" style={{ fontFamily: 'var(--font-mono)', color: 'var(--color-ink-faint)' }}>{groupe.items.length}</span>
          </div>
          <ul className="flex flex-col gap-2.5">
            {groupe.items.map((session) => (
              <li key={session.id}>
                <CarteSession session={session} />
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  )
}

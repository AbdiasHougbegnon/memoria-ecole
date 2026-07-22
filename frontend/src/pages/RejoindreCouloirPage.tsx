import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { obtenirCouloir, rejoindreCouloir } from '../api'
import type { Couloir } from '../types'

export function RejoindreCouloirPage() {
  const { id } = useParams<{ id: string }>()
  const [couloir, setCouloir] = useState<Couloir | null>(null)
  const [introuvable, setIntrouvable] = useState(false)
  const [enCours, setEnCours] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)
  const navigate = useNavigate()

  useEffect(() => {
    if (!id) return
    obtenirCouloir(id)
      .then(setCouloir)
      .catch(() => setIntrouvable(true))
  }, [id])

  async function rejoindre() {
    if (!id) return
    setErreur(null)
    setEnCours(true)
    try {
      await rejoindreCouloir(id)
      navigate(`/couloirs/${id}`)
    } catch {
      setErreur('Impossible de rejoindre ce couloir.')
    } finally {
      setEnCours(false)
    }
  }

  if (introuvable) {
    return <p className="p-6 text-center text-sm" style={{ color: '#B02631' }}>Couloir introuvable.</p>
  }

  if (!couloir) {
    return <p className="p-6 text-center text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>
  }

  return (
    <div style={{ background: 'var(--color-cream)', minHeight: '100vh' }} className="mx-auto flex max-w-sm flex-col justify-center px-4 text-center">
      <p className="mb-2 text-sm" style={{ color: 'var(--color-ink-muted)' }}>Tu es sur le point de rejoindre</p>
      <h1 className="mb-6 text-2xl font-bold tracking-tight">{couloir.nom}</h1>
      {erreur && <p className="mb-4 text-sm" style={{ color: '#B02631' }}>{erreur}</p>}
      <button
        onClick={() => void rejoindre()}
        disabled={enCours}
        className="rounded-lg px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
        style={{ background: 'var(--color-brand)', boxShadow: '0 2px 10px rgba(75,70,214,.3)' }}
      >
        {enCours ? 'Patiente...' : 'Rejoindre'}
      </button>
    </div>
  )
}

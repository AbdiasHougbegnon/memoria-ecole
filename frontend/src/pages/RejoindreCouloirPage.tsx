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
    return <p className="p-6 text-center text-sm text-red-600">Couloir introuvable.</p>
  }

  if (!couloir) {
    return <p className="p-6 text-center text-sm text-slate-500">Chargement...</p>
  }

  return (
    <div className="mx-auto flex min-h-screen max-w-sm flex-col justify-center px-4 text-center">
      <p className="mb-2 text-sm text-slate-500">Tu es sur le point de rejoindre</p>
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">{couloir.nom}</h1>
      {erreur && <p className="mb-4 text-sm text-red-600">{erreur}</p>}
      <button
        onClick={() => void rejoindre()}
        disabled={enCours}
        className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
      >
        {enCours ? 'Patiente...' : 'Rejoindre'}
      </button>
    </div>
  )
}

import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { creerCouloir, listerCouloirs } from '../api'
import type { Couloir } from '../types'

export function CouloirsPage() {
  const [couloirs, setCouloirs] = useState<Couloir[]>([])
  const [chargement, setChargement] = useState(true)
  const [nom, setNom] = useState('')
  const [creationEnCours, setCreationEnCours] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)
  const [lienCopie, setLienCopie] = useState<string | null>(null)
  const navigate = useNavigate()

  async function rafraichir() {
    setChargement(true)
    try {
      setCouloirs(await listerCouloirs())
    } finally {
      setChargement(false)
    }
  }

  useEffect(() => {
    void rafraichir()
  }, [])

  async function creer(e: React.FormEvent) {
    e.preventDefault()
    if (!nom.trim()) return
    setErreur(null)
    setCreationEnCours(true)
    try {
      const couloir = await creerCouloir(nom.trim())
      setNom('')
      await rafraichir()
      navigate(`/couloirs/${couloir.id}`)
    } catch {
      setErreur('Impossible de creer le couloir.')
    } finally {
      setCreationEnCours(false)
    }
  }

  function copierLien(id: string) {
    const lien = `${window.location.origin}/couloirs/${id}/rejoindre`
    navigator.clipboard.writeText(lien).then(() => {
      setLienCopie(id)
      setTimeout(() => setLienCopie(null), 2000)
    })
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900">Couloirs</h1>
        <Link to="/" className="text-sm text-slate-500 hover:text-slate-700">
          Retour aux sessions
        </Link>
      </div>

      <form onSubmit={creer} className="mb-8 flex gap-2">
        <input
          type="text"
          placeholder="Nom du couloir (ex. Ing1-SI EPISEN)"
          value={nom}
          onChange={(e) => setNom(e.target.value)}
          className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
        />
        <button
          type="submit"
          disabled={creationEnCours}
          className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
        >
          {creationEnCours ? 'Creation...' : 'Creer'}
        </button>
      </form>
      {erreur && <p className="mb-4 text-sm text-red-600">{erreur}</p>}

      <h2 className="mb-3 text-sm font-medium uppercase tracking-wide text-slate-500">
        Mes couloirs
      </h2>

      {chargement && <p className="text-sm text-slate-500">Chargement...</p>}
      {!chargement && couloirs.length === 0 && (
        <p className="text-sm text-slate-500">Tu ne fais partie d'aucun couloir pour le moment.</p>
      )}

      <ul className="flex flex-col gap-2">
        {couloirs.map((couloir) => (
          <li
            key={couloir.id}
            className="flex items-center justify-between rounded-lg border border-slate-200 bg-white px-4 py-3"
          >
            <Link to={`/couloirs/${couloir.id}`} className="font-medium text-slate-900 hover:underline">
              {couloir.nom}
            </Link>
            <div className="flex items-center gap-3">
              <span className="text-xs text-slate-500">{couloir.nombreMembres} membre(s)</span>
              <button
                onClick={() => copierLien(couloir.id)}
                className="rounded-lg bg-slate-100 px-3 py-1 text-xs text-slate-600 hover:bg-slate-200"
              >
                {lienCopie === couloir.id ? 'Lien copie !' : "Copier le lien d'invitation"}
              </button>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

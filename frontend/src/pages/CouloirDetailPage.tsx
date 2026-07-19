import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  listerMembresCouloir,
  listerSessionsCouloir,
  obtenirCouloir,
  renommerCouloir,
  retirerMembreCouloir,
  supprimerCouloir,
} from '../api'
import { obtenirUtilisateurIdConnecte } from '../auth'
import type { Couloir, MembreCouloir, Session } from '../types'

const LIBELLE_STATUT: Record<Session['statut'], string> = {
  EN_COURS: 'En cours',
  TERMINEE: 'Terminee',
  ERREUR: 'Erreur',
}

export function CouloirDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [couloir, setCouloir] = useState<Couloir | null>(null)
  const [sessions, setSessions] = useState<Session[]>([])
  const [membres, setMembres] = useState<MembreCouloir[]>([])
  const [chargement, setChargement] = useState(true)
  const [introuvable, setIntrouvable] = useState(false)
  const [nouveauNom, setNouveauNom] = useState('')
  const [renommageEnCours, setRenommageEnCours] = useState(false)

  const utilisateurIdConnecte = obtenirUtilisateurIdConnecte()
  const estProprietaire = couloir !== null && couloir.proprietaireId === utilisateurIdConnecte

  useEffect(() => {
    if (!id) return
    Promise.all([obtenirCouloir(id), listerSessionsCouloir(id), listerMembresCouloir(id)])
      .then(([c, s, m]) => {
        setCouloir(c)
        setSessions(s)
        setNouveauNom(c.nom)
        setMembres(m)
      })
      .catch(() => setIntrouvable(true))
      .finally(() => setChargement(false))
  }, [id])

  async function gererRenommage() {
    if (!id || !nouveauNom.trim()) return
    setRenommageEnCours(true)
    try {
      const c = await renommerCouloir(id, nouveauNom.trim())
      setCouloir(c)
    } finally {
      setRenommageEnCours(false)
    }
  }

  async function gererSuppression() {
    if (!id) return
    if (!window.confirm('Supprimer definitivement ce couloir ?')) return
    await supprimerCouloir(id)
    navigate('/couloirs')
  }

  async function gererRetraitMembre(membreId: string) {
    if (!id) return
    await retirerMembreCouloir(id, membreId)
    setMembres((precedent) => precedent.filter((m) => m.utilisateurId !== membreId))
  }

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

      {estProprietaire && (
        <div className="mb-8 flex flex-col gap-6 rounded-lg border border-slate-200 bg-slate-50 p-4">
          <div>
            <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
              Renommer le couloir
            </h2>
            <div className="flex gap-2">
              <input
                type="text"
                value={nouveauNom}
                onChange={(e) => setNouveauNom(e.target.value)}
                className="flex-1 rounded-md border border-slate-300 px-3 py-1.5 text-sm"
              />
              <button
                onClick={gererRenommage}
                disabled={renommageEnCours || !nouveauNom.trim()}
                className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
              >
                Renommer
              </button>
            </div>
          </div>

          <div>
            <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">Membres</h2>
            <ul className="flex flex-col gap-2">
              {membres.map((membre) => (
                <li
                  key={membre.utilisateurId}
                  className="flex items-center justify-between rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                >
                  <span>{membre.email}</span>
                  {membre.utilisateurId !== couloir.proprietaireId && (
                    <button
                      onClick={() => gererRetraitMembre(membre.utilisateurId)}
                      className="text-xs font-medium text-red-600 hover:text-red-800"
                    >
                      Retirer
                    </button>
                  )}
                </li>
              ))}
            </ul>
          </div>

          <div>
            <button
              onClick={gererSuppression}
              className="rounded-md border border-red-300 px-3 py-1.5 text-sm font-medium text-red-600 hover:bg-red-50"
            >
              Supprimer le couloir
            </button>
          </div>
        </div>
      )}

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

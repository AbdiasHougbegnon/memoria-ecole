import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  listerMembresCouloir,
  listerSessionsCouloir,
  obtenirCouloir,
  quitterCouloir,
  renommerCouloir,
  retirerMembreCouloir,
  supprimerCouloir,
  transfererProprieteCouloir,
} from '../api'
import { obtenirModuleConnecte, obtenirUtilisateurIdConnecte } from '../auth'
import type { Couloir, MembreCouloir, Session } from '../types'

const LIBELLE_STATUT: Record<Session['statut'], string> = {
  EN_COURS: 'En cours',
  TERMINEE: 'Terminee',
  ERREUR: 'Erreur',
}

const PALETTE_AVATAR = ['#4B46D6', '#2F6FB0', '#E0662D', '#2E9E6B', '#9B4DCA', '#C99A2E', '#B0472F']
function couleurMembre(cle: string): string {
  let h = 0
  for (let i = 0; i < cle.length; i++) h = (h * 31 + cle.charCodeAt(i)) >>> 0
  return PALETTE_AVATAR[h % PALETTE_AVATAR.length]
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
  const [gestionOuverte, setGestionOuverte] = useState(false)

  const utilisateurIdConnecte = obtenirUtilisateurIdConnecte()
  const estProprietaire = couloir !== null && couloir.proprietaireId === utilisateurIdConnecte
  const estMembreNonProprietaire =
    !estProprietaire && membres.some((m) => m.utilisateurId === utilisateurIdConnecte)

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

  async function gererQuitter() {
    if (!id) return
    if (!window.confirm('Quitter ce couloir ?')) return
    await quitterCouloir(id)
    navigate('/couloirs')
  }

  async function gererTransfert(membreId: string, membreEmail: string) {
    if (!id) return
    if (!window.confirm(`Rendre ${membreEmail} proprietaire de ce couloir ? Tu perdras les droits de gestion.`)) return
    const c = await transfererProprieteCouloir(id, membreId)
    setCouloir(c)
  }

  if (introuvable) {
    return <p className="p-6 text-center text-sm" style={{ color: '#B02631' }}>Couloir introuvable.</p>
  }

  if (chargement || !couloir) {
    return <p className="p-6 text-center text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>
  }

  return (
    <div className="mx-auto max-w-[1000px] px-8 py-10">
      <Link to="/couloirs" className="mb-4 inline-flex items-center gap-1.5 text-sm" style={{ color: 'var(--color-ink-faint)' }}>
        &larr; Retour aux couloirs
      </Link>

      <div className="flex items-center gap-4">
        <span
          className="flex h-14 w-14 flex-none items-center justify-center rounded-2xl text-2xl font-bold text-white"
          style={{ background: couleurMembre(couloir.nom) }}
        >
          {couloir.nom.charAt(0).toUpperCase()}
        </span>
        <div>
          <h1 className="text-[26px] font-bold tracking-tight">{couloir.nom}</h1>
          <div className="mt-1 text-xs" style={{ color: 'var(--color-ink-faint)', fontFamily: 'var(--font-mono)' }}>
            {couloir.nombreMembres} membre(s)
          </div>
        </div>
        {obtenirModuleConnecte() === 'ECOLE' && (
          <Link
            to={`/couloirs/${couloir.id}/matieres`}
            className="ml-auto rounded-lg px-3.5 py-2 text-xs font-semibold text-white"
            style={{ background: 'var(--color-brand)' }}
          >
            Matieres &amp; tuteur vocal
          </Link>
        )}
      </div>

      <div className="mt-7 grid grid-cols-[1.5fr_1fr] gap-6">
        <div className="flex flex-col gap-3.5">
          <h2 className="text-sm font-bold">Sessions du couloir</h2>

          {sessions.length === 0 && (
            <p className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Aucune session rattachee a ce couloir pour le moment.</p>
          )}

          <ul className="flex flex-col gap-2.5">
            {sessions.map((session) => (
              <li key={session.id}>
                <Link
                  to={`/sessions/${session.id}`}
                  className="flex items-center justify-between rounded-2xl border bg-white px-5 py-4 transition-all hover:-translate-y-px hover:shadow-md"
                  style={{ borderColor: 'var(--color-border-soft)' }}
                >
                  <div>
                    <p className="font-semibold">{session.titre}</p>
                    <p className="mt-1 text-xs" style={{ color: 'var(--color-ink-faint)' }}>
                      {new Date(session.dateCreation).toLocaleString('fr-FR')}
                    </p>
                  </div>
                  <span className="text-xs font-semibold" style={{ color: 'var(--color-ink-muted)' }}>
                    {LIBELLE_STATUT[session.statut]}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </div>

        <div className="flex flex-col gap-3.5">
          <div className="rounded-2xl border bg-white p-4.5" style={{ borderColor: 'var(--color-border-soft)' }}>
            <div className="mb-3.5 text-[11px] font-bold uppercase tracking-wide" style={{ color: 'var(--color-ink-faint-2)' }}>
              Membres &middot; {couloir.nombreMembres}
            </div>
            <div className="mb-3.5 flex items-center">
              {membres.slice(0, 6).map((membre) => (
                <span
                  key={membre.utilisateurId}
                  title={membre.email}
                  className="-ml-2 flex h-8 w-8 items-center justify-center rounded-full border-2 border-white text-[11px] font-semibold text-white first:ml-0"
                  style={{ background: couleurMembre(membre.email) }}
                >
                  {membre.email.slice(0, 2).toUpperCase()}
                </span>
              ))}
              {membres.length > 6 && (
                <span className="ml-3 text-xs" style={{ color: 'var(--color-ink-faint)', fontFamily: 'var(--font-mono)' }}>+{membres.length - 6}</span>
              )}
            </div>
            {(estProprietaire || estMembreNonProprietaire) && (
              <button
                onClick={() => setGestionOuverte((v) => !v)}
                className="w-full rounded-lg border border-dashed py-2.5 text-xs font-semibold"
                style={{ borderColor: '#C9C3B8', color: 'var(--color-ink-muted)' }}
              >
                {gestionOuverte ? 'Fermer la gestion' : 'Gerer les membres'}
              </button>
            )}
          </div>

          {gestionOuverte && estProprietaire && (
            <div className="flex flex-col gap-5 rounded-2xl border p-4.5" style={{ borderColor: 'var(--color-border-soft)', background: 'var(--color-sidebar)' }}>
              <div>
                <h3 className="mb-2 text-[11px] font-bold uppercase tracking-wide" style={{ color: 'var(--color-ink-faint-2)' }}>
                  Renommer
                </h3>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={nouveauNom}
                    onChange={(e) => setNouveauNom(e.target.value)}
                    className="flex-1 rounded-md border px-2.5 py-1.5 text-sm"
                    style={{ borderColor: 'var(--color-border-soft)' }}
                  />
                  <button
                    onClick={gererRenommage}
                    disabled={renommageEnCours || !nouveauNom.trim()}
                    className="rounded-md px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
                    style={{ background: 'var(--color-brand)' }}
                  >
                    OK
                  </button>
                </div>
              </div>

              <div>
                <h3 className="mb-2 text-[11px] font-bold uppercase tracking-wide" style={{ color: 'var(--color-ink-faint-2)' }}>Membres</h3>
                <ul className="flex flex-col gap-1.5">
                  {membres.map((membre) => (
                    <li
                      key={membre.utilisateurId}
                      className="flex items-center justify-between rounded-md border bg-white px-2.5 py-1.5 text-xs"
                      style={{ borderColor: 'var(--color-border-soft)' }}
                    >
                      <span className="truncate">{membre.email}</span>
                      {membre.utilisateurId !== couloir.proprietaireId && (
                        <span className="flex flex-none gap-2.5">
                          <button onClick={() => gererTransfert(membre.utilisateurId, membre.email)} style={{ color: 'var(--color-ink-muted)' }}>
                            Proprietaire
                          </button>
                          <button onClick={() => gererRetraitMembre(membre.utilisateurId)} style={{ color: '#D33A40' }}>
                            Retirer
                          </button>
                        </span>
                      )}
                    </li>
                  ))}
                </ul>
              </div>

              <button
                onClick={gererSuppression}
                className="rounded-md border py-1.5 text-xs font-semibold"
                style={{ borderColor: '#F1C7C9', color: '#D33A40' }}
              >
                Supprimer le couloir
              </button>
            </div>
          )}

          {gestionOuverte && estMembreNonProprietaire && (
            <button
              onClick={gererQuitter}
              className="rounded-md border py-2 text-xs font-semibold"
              style={{ borderColor: '#F1C7C9', color: '#D33A40' }}
            >
              Quitter le couloir
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

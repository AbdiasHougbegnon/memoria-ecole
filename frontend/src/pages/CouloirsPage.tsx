import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { creerCouloir, listerCouloirs } from '../api'
import { obtenirModuleConnecte, obtenirUtilisateurIdConnecte } from '../auth'
import { IDENTITES_MODULE } from '../moduleIdentite'
import type { Couloir } from '../types'

const PALETTE_AVATAR = ['#4B46D6', '#2F6FB0', '#E0662D', '#2E9E6B', '#9B4DCA', '#C99A2E', '#B0472F']
function couleurCouloir(nom: string): string {
  let h = 0
  for (let i = 0; i < nom.length; i++) h = (h * 31 + nom.charCodeAt(i)) >>> 0
  return PALETTE_AVATAR[h % PALETTE_AVATAR.length]
}

export function CouloirsPage() {
  const [couloirs, setCouloirs] = useState<Couloir[]>([])
  const [chargement, setChargement] = useState(true)
  const [nom, setNom] = useState('')
  const [creationEnCours, setCreationEnCours] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)
  const [lienCopie, setLienCopie] = useState<string | null>(null)
  const navigate = useNavigate()
  const libelle = IDENTITES_MODULE[obtenirModuleConnecte() ?? 'ENTREPRISE'].libelleCouloirs
  const utilisateurIdConnecte = obtenirUtilisateurIdConnecte()

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

  function copierLien(e: React.MouseEvent, id: string) {
    e.stopPropagation()
    e.preventDefault()
    const lien = `${window.location.origin}/couloirs/${id}/rejoindre`
    navigator.clipboard.writeText(lien).then(() => {
      setLienCopie(id)
      setTimeout(() => setLienCopie(null), 2000)
    })
  }

  return (
    <div className="mx-auto max-w-[1000px] px-8 py-10">
      <div className="flex items-start justify-between gap-6">
        <div>
          <h1 className="text-[28px] font-bold tracking-tight">{libelle}</h1>
          <p className="mt-1.5 max-w-lg text-sm" style={{ color: 'var(--color-ink-muted)' }}>
            Vos espaces partages. Les sessions collectives et les membres y sont regroupes.
          </p>
        </div>
      </div>

      <form onSubmit={creer} className="mt-6 flex gap-2">
        <input
          type="text"
          placeholder="Nom du couloir (ex. Ing1-SI EPISEN)"
          value={nom}
          onChange={(e) => setNom(e.target.value)}
          className="flex-1 rounded-lg border px-3.5 py-2.5 text-sm outline-none"
          style={{ borderColor: 'var(--color-border-soft)', background: '#FCFBF9' }}
        />
        <button
          type="submit"
          disabled={creationEnCours}
          className="rounded-lg px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
          style={{ background: 'var(--color-brand)' }}
        >
          {creationEnCours ? 'Creation...' : 'Creer'}
        </button>
      </form>
      {erreur && <p className="mt-3 text-sm" style={{ color: '#B02631' }}>{erreur}</p>}

      {chargement && <p className="mt-8 text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>}
      {!chargement && couloirs.length === 0 && (
        <p className="mt-8 text-sm" style={{ color: 'var(--color-ink-muted)' }}>Tu ne fais partie d'aucun couloir pour le moment.</p>
      )}

      <div className="mt-7 grid grid-cols-2 gap-3.5">
        {couloirs.map((couloir) => {
          const proprietaire = couloir.proprietaireId === utilisateurIdConnecte
          return (
            <a
              key={couloir.id}
              href={`/couloirs/${couloir.id}`}
              onClick={(e) => { e.preventDefault(); navigate(`/couloirs/${couloir.id}`) }}
              className="cursor-pointer rounded-2xl border bg-white p-5 text-left transition-all hover:-translate-y-px hover:shadow-md"
              style={{ borderColor: 'var(--color-border-soft)' }}
            >
              <div className="mb-4 flex items-center justify-between gap-3">
                <span
                  className="flex h-11 w-11 items-center justify-center rounded-xl text-lg font-bold text-white"
                  style={{ background: couleurCouloir(couloir.nom) }}
                >
                  {couloir.nom.charAt(0).toUpperCase()}
                </span>
                <span className="rounded-full px-2.5 py-1 text-[11px] font-semibold" style={{ background: '#F4F2EE', color: 'var(--color-ink-muted)' }}>
                  {proprietaire ? 'Proprietaire' : 'Membre'}
                </span>
              </div>
              <div className="text-[16.5px] font-bold tracking-tight">{couloir.nom}</div>
              <div className="mt-3 flex items-center justify-between border-t pt-3.5" style={{ borderColor: 'var(--color-border-softer)' }}>
                <div>
                  <div className="text-base font-bold" style={{ fontFamily: 'var(--font-mono)' }}>{couloir.nombreMembres}</div>
                  <div className="text-[11px]" style={{ color: 'var(--color-ink-faint)' }}>membre(s)</div>
                </div>
                <button
                  onClick={(e) => copierLien(e, couloir.id)}
                  className="rounded-lg px-3 py-1.5 text-xs font-semibold"
                  style={{ background: '#F4F2EE', color: 'var(--color-ink-muted)' }}
                >
                  {lienCopie === couloir.id ? 'Copie !' : "Copier l'invitation"}
                </button>
              </div>
            </a>
          )
        })}
      </div>
    </div>
  )
}

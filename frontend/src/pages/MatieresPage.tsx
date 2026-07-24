import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { creerMatiere, listerMatieresParCouloir, obtenirCouloir } from '../api'
import { obtenirUtilisateurIdConnecte } from '../auth'
import type { Couloir, Matiere } from '../types'

export function MatieresPage() {
  const { id: couloirId } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [couloir, setCouloir] = useState<Couloir | null>(null)
  const [matieres, setMatieres] = useState<Matiere[]>([])
  const [chargement, setChargement] = useState(true)
  const [nom, setNom] = useState('')
  const [creationEnCours, setCreationEnCours] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)

  const utilisateurIdConnecte = obtenirUtilisateurIdConnecte()
  const estProprietaire = couloir !== null && couloir.proprietaireId === utilisateurIdConnecte

  async function rafraichir() {
    if (!couloirId) return
    setChargement(true)
    try {
      const [c, m] = await Promise.all([obtenirCouloir(couloirId), listerMatieresParCouloir(couloirId)])
      setCouloir(c)
      setMatieres(m)
    } finally {
      setChargement(false)
    }
  }

  useEffect(() => {
    void rafraichir()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [couloirId])

  async function creer(e: React.FormEvent) {
    e.preventDefault()
    if (!couloirId || !nom.trim()) return
    setErreur(null)
    setCreationEnCours(true)
    try {
      const matiere = await creerMatiere(nom.trim(), couloirId)
      setNom('')
      await rafraichir()
      navigate(`/matieres/${matiere.id}`)
    } catch {
      setErreur('Impossible de creer la matiere.')
    } finally {
      setCreationEnCours(false)
    }
  }

  if (chargement || !couloir) {
    return <p className="p-6 text-center text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>
  }

  return (
    <div className="mx-auto max-w-[900px] px-8 py-10">
      <Link to={`/couloirs/${couloirId}`} className="mb-4 inline-flex items-center gap-1.5 text-sm" style={{ color: 'var(--color-ink-faint)' }}>
        &larr; Retour au couloir
      </Link>
      <h1 className="text-[26px] font-bold tracking-tight">Matieres &middot; {couloir.nom}</h1>

      {estProprietaire && (
        <form onSubmit={creer} className="mt-6 flex gap-2">
          <input
            type="text"
            placeholder="Nom de la matiere (ex. Mathematiques)"
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
      )}
      {erreur && <p className="mt-3 text-sm" style={{ color: '#B02631' }}>{erreur}</p>}

      {matieres.length === 0 && (
        <p className="mt-8 text-sm" style={{ color: 'var(--color-ink-muted)' }}>Aucune matiere pour ce couloir pour le moment.</p>
      )}

      <ul className="mt-7 flex flex-col gap-2.5">
        {matieres.map((matiere) => (
          <li key={matiere.id}>
            <Link
              to={`/matieres/${matiere.id}`}
              className="flex items-center justify-between rounded-2xl border bg-white px-5 py-4 transition-all hover:-translate-y-px hover:shadow-md"
              style={{ borderColor: 'var(--color-border-soft)' }}
            >
              <p className="font-semibold">{matiere.nom}</p>
              <span className="text-xs" style={{ color: 'var(--color-ink-faint)' }}>
                {new Date(matiere.dateCreation).toLocaleDateString('fr-FR')}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}

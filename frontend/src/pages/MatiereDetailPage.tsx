import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { creerNotion, creerSeance, listerNotionsParMatiere, listerSeancesParMatiere, obtenirCouloir, obtenirMatiere } from '../api'
import { obtenirUtilisateurIdConnecte } from '../auth'
import type { Couloir, Matiere, Notion, Seance } from '../types'

export function MatiereDetailPage() {
  const { id: matiereId } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [matiere, setMatiere] = useState<Matiere | null>(null)
  const [couloir, setCouloir] = useState<Couloir | null>(null)
  const [notions, setNotions] = useState<Notion[]>([])
  const [seances, setSeances] = useState<Seance[]>([])
  const [chargement, setChargement] = useState(true)

  const [terme, setTerme] = useState('')
  const [definition, setDefinition] = useState('')
  const [titreSeance, setTitreSeance] = useState('')
  const [erreur, setErreur] = useState<string | null>(null)

  const utilisateurIdConnecte = obtenirUtilisateurIdConnecte()
  const estProprietaire = couloir !== null && couloir.proprietaireId === utilisateurIdConnecte

  async function rafraichir() {
    if (!matiereId) return
    setChargement(true)
    try {
      const m = await obtenirMatiere(matiereId)
      const [c, n, s] = await Promise.all([obtenirCouloir(m.couloirId), listerNotionsParMatiere(matiereId), listerSeancesParMatiere(matiereId)])
      setMatiere(m)
      setCouloir(c)
      setNotions(n)
      setSeances(s)
    } finally {
      setChargement(false)
    }
  }

  useEffect(() => {
    void rafraichir()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [matiereId])

  async function ajouterNotion(e: React.FormEvent) {
    e.preventDefault()
    if (!matiereId || !terme.trim() || !definition.trim()) return
    setErreur(null)
    try {
      await creerNotion(matiereId, terme.trim(), definition.trim(), notions.length)
      setTerme('')
      setDefinition('')
      await rafraichir()
    } catch {
      setErreur('Impossible de creer la notion.')
    }
  }

  async function ajouterSeance(e: React.FormEvent) {
    e.preventDefault()
    if (!matiereId || !titreSeance.trim()) return
    setErreur(null)
    try {
      const seance = await creerSeance(matiereId, titreSeance.trim())
      setTitreSeance('')
      await rafraichir()
      navigate(`/seances/${seance.id}`)
    } catch {
      setErreur('Impossible de creer la seance.')
    }
  }

  if (chargement || !matiere || !couloir) {
    return <p className="p-6 text-center text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>
  }

  return (
    <div className="mx-auto max-w-[900px] px-8 py-10">
      <Link to={`/couloirs/${couloir.id}/matieres`} className="mb-4 inline-flex items-center gap-1.5 text-sm" style={{ color: 'var(--color-ink-faint)' }}>
        &larr; Retour aux matieres
      </Link>
      <h1 className="text-[26px] font-bold tracking-tight">{matiere.nom}</h1>
      {erreur && <p className="mt-3 text-sm" style={{ color: '#B02631' }}>{erreur}</p>}

      <div className="mt-7 grid grid-cols-2 gap-6">
        <div>
          <h2 className="text-sm font-bold">Notions</h2>
          {estProprietaire && (
            <form onSubmit={ajouterNotion} className="mt-3 flex flex-col gap-2">
              <input
                type="text"
                placeholder="Terme (ex. Derivees)"
                value={terme}
                onChange={(e) => setTerme(e.target.value)}
                className="rounded-lg border px-3 py-2 text-sm outline-none"
                style={{ borderColor: 'var(--color-border-soft)', background: '#FCFBF9' }}
              />
              <textarea
                placeholder="Definition"
                value={definition}
                onChange={(e) => setDefinition(e.target.value)}
                rows={2}
                className="rounded-lg border px-3 py-2 text-sm outline-none"
                style={{ borderColor: 'var(--color-border-soft)', background: '#FCFBF9' }}
              />
              <button
                type="submit"
                className="self-start rounded-lg px-3.5 py-1.5 text-xs font-semibold text-white"
                style={{ background: 'var(--color-brand)' }}
              >
                Ajouter la notion
              </button>
            </form>
          )}
          <ul className="mt-4 flex flex-col gap-2">
            {notions.map((notion) => (
              <li key={notion.id} className="rounded-lg border bg-white px-3.5 py-2.5 text-sm" style={{ borderColor: 'var(--color-border-soft)' }}>
                <p className="font-semibold">{notion.terme}</p>
                <p className="mt-0.5 text-xs" style={{ color: 'var(--color-ink-muted)' }}>{notion.definition}</p>
              </li>
            ))}
            {notions.length === 0 && (
              <li className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Aucune notion pour le moment.</li>
            )}
          </ul>
        </div>

        <div>
          <h2 className="text-sm font-bold">Seances</h2>
          {estProprietaire && (
            <form onSubmit={ajouterSeance} className="mt-3 flex gap-2">
              <input
                type="text"
                placeholder="Titre de la seance"
                value={titreSeance}
                onChange={(e) => setTitreSeance(e.target.value)}
                className="flex-1 rounded-lg border px-3 py-2 text-sm outline-none"
                style={{ borderColor: 'var(--color-border-soft)', background: '#FCFBF9' }}
              />
              <button
                type="submit"
                className="rounded-lg px-3.5 py-2 text-xs font-semibold text-white"
                style={{ background: 'var(--color-brand)' }}
              >
                Creer
              </button>
            </form>
          )}
          <ul className="mt-4 flex flex-col gap-2">
            {seances.map((seance) => (
              <li key={seance.id}>
                <Link
                  to={`/seances/${seance.id}`}
                  className="block rounded-lg border bg-white px-3.5 py-2.5 text-sm font-semibold transition-all hover:-translate-y-px hover:shadow-md"
                  style={{ borderColor: 'var(--color-border-soft)' }}
                >
                  {seance.titre}
                </Link>
              </li>
            ))}
            {seances.length === 0 && (
              <li className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Aucune seance pour le moment.</li>
            )}
          </ul>
        </div>
      </div>
    </div>
  )
}

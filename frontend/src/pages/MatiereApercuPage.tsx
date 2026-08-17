import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  creerNotion,
  creerSeance,
  demarrerTutoratMatiere,
  listerNotionsParMatiere,
  listerSeancesParMatiere,
  obtenirCouloir,
  obtenirMatiere,
  supprimerNotion,
  supprimerSeance,
} from '../api'
import { MatiereSousNav } from '../components/MatiereSousNav'
import type { Couloir, Matiere, Notion, Seance } from '../types'

export function MatiereApercuPage() {
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
  const [discussionLibreEnCours, setDiscussionLibreEnCours] = useState(false)

  async function rafraichir() {
    if (!matiereId) return
    setChargement(true)
    try {
      const m = await obtenirMatiere(matiereId)
      const [c, n, s] = await Promise.all([
        obtenirCouloir(m.couloirId),
        listerNotionsParMatiere(matiereId),
        listerSeancesParMatiere(matiereId),
      ])
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

  async function retirerNotion(notion: Notion) {
    if (!matiereId) return
    if (!window.confirm(`Supprimer la notion "${notion.terme}" ?`)) return
    setErreur(null)
    try {
      await supprimerNotion(matiereId, notion.id)
      await rafraichir()
    } catch {
      setErreur('Impossible de supprimer la notion.')
    }
  }

  async function retirerSeance(seance: Seance) {
    if (!window.confirm(`Supprimer la seance "${seance.titre}" ?`)) return
    setErreur(null)
    try {
      await supprimerSeance(seance.id)
      await rafraichir()
    } catch {
      setErreur('Impossible de supprimer la seance.')
    }
  }

  // Discussion libre sur toute la matiere (pas rattachee a une seance
  // precise) : accès rapide pour une question ponctuelle, distinct du
  // tutorat structure (notions cochees) d'une seance -- voir
  // SeanceDetailPage.
  async function demarrerDiscussionLibre() {
    if (!matiereId) return
    setErreur(null)
    setDiscussionLibreEnCours(true)
    try {
      const resultat = await demarrerTutoratMatiere(matiereId)
      navigate(`/tutorat/${resultat.seanceTutoratId}`)
    } catch {
      setErreur('Impossible de demarrer la discussion libre.')
      setDiscussionLibreEnCours(false)
    }
  }

  if (chargement || !matiere || !couloir) {
    return <p className="p-6 text-center text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>
  }

  return (
    <div className="mx-auto max-w-[900px] px-8 py-10">
      <MatiereSousNav matiere={matiere} couloir={couloir} actif="" />
      {erreur && <p className="mt-4 text-sm" style={{ color: '#B02631' }}>{erreur}</p>}

      <button
        type="button"
        onClick={() => void demarrerDiscussionLibre()}
        disabled={discussionLibreEnCours}
        className="mt-5 rounded-lg border px-3.5 py-2 text-xs font-semibold disabled:opacity-60"
        style={{ borderColor: 'var(--color-border-soft)', color: 'var(--color-ink-muted)' }}
      >
        {discussionLibreEnCours ? 'Ouverture...' : 'Discussion libre sur cette matiere'}
      </button>

      <div className="mt-6 grid grid-cols-2 gap-6">
        <div>
          <h2 className="text-sm font-bold">Notions</h2>
          <form
            onSubmit={ajouterNotion}
            className="mt-3 flex flex-col gap-2.5 rounded-xl border p-3.5"
            style={{ borderColor: 'var(--color-border-soft)', background: '#FBFAF7' }}
          >
            <div>
              <label className="mb-1 block text-[11px] font-semibold" style={{ color: 'var(--color-ink-muted)' }}>Terme</label>
              <input
                type="text"
                placeholder="ex. Derivees"
                value={terme}
                onChange={(e) => setTerme(e.target.value)}
                className="w-full rounded-lg border px-3 py-2 text-sm outline-none"
                style={{ borderColor: 'var(--color-border-soft)', background: '#fff' }}
              />
            </div>
            <div>
              <label className="mb-1 block text-[11px] font-semibold" style={{ color: 'var(--color-ink-muted)' }}>Definition</label>
              <textarea
                placeholder="Definition de la notion"
                value={definition}
                onChange={(e) => setDefinition(e.target.value)}
                rows={2}
                className="w-full rounded-lg border px-3 py-2 text-sm outline-none"
                style={{ borderColor: 'var(--color-border-soft)', background: '#fff' }}
              />
            </div>
            <button
              type="submit"
              className="self-start rounded-lg px-3.5 py-1.5 text-xs font-semibold text-white"
              style={{ background: 'var(--color-brand)' }}
            >
              Ajouter la notion
            </button>
          </form>
          <ul className="mt-4 flex flex-col gap-2">
            {notions.map((notion) => (
              <li
                key={notion.id}
                className="group flex items-start justify-between gap-3 rounded-xl border bg-white px-3.5 py-3 text-sm transition-all hover:-translate-y-px hover:shadow-md"
                style={{ borderColor: 'var(--color-border-soft)' }}
              >
                <div className="min-w-0">
                  <p className="font-semibold">{notion.terme}</p>
                  <p className="mt-0.5 text-xs leading-relaxed" style={{ color: 'var(--color-ink-muted)' }}>{notion.definition}</p>
                </div>
                <button
                  type="button"
                  onClick={() => void retirerNotion(notion)}
                  title="Supprimer cette notion"
                  className="flex-none rounded-full px-2 py-0.5 text-xs font-semibold opacity-0 transition-opacity group-hover:opacity-100"
                  style={{ color: '#B02631' }}
                >
                  &times;
                </button>
              </li>
            ))}
            {notions.length === 0 && (
              <li className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Aucune notion pour le moment.</li>
            )}
          </ul>
        </div>

        <div>
          <h2 className="text-sm font-bold">Seances</h2>
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
          <ul className="mt-4 flex flex-col gap-2">
            {seances.map((seance) => (
              <li
                key={seance.id}
                className="group flex items-center gap-2 rounded-lg border bg-white px-3.5 py-2.5 text-sm transition-all hover:-translate-y-px hover:shadow-md"
                style={{ borderColor: 'var(--color-border-soft)' }}
              >
                <Link to={`/seances/${seance.id}`} className="flex-1 font-semibold">
                  {seance.titre}
                </Link>
                <button
                  type="button"
                  onClick={() => void retirerSeance(seance)}
                  title="Supprimer cette seance"
                  className="flex-none rounded-full px-2 py-0.5 text-xs font-semibold opacity-0 transition-opacity group-hover:opacity-100"
                  style={{ color: '#B02631' }}
                >
                  &times;
                </button>
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

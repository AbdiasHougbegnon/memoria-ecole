import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  creerNotion,
  creerSeance,
  listerDocumentsMatiere,
  listerNotionsCandidates,
  listerNotionsParMatiere,
  listerSeancesParMatiere,
  obtenirCouloir,
  obtenirMatiere,
  rejeterNotionCandidate,
  televerserDocumentMatiere,
  validerNotionCandidate,
} from '../api'
import { obtenirUtilisateurIdConnecte } from '../auth'
import type { Couloir, DocumentMatiere, Matiere, Notion, NotionCandidate, Seance } from '../types'

const INTERVALLE_POLLING_DOCUMENTS_MS = 4000

export function MatiereDetailPage() {
  const { id: matiereId } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [matiere, setMatiere] = useState<Matiere | null>(null)
  const [couloir, setCouloir] = useState<Couloir | null>(null)
  const [notions, setNotions] = useState<Notion[]>([])
  const [seances, setSeances] = useState<Seance[]>([])
  const [documents, setDocuments] = useState<DocumentMatiere[]>([])
  const [candidates, setCandidates] = useState<NotionCandidate[]>([])
  const [candidatsEdites, setCandidatsEdites] = useState<Record<string, { terme: string; definition: string }>>({})
  const [chargement, setChargement] = useState(true)
  const [televersementEnCours, setTeleversementEnCours] = useState(false)

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
      const [c, n, s, d, cd] = await Promise.all([
        obtenirCouloir(m.couloirId),
        listerNotionsParMatiere(matiereId),
        listerSeancesParMatiere(matiereId),
        listerDocumentsMatiere(matiereId),
        listerNotionsCandidates(matiereId),
      ])
      setMatiere(m)
      setCouloir(c)
      setNotions(n)
      setSeances(s)
      setDocuments(d)
      const enAttente = cd.filter((candidate) => candidate.statut === 'EN_ATTENTE')
      setCandidates(enAttente)
      setCandidatsEdites((prev) => {
        const next: Record<string, { terme: string; definition: string }> = {}
        enAttente.forEach((candidate) => {
          next[candidate.id] = prev[candidate.id] ?? { terme: candidate.terme, definition: candidate.definition }
        })
        return next
      })
    } finally {
      setChargement(false)
    }
  }

  useEffect(() => {
    void rafraichir()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [matiereId])

  // L'extraction (Document Intelligence) puis la generation des candidats
  // (Azure OpenAI) sont asynchrones cote serveur -- environ 9s observees en
  // conditions reelles. Tant qu'un document reste EN_ATTENTE, on reinterroge
  // periodiquement plutot que de laisser l'ecran fige sur l'etat televerse.
  useEffect(() => {
    if (!matiereId) return
    if (!documents.some((d) => d.statut === 'EN_ATTENTE')) return

    let annule = false
    const intervalle = window.setInterval(() => {
      Promise.all([listerDocumentsMatiere(matiereId), listerNotionsCandidates(matiereId)])
        .then(([d, cd]) => {
          if (annule) return
          setDocuments(d)
          const enAttente = cd.filter((candidate) => candidate.statut === 'EN_ATTENTE')
          setCandidates(enAttente)
          setCandidatsEdites((prev) => {
            const next: Record<string, { terme: string; definition: string }> = {}
            enAttente.forEach((candidate) => {
              next[candidate.id] = prev[candidate.id] ?? { terme: candidate.terme, definition: candidate.definition }
            })
            return next
          })
        })
        .catch(() => {})
    }, INTERVALLE_POLLING_DOCUMENTS_MS)

    return () => {
      annule = true
      window.clearInterval(intervalle)
    }
  }, [matiereId, documents])

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

  async function televerserFiche(e: React.ChangeEvent<HTMLInputElement>) {
    const fichier = e.target.files?.[0]
    e.target.value = ''
    if (!matiereId || !fichier) return
    setErreur(null)
    setTeleversementEnCours(true)
    try {
      await televerserDocumentMatiere(matiereId, fichier)
      await rafraichir()
    } catch {
      setErreur('Impossible de televerser la fiche.')
    } finally {
      setTeleversementEnCours(false)
    }
  }

  async function validerCandidate(candidate: NotionCandidate) {
    if (!matiereId) return
    const edite = candidatsEdites[candidate.id] ?? { terme: candidate.terme, definition: candidate.definition }
    if (!edite.terme.trim() || !edite.definition.trim()) return
    setErreur(null)
    try {
      await validerNotionCandidate(matiereId, candidate.id, edite.terme.trim(), edite.definition.trim())
      await rafraichir()
    } catch {
      setErreur('Impossible de valider la notion proposee.')
    }
  }

  async function rejeterCandidate(candidate: NotionCandidate) {
    if (!matiereId) return
    setErreur(null)
    try {
      await rejeterNotionCandidate(matiereId, candidate.id)
      await rafraichir()
    } catch {
      setErreur('Impossible de rejeter la proposition.')
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

      {estProprietaire && (
        <div className="mt-8 border-t pt-7" style={{ borderColor: 'var(--color-border-soft)' }}>
          <h2 className="text-sm font-bold">Contenu documentaire</h2>
          <p className="mt-1 text-xs" style={{ color: 'var(--color-ink-muted)' }}>
            Televersez une fiche de cours, d&apos;exercices ou d&apos;epreuve (PDF ou photo) : des notions candidates
            en sont extraites automatiquement, a valider ou rejeter ci-dessous avant qu&apos;elles n&apos;entrent
            dans le suivi de maitrise des etudiants.
          </p>

          <div className="mt-3 flex items-center gap-3">
            <label
              className="cursor-pointer rounded-lg px-3.5 py-1.5 text-xs font-semibold text-white"
              style={{ background: 'var(--color-brand)', opacity: televersementEnCours ? 0.6 : 1 }}
            >
              {televersementEnCours ? 'Televersement...' : 'Televerser une fiche'}
              <input
                type="file"
                accept=".pdf,image/*"
                onChange={televerserFiche}
                disabled={televersementEnCours}
                className="hidden"
              />
            </label>
          </div>

          <ul className="mt-3 flex flex-col gap-2">
            {documents.map((document) => (
              <li
                key={document.id}
                className="flex items-center justify-between rounded-lg border bg-white px-3.5 py-2 text-sm"
                style={{ borderColor: 'var(--color-border-soft)' }}
              >
                <span>{document.nomFichier}</span>
                <span className="text-xs" style={{ color: 'var(--color-ink-muted)' }}>{document.statut}</span>
              </li>
            ))}
            {documents.length === 0 && (
              <li className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Aucune fiche televersee pour le moment.</li>
            )}
          </ul>

          <h3 className="mt-6 text-xs font-bold uppercase tracking-wide" style={{ color: 'var(--color-ink-muted)' }}>
            Notions proposees
          </h3>
          <ul className="mt-3 flex flex-col gap-3">
            {candidates.map((candidate) => {
              const edite = candidatsEdites[candidate.id] ?? { terme: candidate.terme, definition: candidate.definition }
              return (
                <li
                  key={candidate.id}
                  className="flex flex-col gap-2 rounded-lg border bg-white px-3.5 py-3 text-sm"
                  style={{ borderColor: 'var(--color-border-soft)' }}
                >
                  <input
                    type="text"
                    value={edite.terme}
                    onChange={(e) => setCandidatsEdites((prev) => ({ ...prev, [candidate.id]: { ...edite, terme: e.target.value } }))}
                    className="rounded-lg border px-3 py-2 text-sm outline-none"
                    style={{ borderColor: 'var(--color-border-soft)', background: '#FCFBF9' }}
                  />
                  <textarea
                    value={edite.definition}
                    onChange={(e) => setCandidatsEdites((prev) => ({ ...prev, [candidate.id]: { ...edite, definition: e.target.value } }))}
                    rows={2}
                    className="rounded-lg border px-3 py-2 text-sm outline-none"
                    style={{ borderColor: 'var(--color-border-soft)', background: '#FCFBF9' }}
                  />
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => void validerCandidate(candidate)}
                      className="rounded-lg px-3.5 py-1.5 text-xs font-semibold text-white"
                      style={{ background: 'var(--color-brand)' }}
                    >
                      Valider
                    </button>
                    <button
                      type="button"
                      onClick={() => void rejeterCandidate(candidate)}
                      className="rounded-lg border px-3.5 py-1.5 text-xs font-semibold"
                      style={{ borderColor: 'var(--color-border-soft)' }}
                    >
                      Rejeter
                    </button>
                  </div>
                </li>
              )
            })}
            {candidates.length === 0 && (
              <li className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Aucune notion proposee pour le moment.</li>
            )}
          </ul>
        </div>
      )}
    </div>
  )
}

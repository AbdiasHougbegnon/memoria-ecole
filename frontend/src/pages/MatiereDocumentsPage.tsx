import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  listerDocumentsMatiere,
  listerNotionsCandidates,
  obtenirCouloir,
  obtenirMatiere,
  rejeterNotionCandidate,
  televerserDocumentMatiere,
  validerNotionCandidate,
} from '../api'
import { MatiereSousNav } from '../components/MatiereSousNav'
import type { Couloir, DocumentMatiere, Matiere, NotionCandidate } from '../types'

const INTERVALLE_POLLING_DOCUMENTS_MS = 4000

function formaterTaille(octets: number): string {
  if (octets < 1024) return `${octets} o`
  if (octets < 1024 * 1024) return `${(octets / 1024).toFixed(1)} Ko`
  return `${(octets / (1024 * 1024)).toFixed(1)} Mo`
}

export function MatiereDocumentsPage() {
  const { id: matiereId } = useParams<{ id: string }>()
  const [matiere, setMatiere] = useState<Matiere | null>(null)
  const [couloir, setCouloir] = useState<Couloir | null>(null)
  const [documents, setDocuments] = useState<DocumentMatiere[]>([])
  const [candidates, setCandidates] = useState<NotionCandidate[]>([])
  const [candidatsEdites, setCandidatsEdites] = useState<Record<string, { terme: string; definition: string }>>({})
  const [chargement, setChargement] = useState(true)
  const [televersementEnCours, setTeleversementEnCours] = useState(false)
  const [fichierEnAttente, setFichierEnAttente] = useState<File | null>(null)
  const [erreur, setErreur] = useState<string | null>(null)

  const doublonsPotentiels = fichierEnAttente
    ? documents.filter((d) => d.nomFichier === fichierEnAttente.name)
    : []

  async function rafraichir() {
    if (!matiereId) return
    setChargement(true)
    try {
      const m = await obtenirMatiere(matiereId)
      const [c, d, cd] = await Promise.all([
        obtenirCouloir(m.couloirId),
        listerDocumentsMatiere(matiereId),
        listerNotionsCandidates(matiereId),
      ])
      setMatiere(m)
      setCouloir(c)
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

  async function effectuerUpload(fichier: File) {
    if (!matiereId) return
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

  // Verification du nom (et taille affichee pour comparaison, voir
  // formaterTaille) avant tout envoi reseau : la liste des documents est deja
  // en memoire, pas besoin d'un aller-retour serveur supplementaire. En cas
  // de doublon potentiel, on ne bloque pas l'envoi -- on demande confirmation
  // (voir doublonsPotentiels / confirmerEnvoiMalgreDoublon).
  async function televerserFiche(e: React.ChangeEvent<HTMLInputElement>) {
    const fichier = e.target.files?.[0]
    e.target.value = ''
    if (!fichier) return

    const dejaPresent = documents.some((d) => d.nomFichier === fichier.name)
    if (dejaPresent) {
      setFichierEnAttente(fichier)
      return
    }

    await effectuerUpload(fichier)
  }

  async function confirmerEnvoiMalgreDoublon() {
    if (!fichierEnAttente) return
    const fichier = fichierEnAttente
    setFichierEnAttente(null)
    await effectuerUpload(fichier)
  }

  function annulerEnvoi() {
    setFichierEnAttente(null)
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
      <MatiereSousNav matiere={matiere} couloir={couloir} actif="/documents" />
      {erreur && <p className="mt-4 text-sm" style={{ color: '#B02631' }}>{erreur}</p>}

      <div className="mt-6">
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

        {fichierEnAttente && (
          <div className="mt-3 rounded-lg border p-3.5 text-sm" style={{ borderColor: '#E8CB86', background: '#FBF4E2' }}>
            <p className="font-semibold">Il semblerait que ce fichier existe deja dans cette matiere.</p>
            <ul className="mt-2 flex flex-col gap-1 text-xs" style={{ color: 'var(--color-ink-muted)' }}>
              {doublonsPotentiels.map((d) => (
                <li key={d.id}>Deja televerse : {d.nomFichier} ({formaterTaille(d.taille)}, {new Date(d.dateCreation).toLocaleDateString('fr-FR')})</li>
              ))}
              <li>Nouveau fichier : {fichierEnAttente.name} ({formaterTaille(fichierEnAttente.size)})</li>
            </ul>
            <div className="mt-3 flex gap-2">
              <button
                type="button"
                onClick={() => void confirmerEnvoiMalgreDoublon()}
                className="rounded-lg px-3.5 py-1.5 text-xs font-semibold text-white"
                style={{ background: 'var(--color-brand)' }}
              >
                Envoyer quand meme
              </button>
              <button
                type="button"
                onClick={annulerEnvoi}
                className="rounded-lg border px-3.5 py-1.5 text-xs font-semibold"
                style={{ borderColor: 'var(--color-border-soft)' }}
              >
                Annuler
              </button>
            </div>
          </div>
        )}

        <ul className="mt-3 flex flex-col gap-2">
          {documents.map((document) => (
            <li
              key={document.id}
              className="flex items-center justify-between rounded-lg border bg-white px-3.5 py-2 text-sm"
              style={{ borderColor: 'var(--color-border-soft)' }}
            >
              <span>{document.nomFichier}</span>
              <span className="text-xs" style={{ color: 'var(--color-ink-muted)' }}>
                {formaterTaille(document.taille)} &middot; {document.statut}
              </span>
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
    </div>
  )
}

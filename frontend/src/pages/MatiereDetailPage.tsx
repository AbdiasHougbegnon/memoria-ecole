import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  creerNotion,
  creerSeance,
  genererExercices,
  genererQcmMatiere,
  listerDocumentsMatiere,
  listerMesTravauxPapier,
  listerNotionsCandidates,
  listerNotionsParMatiere,
  listerSeancesParMatiere,
  obtenirCouloir,
  obtenirExercices,
  obtenirMaTentativeExercices,
  obtenirMaTentativeQcmMatiere,
  obtenirMatiere,
  obtenirQcmMatiere,
  rejeterNotionCandidate,
  soumettreReponsesExercices,
  soumettreTentativeQcmMatiere,
  soumettreTravailPapier,
  televerserDocumentMatiere,
  validerNotionCandidate,
} from '../api'
import { obtenirUtilisateurIdConnecte } from '../auth'
import type { Couloir, DocumentMatiere, ExerciceMatiere, Matiere, Notion, NotionCandidate, QcmMatiere, Seance, TentativeExerciceSaisieLibre, TentativeQcm, TravailPapierMatiere } from '../types'

const INTERVALLE_POLLING_DOCUMENTS_MS = 4000

// Revision progressive sur toute la matiere (phase 22c) : contrairement au QCM
// par session (genere sur une seule seance), celui-ci s'appuie sur tous les
// resumes de cours et documents deja disponibles pour la matiere -- ouvert a
// tout membre du couloir, pas seulement au proprietaire (reviser n'est pas
// modifier le contenu pedagogique).
function SectionQcmMatiere({ matiereId }: { matiereId: string }) {
  const [qcm, setQcm] = useState<QcmMatiere | null>(null)
  const [qcmEnCours, setQcmEnCours] = useState(false)
  const [erreurQcm, setErreurQcm] = useState<string | null>(null)
  const [reponses, setReponses] = useState<(number | null)[]>([])
  const [tentative, setTentative] = useState<TentativeQcm | null>(null)
  const [modeCorrige, setModeCorrige] = useState(false)
  const [validationEnCours, setValidationEnCours] = useState(false)

  useEffect(() => {
    let annule = false
    void Promise.all([obtenirQcmMatiere(matiereId), obtenirMaTentativeQcmMatiere(matiereId)]).then(([q, t]) => {
      if (annule) return
      setQcm(q)
      setTentative(t)
      if (q && t) {
        setReponses(t.reponsesChoisies)
        setModeCorrige(true)
      } else if (q) {
        setReponses(new Array(q.questions.length).fill(null))
      }
    })
    return () => {
      annule = true
    }
  }, [matiereId])

  async function genererLeQcm() {
    if (qcmEnCours) return
    setErreurQcm(null)
    setQcmEnCours(true)
    try {
      const genere = await genererQcmMatiere(matiereId)
      setQcm(genere)
      setReponses(new Array(genere.questions.length).fill(null))
      setModeCorrige(false)
    } catch {
      setErreurQcm('Impossible de generer le QCM (au moins un resume de cours ou un document doit etre disponible).')
    } finally {
      setQcmEnCours(false)
    }
  }

  function selectionnerReponse(indexQuestion: number, indexChoix: number) {
    if (modeCorrige) return
    setReponses((precedent) => precedent.map((reponse, i) => (i === indexQuestion ? indexChoix : reponse)))
  }

  async function valider() {
    if (validationEnCours || reponses.some((reponse) => reponse === null)) return
    setValidationEnCours(true)
    try {
      setTentative(await soumettreTentativeQcmMatiere(matiereId, reponses as number[]))
      setModeCorrige(true)
    } finally {
      setValidationEnCours(false)
    }
  }

  function recommencer() {
    if (!qcm) return
    setReponses(new Array(qcm.questions.length).fill(null))
    setModeCorrige(false)
  }

  return (
    <div className="mt-8 border-t pt-7" style={{ borderColor: 'var(--color-border-soft)' }}>
      <h2 className="text-sm font-bold">QCM de revision (toute la matiere)</h2>
      <p className="mt-1 text-xs" style={{ color: 'var(--color-ink-muted)' }}>
        Genere a partir de tous les resumes de cours et documents deja disponibles pour cette matiere -- pour reviser
        progressivement, pas juste le dernier cours.
      </p>

      {!qcm && (
        <div className="mt-3">
          <button
            onClick={() => void genererLeQcm()}
            disabled={qcmEnCours}
            className="rounded-lg px-3.5 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
            style={{ background: 'var(--color-brand)' }}
          >
            {qcmEnCours ? 'Generation en cours...' : 'Generer le QCM de matiere'}
          </button>
          {erreurQcm && <p className="mt-2 text-sm" style={{ color: '#B02631' }}>{erreurQcm}</p>}
        </div>
      )}

      {qcm && qcm.statut === 'ECHEC' && (
        <p className="mt-3 text-sm" style={{ color: '#B02631' }}>La generation du QCM a echoue.</p>
      )}

      {qcm && qcm.statut === 'REUSSI' && (
        <>
          {modeCorrige && tentative && (
            <p className="mt-3 text-sm font-semibold">Score : {tentative.score}/{tentative.nombreQuestions}</p>
          )}
          <div className="mt-3 flex flex-col gap-3">
            {qcm.questions.map((question, indexQuestion) => (
              <div key={indexQuestion} className="rounded-lg border bg-white p-3.5 text-sm" style={{ borderColor: 'var(--color-border-soft)' }}>
                <p className="mb-2 font-semibold">{question.enonce}</p>
                <div className="flex flex-col gap-1.5">
                  {question.choix.map((choix, indexChoix) => {
                    const selectionne = reponses[indexQuestion] === indexChoix
                    const estCorrect = indexChoix === question.reponseCorrecte
                    let couleur: string | undefined
                    let suffixe = ''
                    if (modeCorrige) {
                      if (estCorrect) {
                        couleur = '#2E9E6B'
                        suffixe = ' ✓'
                      } else if (selectionne) {
                        couleur = '#B02631'
                        suffixe = ' ✗'
                      }
                    }
                    return (
                      <label key={indexChoix} className="flex items-center gap-2 text-sm" style={{ color: couleur }}>
                        <input
                          type="radio"
                          name={`qcm-matiere-${matiereId}-${indexQuestion}`}
                          checked={selectionne}
                          disabled={modeCorrige}
                          onChange={() => selectionnerReponse(indexQuestion, indexChoix)}
                        />
                        {choix}
                        {suffixe}
                      </label>
                    )
                  })}
                </div>
                {modeCorrige && (
                  <p className="mt-2 text-xs" style={{ color: 'var(--color-ink-muted)' }}>{question.explication}</p>
                )}
              </div>
            ))}
          </div>
          <div className="mt-3">
            {modeCorrige ? (
              <button
                onClick={recommencer}
                className="rounded-lg border px-3.5 py-1.5 text-xs font-semibold"
                style={{ borderColor: 'var(--color-border-soft)' }}
              >
                Recommencer
              </button>
            ) : (
              <button
                onClick={() => void valider()}
                disabled={validationEnCours || reponses.some((reponse) => reponse === null)}
                className="rounded-lg px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
                style={{ background: 'var(--color-brand)' }}
              >
                {validationEnCours ? 'Validation en cours...' : 'Valider mes reponses'}
              </button>
            )}
          </div>
        </>
      )}
    </div>
  )
}

const LIBELLE_NIVEAU: Record<string, string> = {
  NON_ABORDEE: 'Pas encore acquis',
  EN_COURS: 'En cours d’acquisition',
  MAITRISEE: 'Acquis',
}

const COULEUR_NIVEAU: Record<string, string> = {
  NON_ABORDEE: '#B02631',
  EN_COURS: '#C99A2E',
  MAITRISEE: '#2E9E6B',
}

// Exercices a reponse libre sur toute la matiere (phase 22d) : questions
// ouvertes, chaque reponse est notee qualitativement par l'IA (comme
// NiveauMaitrise) plutot que par un score -- pas de "bonne/mauvaise reponse"
// binaire comme pour le QCM.
function SectionExerciceSaisieLibre({ matiereId }: { matiereId: string }) {
  const [exercice, setExercice] = useState<ExerciceMatiere | null>(null)
  const [exerciceEnCours, setExerciceEnCours] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)
  const [reponses, setReponses] = useState<string[]>([])
  const [tentative, setTentative] = useState<TentativeExerciceSaisieLibre | null>(null)
  const [modeCorrige, setModeCorrige] = useState(false)
  const [validationEnCours, setValidationEnCours] = useState(false)

  useEffect(() => {
    let annule = false
    void Promise.all([obtenirExercices(matiereId), obtenirMaTentativeExercices(matiereId)]).then(([ex, t]) => {
      if (annule) return
      setExercice(ex)
      setTentative(t)
      if (ex && t) {
        setReponses(t.reponses.map((r) => r.reponse))
        setModeCorrige(true)
      } else if (ex) {
        setReponses(new Array(ex.questions.length).fill(''))
      }
    })
    return () => {
      annule = true
    }
  }, [matiereId])

  async function genererLExercice() {
    if (exerciceEnCours) return
    setErreur(null)
    setExerciceEnCours(true)
    try {
      const genere = await genererExercices(matiereId)
      setExercice(genere)
      setReponses(new Array(genere.questions.length).fill(''))
      setModeCorrige(false)
    } catch {
      setErreur('Impossible de generer les exercices (au moins un resume de cours ou un document doit etre disponible).')
    } finally {
      setExerciceEnCours(false)
    }
  }

  async function valider() {
    if (validationEnCours || reponses.some((reponse) => !reponse.trim())) return
    setValidationEnCours(true)
    setErreur(null)
    try {
      setTentative(await soumettreReponsesExercices(matiereId, reponses))
      setModeCorrige(true)
    } catch {
      setErreur("Impossible d'evaluer les reponses pour le moment.")
    } finally {
      setValidationEnCours(false)
    }
  }

  function recommencer() {
    if (!exercice) return
    setReponses(new Array(exercice.questions.length).fill(''))
    setModeCorrige(false)
  }

  return (
    <div className="mt-8 border-t pt-7" style={{ borderColor: 'var(--color-border-soft)' }}>
      <h2 className="text-sm font-bold">Exercices a reponse libre</h2>
      <p className="mt-1 text-xs" style={{ color: 'var(--color-ink-muted)' }}>
        Questions ouvertes generees a partir de tous les resumes de cours et documents de la matiere, redigees
        librement et evaluees par l'IA -- pas de choix multiple.
      </p>

      {!exercice && (
        <div className="mt-3">
          <button
            onClick={() => void genererLExercice()}
            disabled={exerciceEnCours}
            className="rounded-lg px-3.5 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
            style={{ background: 'var(--color-brand)' }}
          >
            {exerciceEnCours ? 'Generation en cours...' : 'Generer des exercices'}
          </button>
          {erreur && <p className="mt-2 text-sm" style={{ color: '#B02631' }}>{erreur}</p>}
        </div>
      )}

      {exercice && exercice.statut === 'ECHEC' && (
        <p className="mt-3 text-sm" style={{ color: '#B02631' }}>La generation des exercices a echoue.</p>
      )}

      {exercice && exercice.statut === 'REUSSI' && (
        <>
          <div className="mt-3 flex flex-col gap-3">
            {exercice.questions.map((question, index) => {
              const evaluation = tentative?.reponses[index]
              return (
                <div key={index} className="rounded-lg border bg-white p-3.5 text-sm" style={{ borderColor: 'var(--color-border-soft)' }}>
                  <p className="mb-2 font-semibold">{question.enonce}</p>
                  <textarea
                    value={reponses[index] ?? ''}
                    onChange={(e) => setReponses((prev) => prev.map((r, i) => (i === index ? e.target.value : r)))}
                    disabled={modeCorrige}
                    rows={3}
                    placeholder="Ta reponse..."
                    className="w-full rounded-lg border px-3 py-2 text-sm outline-none disabled:opacity-70"
                    style={{ borderColor: 'var(--color-border-soft)', background: modeCorrige ? '#F4F2EE' : '#FCFBF9' }}
                  />
                  {modeCorrige && evaluation && (
                    <div className="mt-2 rounded-lg p-2.5" style={{ background: '#F4F2EE' }}>
                      <span
                        className="text-xs font-semibold"
                        style={{ color: evaluation.niveau ? COULEUR_NIVEAU[evaluation.niveau] : 'var(--color-ink-muted)' }}
                      >
                        {evaluation.niveau ? LIBELLE_NIVEAU[evaluation.niveau] : 'Evaluation indisponible'}
                      </span>
                      <p className="mt-1 text-xs" style={{ color: 'var(--color-ink-muted)' }}>{evaluation.retour}</p>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
          <div className="mt-3">
            {modeCorrige ? (
              <button
                onClick={recommencer}
                className="rounded-lg border px-3.5 py-1.5 text-xs font-semibold"
                style={{ borderColor: 'var(--color-border-soft)' }}
              >
                Recommencer
              </button>
            ) : (
              <button
                onClick={() => void valider()}
                disabled={validationEnCours || reponses.some((reponse) => !reponse.trim())}
                className="rounded-lg px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
                style={{ background: 'var(--color-brand)' }}
              >
                {validationEnCours ? 'Evaluation en cours...' : 'Soumettre mes reponses'}
              </button>
            )}
          </div>
          {erreur && <p className="mt-2 text-sm" style={{ color: '#B02631' }}>{erreur}</p>}
        </>
      )}
    </div>
  )
}

const LIBELLE_STATUT_TRAVAIL: Record<string, string> = {
  EN_ATTENTE: 'Analyse en cours...',
  REUSSI: 'Analyse terminee',
  ECHEC: "Echec de l'analyse",
}

// Travail papier photographie (phase 22e) : personnel a l'etudiant qui le
// soumet (pas partage avec le reste de la classe, contrairement au contenu
// documentaire de l'enseignant plus haut sur cette page) -- alimente ses
// propres conversations avec le tuteur, voir docs/phases/phase-22-tutorat-progressif.md.
function SectionTravailPapier({ matiereId }: { matiereId: string }) {
  const [travaux, setTravaux] = useState<TravailPapierMatiere[]>([])
  const [chargement, setChargement] = useState(true)
  const [envoiEnCours, setEnvoiEnCours] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)

  async function rafraichir() {
    setChargement(true)
    try {
      setTravaux(await listerMesTravauxPapier(matiereId))
    } finally {
      setChargement(false)
    }
  }

  useEffect(() => {
    void rafraichir()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [matiereId])

  // Meme raisonnement que le polling apres upload de fiche de cours
  // (MatiereDetailPage) : l'extraction est asynchrone cote serveur.
  useEffect(() => {
    if (!travaux.some((t) => t.statut === 'EN_ATTENTE')) return
    const intervalle = window.setInterval(() => {
      listerMesTravauxPapier(matiereId).then(setTravaux).catch(() => {})
    }, INTERVALLE_POLLING_DOCUMENTS_MS)
    return () => window.clearInterval(intervalle)
  }, [matiereId, travaux])

  async function envoyer(e: React.ChangeEvent<HTMLInputElement>) {
    const fichier = e.target.files?.[0]
    e.target.value = ''
    if (!fichier) return
    setErreur(null)
    setEnvoiEnCours(true)
    try {
      await soumettreTravailPapier(matiereId, fichier)
      await rafraichir()
    } catch {
      setErreur("Impossible d'envoyer ce travail.")
    } finally {
      setEnvoiEnCours(false)
    }
  }

  return (
    <div className="mt-8 border-t pt-7" style={{ borderColor: 'var(--color-border-soft)' }}>
      <h2 className="text-sm font-bold">Mon travail papier</h2>
      <p className="mt-1 text-xs" style={{ color: 'var(--color-ink-muted)' }}>
        Photographie un exercice fait sur papier pour en discuter ensuite avec le tuteur vocal -- personnel, pas
        partage avec le reste de la classe.
      </p>

      <div className="mt-3">
        <label
          className="inline-block cursor-pointer rounded-lg px-3.5 py-1.5 text-xs font-semibold text-white"
          style={{ background: 'var(--color-brand)', opacity: envoiEnCours ? 0.6 : 1 }}
        >
          {envoiEnCours ? 'Envoi en cours...' : 'Envoyer une photo'}
          <input type="file" accept="image/*,.pdf" onChange={(e) => void envoyer(e)} disabled={envoiEnCours} className="hidden" />
        </label>
        {erreur && <p className="mt-2 text-sm" style={{ color: '#B02631' }}>{erreur}</p>}
      </div>

      {!chargement && (
        <ul className="mt-3 flex flex-col gap-2">
          {travaux.map((travail) => (
            <li
              key={travail.id}
              className="flex items-center justify-between rounded-lg border bg-white px-3.5 py-2 text-sm"
              style={{ borderColor: 'var(--color-border-soft)' }}
            >
              <span>{travail.nomFichier}</span>
              <span className="text-xs" style={{ color: 'var(--color-ink-muted)' }}>{LIBELLE_STATUT_TRAVAIL[travail.statut]}</span>
            </li>
          ))}
          {travaux.length === 0 && (
            <li className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Aucun travail envoye pour le moment.</li>
          )}
        </ul>
      )}
    </div>
  )
}

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

      {matiereId && <SectionQcmMatiere matiereId={matiereId} />}
      {matiereId && <SectionExerciceSaisieLibre matiereId={matiereId} />}
      {matiereId && <SectionTravailPapier matiereId={matiereId} />}
    </div>
  )
}

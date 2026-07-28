import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  genererExercices,
  genererQcmMatiere,
  listerMesTravauxPapier,
  obtenirCouloir,
  obtenirExercices,
  obtenirMaTentativeExercices,
  obtenirMaTentativeQcmMatiere,
  obtenirMatiere,
  obtenirQcmMatiere,
  soumettreReponsesExercices,
  soumettreTentativeQcmMatiere,
  soumettreTravailPapier,
} from '../api'
import { MatiereSousNav } from '../components/MatiereSousNav'
import type { Couloir, ExerciceMatiere, Matiere, QcmMatiere, TentativeExerciceSaisieLibre, TentativeQcm, TravailPapierMatiere } from '../types'

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
    <div>
      <h2 className="text-sm font-bold">QCM de revision (toute la matiere)</h2>
      <p className="mt-1 text-xs" style={{ color: 'var(--color-ink-muted)' }}>
        Genere a partir de tous les resumes de cours et documents deja disponibles pour cette matiere, en couvrant
        chaque notion au programme -- pour reviser progressivement, pas juste le dernier cours.
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
    <div>
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
// documentaire de l'enseignant) -- alimente ses propres conversations avec le
// tuteur, voir docs/phases/phase-22-tutorat-progressif.md.
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
    <div>
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

const ONGLETS_REVISION = [
  { cle: 'qcm', label: 'QCM' },
  { cle: 'exercices', label: 'Exercices' },
  { cle: 'travail-papier', label: 'Travail papier' },
] as const

type OngletRevision = (typeof ONGLETS_REVISION)[number]['cle']

export function MatiereRevisionPage() {
  const { id: matiereId } = useParams<{ id: string }>()
  const [matiere, setMatiere] = useState<Matiere | null>(null)
  const [couloir, setCouloir] = useState<Couloir | null>(null)
  const [chargement, setChargement] = useState(true)
  const [onglet, setOnglet] = useState<OngletRevision>('qcm')

  useEffect(() => {
    if (!matiereId) return
    let annule = false
    setChargement(true)
    obtenirMatiere(matiereId)
      .then(async (m) => {
        const c = await obtenirCouloir(m.couloirId)
        if (annule) return
        setMatiere(m)
        setCouloir(c)
      })
      .finally(() => {
        if (!annule) setChargement(false)
      })
    return () => {
      annule = true
    }
  }, [matiereId])

  if (chargement || !matiere || !couloir || !matiereId) {
    return <p className="p-6 text-center text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>
  }

  return (
    <div className="mx-auto max-w-[900px] px-8 py-10">
      <MatiereSousNav matiere={matiere} couloir={couloir} actif="/revision" />

      <div className="mt-6 flex gap-1.5">
        {ONGLETS_REVISION.map((o) => (
          <button
            key={o.cle}
            onClick={() => setOnglet(o.cle)}
            className="rounded-full px-3.5 py-1.5 text-xs font-semibold"
            style={
              onglet === o.cle
                ? { background: 'var(--color-brand)', color: 'white' }
                : { background: '#F4F2EE', color: 'var(--color-ink-muted)' }
            }
          >
            {o.label}
          </button>
        ))}
      </div>

      <div className="mt-6">
        {onglet === 'qcm' && <SectionQcmMatiere matiereId={matiereId} />}
        {onglet === 'exercices' && <SectionExerciceSaisieLibre matiereId={matiereId} />}
        {onglet === 'travail-papier' && <SectionTravailPapier matiereId={matiereId} />}
      </div>
    </div>
  )
}

import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { genererResume, obtenirResume, obtenirSession, obtenirTranscriptions } from '../api'
import type { Resume, ResumeType, Session, TranscriptionSegment } from '../types'

// Nombre de relectures tentees apres la fin de la session avant d'abandonner
// l'attente d'un resume (environ 15s a raison d'une tentative toutes les 3s).
const MAX_TENTATIVES_APRES_FIN = 5

// Seul le resume DETAILLE est genere automatiquement a la fin de session.
// Les autres types sont generes a la demande, la premiere fois que
// l'utilisateur ouvre l'onglet correspondant (voir afficherOnglet).
const ONGLETS_RESUME: { valeur: ResumeType; libelle: string }[] = [
  { valeur: 'DETAILLE', libelle: 'Detaille' },
  { valeur: 'COURT', libelle: 'Court' },
  { valeur: 'ACTIONS', libelle: 'Actions' },
]

function ContenuResume({ resume }: { resume: Resume }) {
  if (resume.statut === 'ECHEC') {
    return <p className="text-sm text-red-600">La generation du resume a echoue.</p>
  }
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <p className="text-sm text-slate-800">{resume.texteResume}</p>
      {resume.pointsCles.length > 0 && (
        <ul className="mt-3 list-disc pl-5 text-sm text-slate-700">
          {resume.pointsCles.map((point, index) => (
            <li key={index}>{point}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function SessionDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [session, setSession] = useState<Session | null>(null)
  const [transcriptions, setTranscriptions] = useState<TranscriptionSegment[]>([])
  const [resumesParType, setResumesParType] = useState<Partial<Record<ResumeType, Resume | null>>>({})
  const [chargement, setChargement] = useState(true)
  const [resumeAbandonne, setResumeAbandonne] = useState(false)
  const [ongletActif, setOngletActif] = useState<ResumeType>('DETAILLE')
  const [generationEnCours, setGenerationEnCours] = useState<ResumeType | null>(null)
  const [erreurGeneration, setErreurGeneration] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    let annule = false
    let tentativesApresFin = 0
    let intervalle: number

    async function charger() {
      const [s, t, r] = await Promise.all([
        obtenirSession(id!),
        obtenirTranscriptions(id!),
        obtenirResume(id!, 'DETAILLE'),
      ])
      if (annule) return

      setSession(s)
      setTranscriptions(t)
      setResumesParType((precedent) => ({ ...precedent, DETAILLE: r }))
      setChargement(false)

      if (r || s.statut === 'EN_COURS') {
        tentativesApresFin = 0
        return
      }

      // Session terminee et toujours pas de resume detaille : soit il est
      // encore en cours de generation, soit aucune transcription n'a abouti
      // et aucun resume ne sera jamais produit. On n'interroge pas
      // indefiniment.
      tentativesApresFin += 1
      if (tentativesApresFin >= MAX_TENTATIVES_APRES_FIN) {
        setResumeAbandonne(true)
        window.clearInterval(intervalle)
      }
    }

    void charger()

    intervalle = window.setInterval(() => void charger(), 3000)
    return () => {
      annule = true
      window.clearInterval(intervalle)
    }
  }, [id])

  async function afficherOnglet(type: ResumeType) {
    setOngletActif(type)
    if (type === 'DETAILLE' || resumesParType[type] !== undefined || generationEnCours === type) {
      return
    }

    setErreurGeneration(null)
    setGenerationEnCours(type)
    try {
      const resume = await genererResume(id!, type)
      setResumesParType((precedent) => ({ ...precedent, [type]: resume }))
    } catch {
      setErreurGeneration("Impossible de generer ce resume (aucune transcription disponible pour le moment ?).")
    } finally {
      setGenerationEnCours(null)
    }
  }

  if (chargement) {
    return <p className="p-8 text-sm text-slate-500">Chargement...</p>
  }

  if (!session) {
    return <p className="p-8 text-sm text-red-600">Session introuvable.</p>
  }

  const resumeOnglet = resumesParType[ongletActif]

  return (
    <div className="mx-auto max-w-2xl px-4 py-8">
      <Link to="/" className="text-sm text-slate-500 hover:underline">
        &larr; Retour aux sessions
      </Link>

      <h1 className="mt-2 mb-1 text-2xl font-semibold text-slate-900">{session.titre}</h1>
      <p className="mb-6 text-sm text-slate-500">
        {new Date(session.dateCreation).toLocaleString('fr-FR')} - {session.statut}
      </p>

      <section className="mb-8">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
          Resume
        </h2>

        <div className="mb-3 flex gap-2">
          {ONGLETS_RESUME.map(({ valeur, libelle }) => (
            <button
              key={valeur}
              onClick={() => void afficherOnglet(valeur)}
              className={`rounded-lg px-3 py-1 text-sm ${
                ongletActif === valeur
                  ? 'bg-slate-900 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {libelle}
            </button>
          ))}
        </div>

        {ongletActif === 'DETAILLE' && !resumeOnglet && (
          <p className="text-sm text-slate-500">
            {session.statut === 'EN_COURS'
              ? 'Le resume sera genere a la fin de la session.'
              : !resumeAbandonne
                ? 'Resume en cours de generation...'
                : transcriptions.some((segment) => segment.statut === 'REUSSIE')
                  ? 'La generation du resume prend plus de temps que prevu.'
                  : "Aucun resume : aucune transcription n'a abouti pour cette session."}
          </p>
        )}

        {ongletActif !== 'DETAILLE' && generationEnCours === ongletActif && (
          <p className="text-sm text-slate-500">Generation en cours...</p>
        )}
        {ongletActif !== 'DETAILLE' && generationEnCours !== ongletActif && erreurGeneration && (
          <p className="text-sm text-red-600">{erreurGeneration}</p>
        )}
        {ongletActif !== 'DETAILLE' &&
          generationEnCours !== ongletActif &&
          !erreurGeneration &&
          resumeOnglet === undefined && <p className="text-sm text-slate-500">Chargement...</p>}

        {resumeOnglet && <ContenuResume resume={resumeOnglet} />}
      </section>

      <section>
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
          Transcription
        </h2>
        {transcriptions.length === 0 && (
          <p className="text-sm text-slate-500">Aucun segment transcrit pour le moment.</p>
        )}
        <ol className="flex flex-col gap-2">
          {transcriptions.map((segment) => (
            <li
              key={segment.numeroSequence}
              className="rounded-lg border border-slate-200 bg-white p-3 text-sm"
            >
              <span className="mr-2 font-mono text-xs text-slate-400">
                #{segment.numeroSequence}
              </span>
              {segment.statut !== 'REUSSIE' ? (
                <span className="italic text-red-600">Echec de la transcription</span>
              ) : segment.segmentsLocuteur.length > 0 ? (
                <div className="mt-1 flex flex-col gap-1">
                  {segment.segmentsLocuteur.map((locuteur, index) => (
                    <p key={index}>
                      <span className="mr-2 font-medium text-slate-500">
                        Intervenant {locuteur.locuteur}
                      </span>
                      <span className="text-slate-800">{locuteur.texte}</span>
                    </p>
                  ))}
                </div>
              ) : (
                <span className="text-slate-800">{segment.texte}</span>
              )}
            </li>
          ))}
        </ol>
      </section>
    </div>
  )
}

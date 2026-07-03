import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { obtenirResume, obtenirSession, obtenirTranscriptions } from '../api'
import type { Resume, Session, TranscriptionSegment } from '../types'

// Nombre de relectures tentees apres la fin de la session avant d'abandonner
// l'attente d'un resume (environ 15s a raison d'une tentative toutes les 3s).
const MAX_TENTATIVES_APRES_FIN = 5

export function SessionDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [session, setSession] = useState<Session | null>(null)
  const [transcriptions, setTranscriptions] = useState<TranscriptionSegment[]>([])
  const [resume, setResume] = useState<Resume | null>(null)
  const [chargement, setChargement] = useState(true)
  const [resumeAbandonne, setResumeAbandonne] = useState(false)

  useEffect(() => {
    if (!id) return
    let annule = false
    let tentativesApresFin = 0
    let intervalle: number

    async function charger() {
      const [s, t, r] = await Promise.all([
        obtenirSession(id!),
        obtenirTranscriptions(id!),
        obtenirResume(id!),
      ])
      if (annule) return

      setSession(s)
      setTranscriptions(t)
      setResume(r)
      setChargement(false)

      if (r) {
        // Resume obtenu (succes ou echec) : plus rien de nouveau a attendre.
        window.clearInterval(intervalle)
        return
      }

      if (s.statut === 'EN_COURS') {
        tentativesApresFin = 0
        return
      }

      // Session terminee et toujours pas de resume : soit il est encore en
      // cours de generation, soit aucune transcription n'a abouti et aucun
      // resume ne sera jamais produit. On n'interroge pas indefiniment.
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

  if (chargement) {
    return <p className="p-8 text-sm text-slate-500">Chargement...</p>
  }

  if (!session) {
    return <p className="p-8 text-sm text-red-600">Session introuvable.</p>
  }

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
        {!resume && (
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
        {resume?.statut === 'ECHEC' && (
          <p className="text-sm text-red-600">La generation du resume a echoue.</p>
        )}
        {resume?.statut === 'REUSSI' && (
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
        )}
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
              {segment.statut === 'REUSSIE' ? (
                <span className="text-slate-800">{segment.texte}</span>
              ) : (
                <span className="italic text-red-600">Echec de la transcription</span>
              )}
            </li>
          ))}
        </ol>
      </section>
    </div>
  )
}

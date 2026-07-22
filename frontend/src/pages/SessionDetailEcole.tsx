import { useEffect, useState } from 'react'
import { genererResumeCours, obtenirResumeCours } from '../api'
import type { ResumeCours } from '../types'

export function SessionDetailEcole({ sessionId }: { sessionId: string }) {
  const [resumeCours, setResumeCours] = useState<ResumeCours | null>(null)
  const [resumeCoursEnCours, setResumeCoursEnCours] = useState(false)
  const [erreurResumeCours, setErreurResumeCours] = useState<string | null>(null)

  useEffect(() => {
    let annule = false
    void obtenirResumeCours(sessionId).then((rc) => {
      if (!annule) setResumeCours(rc)
    })
    return () => {
      annule = true
    }
  }, [sessionId])

  async function genererLeResumeCours() {
    if (resumeCoursEnCours) return
    setErreurResumeCours(null)
    setResumeCoursEnCours(true)
    try {
      setResumeCours(await genererResumeCours(sessionId))
    } catch {
      setErreurResumeCours(
        "Impossible de generer le resume de cours (aucune transcription disponible pour le moment ?).",
      )
    } finally {
      setResumeCoursEnCours(false)
    }
  }

  return (
    <section className="mb-8">
      <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
        Resume de cours
      </h2>

      {!resumeCours && (
        <button
          onClick={() => void genererLeResumeCours()}
          disabled={resumeCoursEnCours}
          className="rounded-lg bg-slate-100 px-3 py-1 text-sm text-slate-600 hover:bg-slate-200"
        >
          {resumeCoursEnCours ? 'Generation en cours...' : 'Generer le resume de cours'}
        </button>
      )}

      {erreurResumeCours && <p className="mt-2 text-sm text-red-600">{erreurResumeCours}</p>}

      {resumeCours && resumeCours.statut === 'ECHEC' && (
        <p className="text-sm text-red-600">La generation du resume de cours a echoue.</p>
      )}

      {resumeCours && resumeCours.statut === 'REUSSI' && (
        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <p className="text-sm text-slate-800">{resumeCours.synthese}</p>

          {resumeCours.notions.length > 0 && (
            <>
              <h3 className="mt-4 mb-1 text-xs font-medium uppercase tracking-wide text-slate-500">
                Notions
              </h3>
              <ul className="flex flex-col gap-1 text-sm text-slate-700">
                {resumeCours.notions.map((notion, index) => (
                  <li key={index}>
                    <span className="font-medium">{notion.terme}</span> : {notion.definition}
                  </li>
                ))}
              </ul>
            </>
          )}

          {resumeCours.pointsARevoir.length > 0 && (
            <>
              <h3 className="mt-4 mb-1 text-xs font-medium uppercase tracking-wide text-slate-500">
                Points a revoir
              </h3>
              <ul className="list-disc pl-5 text-sm text-slate-700">
                {resumeCours.pointsARevoir.map((point, index) => (
                  <li key={index}>{point}</li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}
    </section>
  )
}

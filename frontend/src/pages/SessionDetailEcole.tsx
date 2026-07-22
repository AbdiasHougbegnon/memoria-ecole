import { useEffect, useState } from 'react'
import { genererResumeCours, obtenirResumeCours } from '../api'
import type { ResumeCours } from '../types'
import { BoutonSecondaire, SectionTitre } from './SessionDetailPage'

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
    <>
      {!resumeCours && (
        <section>
          <BoutonSecondaire onClick={() => void genererLeResumeCours()} disabled={resumeCoursEnCours}>
            {resumeCoursEnCours ? 'Generation en cours...' : 'Generer le resume de cours'}
          </BoutonSecondaire>
          {erreurResumeCours && <p className="mt-2 text-sm" style={{ color: '#B02631' }}>{erreurResumeCours}</p>}
        </section>
      )}

      {resumeCours && resumeCours.statut === 'ECHEC' && (
        <p className="text-sm" style={{ color: '#B02631' }}>La generation du resume de cours a echoue.</p>
      )}

      {resumeCours && resumeCours.statut === 'REUSSI' && (
        <>
          {resumeCours.synthese && (
            <section>
              <SectionTitre>Resume de cours</SectionTitre>
              <div
                className="rounded-2xl border p-5"
                style={{ borderColor: '#E4E2F6', background: 'linear-gradient(180deg,#F6F5FE,#FBFAFE)' }}
              >
                <p className="text-sm leading-relaxed">{resumeCours.synthese}</p>
              </div>
            </section>
          )}

          {resumeCours.notions.length > 0 && (
            <section>
              <SectionTitre>Notions</SectionTitre>
              <div className="flex flex-col gap-2">
                {resumeCours.notions.map((notion, index) => (
                  <div key={index} className="rounded-xl border bg-white p-3.5 text-sm" style={{ borderColor: 'var(--color-border-soft)' }}>
                    <span className="font-semibold">{notion.terme}</span>
                    <span style={{ color: 'var(--color-ink-muted)' }}> : {notion.definition}</span>
                  </div>
                ))}
              </div>
            </section>
          )}

          {resumeCours.pointsARevoir.length > 0 && (
            <section>
              <SectionTitre>Points a revoir</SectionTitre>
              <ul className="list-disc rounded-2xl border bg-white p-4 pl-8 text-sm" style={{ borderColor: 'var(--color-border-soft)', color: 'var(--color-ink-muted)' }}>
                {resumeCours.pointsARevoir.map((point, index) => (
                  <li key={index}>{point}</li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
    </>
  )
}

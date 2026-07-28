import { useEffect, useState } from 'react'
import {
  genererQcm,
  genererResumeCours,
  obtenirMaTentativeQcm,
  obtenirQcm,
  obtenirResumeCours,
  soumettreTentativeQcm,
  telechargerResumeCours,
} from '../api'
import type { Qcm, ResumeCours, TentativeQcm } from '../types'
import { BoutonSecondaire, Carte, SectionTitre } from './SessionDetailPage'

function SectionQcm({ sessionId }: { sessionId: string }) {
  const [qcm, setQcm] = useState<Qcm | null>(null)
  const [qcmEnCours, setQcmEnCours] = useState(false)
  const [erreurQcm, setErreurQcm] = useState<string | null>(null)
  const [reponses, setReponses] = useState<(number | null)[]>([])
  const [tentative, setTentative] = useState<TentativeQcm | null>(null)
  const [modeCorrige, setModeCorrige] = useState(false)
  const [validationEnCours, setValidationEnCours] = useState(false)

  useEffect(() => {
    let annule = false
    void Promise.all([obtenirQcm(sessionId), obtenirMaTentativeQcm(sessionId)]).then(([q, t]) => {
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
  }, [sessionId])

  async function genererLeQcm() {
    if (qcmEnCours) return
    setErreurQcm(null)
    setQcmEnCours(true)
    try {
      const genere = await genererQcm(sessionId)
      setQcm(genere)
      setReponses(new Array(genere.questions.length).fill(null))
      setModeCorrige(false)
    } catch {
      setErreurQcm('Impossible de generer le QCM (le resume de cours doit etre genere en premier).')
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
      setTentative(await soumettreTentativeQcm(sessionId, reponses as number[]))
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

  if (!qcm) {
    return (
      <section>
        <SectionTitre>QCM de revision</SectionTitre>
        <BoutonSecondaire onClick={() => void genererLeQcm()} disabled={qcmEnCours}>
          {qcmEnCours ? 'Generation en cours...' : 'Generer le QCM'}
        </BoutonSecondaire>
        {erreurQcm && <p className="mt-2 text-sm" style={{ color: '#B02631' }}>{erreurQcm}</p>}
      </section>
    )
  }

  if (qcm.statut === 'ECHEC') {
    return (
      <section>
        <SectionTitre>QCM de revision</SectionTitre>
        <p className="text-sm" style={{ color: '#B02631' }}>La generation du QCM a echoue.</p>
      </section>
    )
  }

  return (
    <section>
      <SectionTitre>QCM de revision</SectionTitre>
      {modeCorrige && tentative && (
        <p className="mb-3 text-sm font-semibold">Score : {tentative.score}/{tentative.nombreQuestions}</p>
      )}
      <div className="flex flex-col gap-3">
        {qcm.questions.map((question, indexQuestion) => (
          <Carte key={indexQuestion}>
            <p className="mb-2 text-sm font-semibold">{question.enonce}</p>
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
                      name={`qcm-${sessionId}-${indexQuestion}`}
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
              <p className="mt-2 text-sm" style={{ color: 'var(--color-ink-muted)' }}>{question.explication}</p>
            )}
          </Carte>
        ))}
      </div>
      <div className="mt-3">
        {modeCorrige ? (
          <BoutonSecondaire onClick={recommencer}>Recommencer</BoutonSecondaire>
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
    </section>
  )
}

interface SessionDetailEcoleProps {
  sessionId: string
  onVoirSources: (numeros: number[]) => void
}

export function SessionDetailEcole({ sessionId, onVoirSources }: SessionDetailEcoleProps) {
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

  async function telechargerLeResumeCours() {
    setErreurResumeCours(null)
    try {
      const blob = await telechargerResumeCours(sessionId)
      const url = URL.createObjectURL(blob)
      const lien = document.createElement('a')
      lien.href = url
      lien.download = `resume-cours-${sessionId}.txt`
      lien.click()
      URL.revokeObjectURL(url)
    } catch {
      setErreurResumeCours('Impossible de telecharger la fiche de resume.')
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
                <div className="mt-3 flex flex-wrap items-center gap-4">
                  {resumeCours.segmentsSources.length > 0 && (
                    <button
                      onClick={() => onVoirSources(resumeCours.segmentsSources)}
                      className="text-xs font-semibold"
                      style={{ color: 'var(--color-brand)' }}
                    >
                      Voir les {resumeCours.segmentsSources.length} passage{resumeCours.segmentsSources.length > 1 ? 's' : ''} source
                    </button>
                  )}
                  <button
                    onClick={() => void telechargerLeResumeCours()}
                    className="text-xs font-semibold"
                    style={{ color: 'var(--color-ink-muted)' }}
                  >
                    Telecharger la fiche
                  </button>
                </div>
                {erreurResumeCours && <p className="mt-2 text-sm" style={{ color: '#B02631' }}>{erreurResumeCours}</p>}
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

          <SectionQcm sessionId={sessionId} />
        </>
      )}
    </>
  )
}

import { useEffect, useState } from 'react'
import {
  confirmerEngagement,
  genererCompteRendu,
  listerEngagementsSession,
  obtenirCompteRendu,
  rejeterEngagement,
  terminerEngagement,
} from '../api'
import type { CompteRendu, Engagement } from '../types'

const LIBELLE_STATUT_ENGAGEMENT: Record<Engagement['statut'], string> = {
  EN_ATTENTE: 'A confirmer',
  CONFIRME: 'Confirme',
  REJETE: 'Rejete',
  TERMINE: 'Termine',
}

export function SessionDetailEntreprise({ sessionId }: { sessionId: string }) {
  const [compteRendu, setCompteRendu] = useState<CompteRendu | null>(null)
  const [compteRenduEnCours, setCompteRenduEnCours] = useState(false)
  const [erreurCompteRendu, setErreurCompteRendu] = useState<string | null>(null)
  const [engagements, setEngagements] = useState<Engagement[]>([])
  const [engagementEnCours, setEngagementEnCours] = useState<string | null>(null)

  useEffect(() => {
    let annule = false
    void Promise.all([obtenirCompteRendu(sessionId), listerEngagementsSession(sessionId)]).then(([cr, eng]) => {
      if (annule) return
      setCompteRendu(cr)
      setEngagements(eng)
    })
    return () => {
      annule = true
    }
  }, [sessionId])

  async function genererLeCompteRendu() {
    if (compteRenduEnCours) return
    setErreurCompteRendu(null)
    setCompteRenduEnCours(true)
    try {
      setCompteRendu(await genererCompteRendu(sessionId))
    } catch {
      setErreurCompteRendu(
        "Impossible de generer le compte rendu (aucune transcription disponible pour le moment ?).",
      )
    } finally {
      setCompteRenduEnCours(false)
    }
  }

  async function agirSurEngagement(engagementId: string, action: (id: string) => Promise<Engagement>) {
    setEngagementEnCours(engagementId)
    try {
      const misAJour = await action(engagementId)
      setEngagements((precedent) => precedent.map((e) => (e.id === engagementId ? misAJour : e)))
    } finally {
      setEngagementEnCours(null)
    }
  }

  return (
    <>
      <section className="mb-8">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
          Compte rendu complet
        </h2>

        {!compteRendu && (
          <button
            onClick={() => void genererLeCompteRendu()}
            disabled={compteRenduEnCours}
            className="rounded-lg bg-slate-100 px-3 py-1 text-sm text-slate-600 hover:bg-slate-200"
          >
            {compteRenduEnCours ? 'Generation en cours...' : 'Generer le compte rendu complet'}
          </button>
        )}

        {erreurCompteRendu && <p className="mt-2 text-sm text-red-600">{erreurCompteRendu}</p>}

        {compteRendu && compteRendu.statut === 'ECHEC' && (
          <p className="text-sm text-red-600">La generation du compte rendu a echoue.</p>
        )}

        {compteRendu && compteRendu.statut === 'REUSSI' && (
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="text-sm text-slate-800">{compteRendu.synthese}</p>

            {compteRendu.decisions.length > 0 && (
              <>
                <h3 className="mt-4 mb-1 text-xs font-medium uppercase tracking-wide text-slate-500">
                  Decisions
                </h3>
                <ul className="list-disc pl-5 text-sm text-slate-700">
                  {compteRendu.decisions.map((decision, index) => (
                    <li key={index}>{decision}</li>
                  ))}
                </ul>
              </>
            )}

            {compteRendu.actions.length > 0 && (
              <>
                <h3 className="mt-4 mb-1 text-xs font-medium uppercase tracking-wide text-slate-500">
                  Actions
                </h3>
                <ul className="flex flex-col gap-1 text-sm text-slate-700">
                  {compteRendu.actions.map((action, index) => (
                    <li key={index}>
                      {action.description}
                      {action.responsable && (
                        <span className="ml-2 text-xs text-slate-500">({action.responsable})</span>
                      )}
                      {action.echeance && (
                        <span className="ml-2 text-xs text-slate-400">- {action.echeance}</span>
                      )}
                    </li>
                  ))}
                </ul>
              </>
            )}
          </div>
        )}
      </section>

      {engagements.length > 0 && (
        <section className="mb-8">
          <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
            Engagements
          </h2>
          <ul className="flex flex-col gap-2">
            {engagements.map((engagement) => (
              <li key={engagement.id} className="rounded-lg border border-slate-200 bg-white p-3 text-sm">
                <div className="mb-1 flex items-start justify-between gap-2">
                  <p className="text-slate-800">{engagement.description}</p>
                  <span className="shrink-0 text-xs font-medium text-slate-500">
                    {LIBELLE_STATUT_ENGAGEMENT[engagement.statut]}
                  </span>
                </div>
                {(engagement.responsable || engagement.echeance) && (
                  <p className="mb-2 text-xs text-slate-500">
                    {engagement.responsable && <span>{engagement.responsable}</span>}
                    {engagement.responsable && engagement.echeance && <span> - </span>}
                    {engagement.echeance && <span>{engagement.echeance}</span>}
                  </p>
                )}
                {engagement.statut === 'EN_ATTENTE' && (
                  <div className="flex gap-2">
                    <button
                      disabled={engagementEnCours === engagement.id}
                      onClick={() => void agirSurEngagement(engagement.id, confirmerEngagement)}
                      className="rounded-lg bg-slate-900 px-3 py-1 text-xs font-medium text-white hover:bg-slate-700 disabled:opacity-50"
                    >
                      Confirmer
                    </button>
                    <button
                      disabled={engagementEnCours === engagement.id}
                      onClick={() => void agirSurEngagement(engagement.id, rejeterEngagement)}
                      className="rounded-lg bg-slate-100 px-3 py-1 text-xs text-slate-600 hover:bg-slate-200 disabled:opacity-50"
                    >
                      Rejeter
                    </button>
                  </div>
                )}
                {engagement.statut === 'CONFIRME' && (
                  <button
                    disabled={engagementEnCours === engagement.id}
                    onClick={() => void agirSurEngagement(engagement.id, terminerEngagement)}
                    className="rounded-lg bg-slate-100 px-3 py-1 text-xs text-slate-600 hover:bg-slate-200 disabled:opacity-50"
                  >
                    Marquer comme termine
                  </button>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  )
}

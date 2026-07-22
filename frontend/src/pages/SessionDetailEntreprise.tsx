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
import { BoutonSecondaire, SectionTitre } from './SessionDetailPage'

const PILL_STATUT_ENGAGEMENT: Record<Engagement['statut'], { label: string; bg: string; color: string }> = {
  EN_ATTENTE: { label: 'A confirmer', bg: 'var(--color-warn-wash)', color: 'var(--color-warn)' },
  CONFIRME: { label: 'Confirme', bg: 'var(--color-brand-wash)', color: 'var(--color-brand)' },
  REJETE: { label: 'Rejete', bg: '#F4F2EE', color: 'var(--color-ink-muted)' },
  TERMINE: { label: 'Termine', bg: 'var(--color-ok-wash)', color: 'var(--color-ok)' },
}

const PALETTE_AVATAR = ['#4B46D6', '#2F6FB0', '#E0662D', '#2E9E6B', '#9B4DCA', '#C99A2E', '#B0472F']
function couleurAvatar(nom: string | null): string {
  if (!nom) return '#9A968E'
  let h = 0
  for (let i = 0; i < nom.length; i++) h = (h * 31 + nom.charCodeAt(i)) >>> 0
  return PALETTE_AVATAR[h % PALETTE_AVATAR.length]
}
function initiales(nom: string | null): string {
  if (!nom) return '?'
  return nom.trim().split(/\s+/).map((m) => m[0]).slice(0, 2).join('').toUpperCase()
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
      {!compteRendu && (
        <section>
          <BoutonSecondaire onClick={() => void genererLeCompteRendu()} disabled={compteRenduEnCours}>
            {compteRenduEnCours ? 'Generation en cours...' : 'Generer le compte rendu complet'}
          </BoutonSecondaire>
          {erreurCompteRendu && <p className="mt-2 text-sm" style={{ color: '#B02631' }}>{erreurCompteRendu}</p>}
        </section>
      )}

      {compteRendu && compteRendu.statut === 'ECHEC' && (
        <p className="text-sm" style={{ color: '#B02631' }}>La generation du compte rendu a echoue.</p>
      )}

      {compteRendu && compteRendu.statut === 'REUSSI' && compteRendu.synthese && (
        <section>
          <SectionTitre>Compte rendu complet</SectionTitre>
          <div
            className="rounded-2xl border p-5"
            style={{ borderColor: '#E4E2F6', background: 'linear-gradient(180deg,#F6F5FE,#FBFAFE)' }}
          >
            <p className="text-sm leading-relaxed">{compteRendu.synthese}</p>
          </div>
        </section>
      )}

      {compteRendu && compteRendu.statut === 'REUSSI' && compteRendu.decisions.length > 0 && (
        <section>
          <SectionTitre>Decisions</SectionTitre>
          <div className="flex flex-col gap-2">
            {compteRendu.decisions.map((decision, index) => (
              <div key={index} className="flex items-start gap-3 rounded-xl border bg-white p-3.5" style={{ borderColor: 'var(--color-border-soft)' }}>
                <span
                  className="flex h-[22px] w-[22px] flex-none items-center justify-center rounded-md text-[11.5px] font-bold"
                  style={{ background: 'var(--color-brand-wash)', color: 'var(--color-brand)', fontFamily: 'var(--font-mono)' }}
                >
                  {index + 1}
                </span>
                <span className="pt-px text-sm">{decision}</span>
              </div>
            ))}
          </div>
        </section>
      )}

      {engagements.length > 0 && (
        <section>
          <SectionTitre>Taches extraites</SectionTitre>
          <div className="overflow-hidden rounded-2xl border bg-white" style={{ borderColor: 'var(--color-border-soft)' }}>
            <div
              className="grid gap-3 px-4 py-2.5 text-[11px] font-bold uppercase tracking-wide"
              style={{ gridTemplateColumns: '150px 1fr auto', background: '#FAF9F6', borderBottom: '1px solid var(--color-border-softer)', color: 'var(--color-ink-faint)' }}
            >
              <span>Responsable</span>
              <span>Tache</span>
              <span className="text-right">Statut</span>
            </div>
            {engagements.map((engagement, index) => (
              <div
                key={engagement.id}
                className="grid items-center gap-3 px-4 py-3"
                style={index > 0 ? { gridTemplateColumns: '150px 1fr auto', borderTop: '1px solid var(--color-border-softer)' } : { gridTemplateColumns: '150px 1fr auto' }}
              >
                <span className="flex min-w-0 items-center gap-2">
                  <span
                    className="flex h-6 w-6 flex-none items-center justify-center rounded-full text-[10px] font-semibold text-white"
                    style={{ background: couleurAvatar(engagement.responsable) }}
                  >
                    {initiales(engagement.responsable)}
                  </span>
                  <span className="truncate text-[13px] font-semibold">{engagement.responsable ?? 'Non identifie'}</span>
                </span>
                <div>
                  <div className="text-[13.5px]">{engagement.description}</div>
                  {engagement.echeance && (
                    <div className="mt-0.5 text-xs" style={{ color: 'var(--color-ink-faint)' }}>{engagement.echeance}</div>
                  )}
                  {engagement.statut === 'EN_ATTENTE' && (
                    <div className="mt-2 flex gap-2">
                      <button
                        disabled={engagementEnCours === engagement.id}
                        onClick={() => void agirSurEngagement(engagement.id, confirmerEngagement)}
                        className="rounded-lg px-2.5 py-1 text-xs font-semibold text-white disabled:opacity-50"
                        style={{ background: 'var(--color-brand)' }}
                      >
                        Confirmer
                      </button>
                      <button
                        disabled={engagementEnCours === engagement.id}
                        onClick={() => void agirSurEngagement(engagement.id, rejeterEngagement)}
                        className="rounded-lg px-2.5 py-1 text-xs font-semibold"
                        style={{ background: '#F4F2EE', color: 'var(--color-ink-muted)' }}
                      >
                        Rejeter
                      </button>
                    </div>
                  )}
                  {engagement.statut === 'CONFIRME' && (
                    <button
                      disabled={engagementEnCours === engagement.id}
                      onClick={() => void agirSurEngagement(engagement.id, terminerEngagement)}
                      className="mt-2 rounded-lg px-2.5 py-1 text-xs font-semibold"
                      style={{ background: '#F4F2EE', color: 'var(--color-ink-muted)' }}
                    >
                      Marquer comme termine
                    </button>
                  )}
                </div>
                <span
                  className="flex-none rounded-full px-2.5 py-1 text-[11px] font-bold"
                  style={{ background: PILL_STATUT_ENGAGEMENT[engagement.statut].bg, color: PILL_STATUT_ENGAGEMENT[engagement.statut].color }}
                >
                  {PILL_STATUT_ENGAGEMENT[engagement.statut].label}
                </span>
              </div>
            ))}
          </div>
        </section>
      )}
    </>
  )
}

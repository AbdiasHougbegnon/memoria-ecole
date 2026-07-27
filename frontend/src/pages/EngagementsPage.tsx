import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  confirmerEngagement,
  listerEngagements,
  planifierEcheanceEngagement,
  rejeterEngagement,
  terminerEngagement,
} from '../api'
import type { Engagement, StatutEngagement } from '../types'

function versValeurInputDatetime(dateEcheance: string | null): string {
  if (!dateEcheance) return ''
  return dateEcheance.slice(0, 16)
}

const COLONNES: { statut: StatutEngagement; label: string; dot: string }[] = [
  { statut: 'EN_ATTENTE', label: 'A confirmer', dot: 'var(--color-warn)' },
  { statut: 'CONFIRME', label: 'Confirmes', dot: 'var(--color-brand)' },
  { statut: 'TERMINE', label: 'Termines', dot: 'var(--color-ok)' },
  { statut: 'REJETE', label: 'Rejetes', dot: 'var(--color-ink-faint-2)' },
]

function initiales(nom: string | null): string {
  if (!nom) return '?'
  const mots = nom.trim().split(/\s+/)
  return mots.map((m) => m[0]).slice(0, 2).join('').toUpperCase()
}

// Couleur d'avatar stable par responsable (meme palette que le prototype).
const PALETTE_AVATAR = ['#4B46D6', '#2F6FB0', '#E0662D', '#2E9E6B', '#9B4DCA', '#C99A2E', '#B0472F']
function couleurAvatar(nom: string | null): string {
  if (!nom) return '#9A968E'
  let h = 0
  for (let i = 0; i < nom.length; i++) h = (h * 31 + nom.charCodeAt(i)) >>> 0
  return PALETTE_AVATAR[h % PALETTE_AVATAR.length]
}

function badgeEcheance(engagement: Engagement): { texte: string; bg: string; color: string } | null {
  if (engagement.dateEcheance) {
    const enRetard = new Date(engagement.dateEcheance) < new Date() && engagement.statut === 'CONFIRME'
    return {
      texte: new Date(engagement.dateEcheance).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' }),
      bg: enRetard ? 'var(--color-live-wash)' : '#F4F2EE',
      color: enRetard ? 'var(--color-live)' : 'var(--color-ink-muted)',
    }
  }
  if (engagement.echeance) {
    return { texte: engagement.echeance, bg: '#F4F2EE', color: 'var(--color-ink-muted)' }
  }
  return null
}

export function EngagementsPage() {
  const [engagements, setEngagements] = useState<Engagement[]>([])
  const [chargement, setChargement] = useState(true)
  const [engagementOuvert, setEngagementOuvert] = useState<string | null>(null)
  const [enCours, setEnCours] = useState<string | null>(null)
  const [echeances, setEcheances] = useState<Record<string, string>>({})
  const [erreur, setErreur] = useState<string | null>(null)

  async function rafraichir() {
    setChargement(true)
    try {
      const resultat = await listerEngagements()
      setEngagements(resultat)
      setEcheances(Object.fromEntries(resultat.map((e) => [e.id, versValeurInputDatetime(e.dateEcheance)])))
    } finally {
      setChargement(false)
    }
  }

  useEffect(() => {
    void rafraichir()
  }, [])

  async function agir(id: string, action: (id: string) => Promise<Engagement>) {
    setErreur(null)
    setEnCours(id)
    try {
      const misAJour = await action(id)
      setEngagements((precedent) => precedent.map((e) => (e.id === id ? misAJour : e)))
    } catch {
      setErreur("Impossible de mettre a jour l'engagement.")
    } finally {
      setEnCours(null)
    }
  }

  async function gererPlanificationEcheance(id: string) {
    const valeur = echeances[id]
    if (!valeur) return
    setErreur(null)
    setEnCours(id)
    try {
      const misAJour = await planifierEcheanceEngagement(id, new Date(valeur).toISOString())
      setEngagements((precedent) => precedent.map((e) => (e.id === id ? misAJour : e)))
    } catch {
      setErreur("Impossible de programmer le rappel.")
    } finally {
      setEnCours(null)
    }
  }

  const engagementDetail = engagements.find((e) => e.id === engagementOuvert) ?? null

  return (
    <div className="mx-auto max-w-[1180px] px-8 py-10">
      <h1 className="text-[28px] font-bold tracking-tight">Suivi des engagements</h1>
      <p className="mt-1.5 max-w-xl text-sm" style={{ color: 'var(--color-ink-muted)' }}>
        Chaque tache extraite d'une reunion suit un cycle de vie -- de la detection par l'IA jusqu'a sa cloture.
        Cliquez sur un engagement pour le confirmer ou le rejeter.
      </p>

      {chargement && <p className="mt-8 text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>}
      {!chargement && engagements.length === 0 && (
        <p className="mt-8 text-sm" style={{ color: 'var(--color-ink-muted)' }}>
          Aucun engagement pour le moment. Ils sont extraits automatiquement quand tu generes le compte rendu
          complet d'une session.
        </p>
      )}

      <div className="mt-7 flex items-start gap-3.5 overflow-x-auto pb-3.5">
        {COLONNES.map((colonne) => {
          const items = engagements.filter((e) => e.statut === colonne.statut)
          return (
            <div key={colonne.statut} className="w-64 flex-none">
              <div className="mb-3 flex items-center gap-2 px-0.5">
                <span className="inline-block h-2 w-2 rounded-full" style={{ background: colonne.dot }} />
                <span className="text-xs font-bold uppercase tracking-wide" style={{ color: 'var(--color-ink)' }}>{colonne.label}</span>
                <span className="text-xs" style={{ fontFamily: 'var(--font-mono)', color: 'var(--color-ink-faint)' }}>{items.length}</span>
              </div>
              <div className="flex flex-col gap-2.5">
                {items.map((engagement) => {
                  const badge = badgeEcheance(engagement)
                  return (
                    <button
                      key={engagement.id}
                      onClick={() => { setErreur(null); setEngagementOuvert(engagement.id) }}
                      className="flex flex-col gap-3 rounded-2xl border bg-white p-3.5 text-left transition-all hover:-translate-y-px hover:shadow-md"
                      style={{ borderColor: 'var(--color-border-soft)' }}
                    >
                      <div className="text-[13.5px] font-semibold leading-tight">{engagement.description}</div>
                      <div className="flex items-center justify-between gap-2">
                        <span className="flex min-w-0 items-center gap-1.5">
                          <span
                            className="flex h-6 w-6 flex-none items-center justify-center rounded-full text-[9.5px] font-semibold text-white"
                            style={{ background: couleurAvatar(engagement.responsable) }}
                          >
                            {initiales(engagement.responsable)}
                          </span>
                          <span className="truncate text-xs" style={{ color: 'var(--color-ink-muted)' }}>
                            {engagement.responsable ?? 'Non identifie'}
                          </span>
                        </span>
                        {badge && (
                          <span
                            className="flex-none rounded px-2 py-0.5 text-[10.5px] font-medium"
                            style={{ background: badge.bg, color: badge.color, fontFamily: 'var(--font-mono)' }}
                          >
                            {badge.texte}
                          </span>
                        )}
                      </div>
                    </button>
                  )
                })}
              </div>
            </div>
          )
        })}
      </div>

      {engagementDetail && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-10"
          style={{ background: 'rgba(28,27,26,.35)' }}
          onClick={() => setEngagementOuvert(null)}
        >
          <div
            className="w-full max-w-lg overflow-hidden rounded-2xl bg-white shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center gap-3 border-b p-5" style={{ borderColor: 'var(--color-border-softer)', background: 'linear-gradient(180deg,#F6F5FE,#fff)' }}>
              <span className="flex h-9 w-9 flex-none items-center justify-center rounded-xl" style={{ background: 'var(--color-brand-wash)' }}>
                <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
                  <path d="M5 9.5 L7.5 12 L13 6" stroke="var(--color-brand)" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </span>
              <div>
                <div className="text-base font-bold">Engagement</div>
                <div className="text-xs" style={{ color: 'var(--color-ink-muted)' }}>Confirmez, rejetez ou planifiez un rappel</div>
              </div>
            </div>
            <div className="p-5">
              <div className="mb-1 text-xs font-bold uppercase tracking-wide" style={{ color: 'var(--color-ink-faint-2)' }}>Tache</div>
              <div className="mb-4 text-base font-semibold leading-snug">{engagementDetail.description}</div>

              <div className="mb-4 flex gap-3">
                <div className="flex-1 rounded-xl border p-3" style={{ borderColor: 'var(--color-border-softer)' }}>
                  <div className="mb-1.5 text-[10.5px] font-bold uppercase tracking-wide" style={{ color: 'var(--color-ink-faint-2)' }}>Responsable</div>
                  <div className="flex items-center gap-2">
                    <span className="flex h-6 w-6 items-center justify-center rounded-full text-[10px] font-semibold text-white" style={{ background: couleurAvatar(engagementDetail.responsable) }}>
                      {initiales(engagementDetail.responsable)}
                    </span>
                    <span className="text-sm font-semibold">{engagementDetail.responsable ?? 'Non identifie'}</span>
                  </div>
                </div>
                <div className="flex-1 rounded-xl border p-3" style={{ borderColor: 'var(--color-border-softer)' }}>
                  <div className="mb-1.5 text-[10.5px] font-bold uppercase tracking-wide" style={{ color: 'var(--color-ink-faint-2)' }}>Session</div>
                  <Link to={`/sessions/${engagementDetail.sessionId}`} className="text-sm font-semibold hover:underline">
                    {engagementDetail.sessionTitre}
                  </Link>
                </div>
              </div>

              {erreur && <p className="mb-4 text-sm" style={{ color: '#B02631' }}>{erreur}</p>}

              {engagementDetail.statut === 'CONFIRME' && (
                <div className="mb-4 flex items-center gap-2">
                  <input
                    type="datetime-local"
                    value={echeances[engagementDetail.id] ?? ''}
                    onChange={(e) => setEcheances((precedent) => ({ ...precedent, [engagementDetail.id]: e.target.value }))}
                    className="rounded-md border px-2 py-1 text-xs"
                    style={{ borderColor: 'var(--color-border-soft)' }}
                  />
                  <button
                    disabled={enCours === engagementDetail.id || !echeances[engagementDetail.id]}
                    onClick={() => void gererPlanificationEcheance(engagementDetail.id)}
                    className="rounded-lg px-3 py-1.5 text-xs font-semibold disabled:opacity-50"
                    style={{ background: '#F4F2EE', color: 'var(--color-ink-muted)' }}
                  >
                    Programmer un rappel
                  </button>
                </div>
              )}

              <div className="flex gap-2.5">
                {engagementDetail.statut === 'EN_ATTENTE' && (
                  <>
                    <button
                      disabled={enCours === engagementDetail.id}
                      onClick={() => void agir(engagementDetail.id, confirmerEngagement)}
                      className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-white disabled:opacity-50"
                      style={{ background: 'var(--color-brand)' }}
                    >
                      Confirmer
                    </button>
                    <button
                      disabled={enCours === engagementDetail.id}
                      onClick={() => void agir(engagementDetail.id, rejeterEngagement)}
                      className="flex-none rounded-lg px-4 py-2.5 text-sm font-semibold"
                      style={{ border: '1px solid var(--color-border-soft)', color: '#B02631' }}
                    >
                      Refuser
                    </button>
                  </>
                )}
                {engagementDetail.statut === 'CONFIRME' && (
                  <button
                    disabled={enCours === engagementDetail.id}
                    onClick={() => void agir(engagementDetail.id, terminerEngagement)}
                    className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-white disabled:opacity-50"
                    style={{ background: 'var(--color-brand)' }}
                  >
                    Marquer comme termine
                  </button>
                )}
                <button
                  onClick={() => setEngagementOuvert(null)}
                  className="flex-none rounded-lg px-4 py-2.5 text-sm font-semibold"
                  style={{ border: '1px solid var(--color-border-soft)', color: 'var(--color-ink-muted)' }}
                >
                  Fermer
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

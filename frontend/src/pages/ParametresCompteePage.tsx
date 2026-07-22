import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  enregistrerEmpreinteVocale,
  obtenirEmpreinteVocale,
  obtenirMonCompte,
  renseignerNom,
  revoquerEmpreinteVocale,
} from '../api'
import { deconnecter, obtenirEmailConnecte } from '../auth'
import { convertirBlobEnWav } from '../audioWav'
import type { EmpreinteVocale, StatutEmpreinteVocale } from '../types'

const DUREE_ENROLEMENT_MS = 20_000
const TYPE_MIME_PREFERE = 'audio/webm;codecs=opus'

const LIBELLE_STATUT: Record<StatutEmpreinteVocale, string> = {
  EN_ATTENTE: 'En cours de traitement...',
  PRETE: 'Active',
  ECHEC: "Echec de l'enrolement -- reessaie",
}

export function ParametresCompteePage() {
  const [nom, setNom] = useState('')
  const [editionNom, setEditionNom] = useState(false)
  const [nomEnregistre, setNomEnregistre] = useState(false)
  const [empreinte, setEmpreinte] = useState<EmpreinteVocale | null>(null)
  const [consentement, setConsentement] = useState(false)
  const [enregistrementEnCours, setEnregistrementEnCours] = useState(false)
  const [secondesRestantes, setSecondesRestantes] = useState(0)
  const [envoiEnCours, setEnvoiEnCours] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)
  const navigate = useNavigate()
  const email = obtenirEmailConnecte() ?? ''

  const streamRef = useRef<MediaStream | null>(null)
  const recorderRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])
  const minuteurRef = useRef<number | null>(null)

  useEffect(() => {
    obtenirMonCompte().then((c) => setNom(c.nom ?? '')).catch(() => {})
    obtenirEmpreinteVocale().then(setEmpreinte).catch(() => {})
  }, [])

  async function gererEnregistrementNom() {
    if (!nom.trim()) return
    await renseignerNom(nom.trim())
    setNomEnregistre(true)
    setEditionNom(false)
    setTimeout(() => setNomEnregistre(false), 2000)
  }

  async function commencerEnrolement() {
    setErreur(null)
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      streamRef.current = stream
      const typeMime = MediaRecorder.isTypeSupported(TYPE_MIME_PREFERE) ? TYPE_MIME_PREFERE : undefined
      const recorder = new MediaRecorder(stream, typeMime ? { mimeType: typeMime } : undefined)
      recorderRef.current = recorder
      chunksRef.current = []

      recorder.ondataavailable = (evenement) => {
        if (evenement.data.size > 0) chunksRef.current.push(evenement.data)
      }
      recorder.onstop = () => {
        void terminerEnrolement()
      }

      recorder.start()
      setEnregistrementEnCours(true)
      setSecondesRestantes(DUREE_ENROLEMENT_MS / 1000)

      const debut = Date.now()
      minuteurRef.current = window.setInterval(() => {
        const ecoule = Date.now() - debut
        const restant = Math.max(0, Math.ceil((DUREE_ENROLEMENT_MS - ecoule) / 1000))
        setSecondesRestantes(restant)
        if (ecoule >= DUREE_ENROLEMENT_MS) {
          arreterEnrolement()
        }
      }, 250)
    } catch {
      setErreur("Impossible d'acceder au microphone.")
    }
  }

  function arreterEnrolement() {
    if (minuteurRef.current) {
      window.clearInterval(minuteurRef.current)
      minuteurRef.current = null
    }
    recorderRef.current?.stop()
    streamRef.current?.getTracks().forEach((piste) => piste.stop())
    streamRef.current = null
    setEnregistrementEnCours(false)
  }

  async function terminerEnrolement() {
    if (chunksRef.current.length === 0) return
    setEnvoiEnCours(true)
    try {
      const blob = new Blob(chunksRef.current, { type: chunksRef.current[0].type })
      const wav = await convertirBlobEnWav(blob)
      const resultat = await enregistrerEmpreinteVocale(wav, consentement)
      setEmpreinte(resultat)
    } catch {
      setErreur("Echec de l'envoi de l'echantillon vocal.")
    } finally {
      setEnvoiEnCours(false)
    }
  }

  async function gererRevocation() {
    if (!window.confirm('Supprimer definitivement ton empreinte vocale ?')) return
    await revoquerEmpreinteVocale()
    setEmpreinte({ id: null, statut: null, dateConsentement: null })
  }

  const empreinteActive = empreinte?.statut != null

  return (
    <div className="mx-auto max-w-[760px] px-8 py-10">
      <h1 className="text-[28px] font-bold tracking-tight">Reglages</h1>
      <p className="mt-1.5 text-sm" style={{ color: 'var(--color-ink-muted)' }}>
        Gerez votre profil et votre empreinte vocale.
      </p>

      <div className="mt-7">
        <h2 className="mb-3 text-sm font-bold">Profil</h2>
        <div className="flex items-center gap-4 rounded-2xl border bg-white p-5" style={{ borderColor: 'var(--color-border-soft)' }}>
          <span
            className="flex h-[52px] w-[52px] flex-none items-center justify-center rounded-full text-lg font-semibold text-white"
            style={{ background: 'var(--color-brand)' }}
          >
            {(nom || email).slice(0, 2).toUpperCase()}
          </span>
          <div className="min-w-0 flex-1">
            {editionNom ? (
              <div className="flex gap-2">
                <input
                  type="text"
                  value={nom}
                  onChange={(e) => setNom(e.target.value)}
                  placeholder="Ton nom"
                  autoFocus
                  className="flex-1 rounded-md border px-3 py-1.5 text-sm"
                  style={{ borderColor: 'var(--color-border-soft)' }}
                />
                <button
                  onClick={() => void gererEnregistrementNom()}
                  disabled={!nom.trim()}
                  className="rounded-md px-3 py-1.5 text-sm font-semibold text-white disabled:opacity-50"
                  style={{ background: 'var(--color-brand)' }}
                >
                  OK
                </button>
              </div>
            ) : (
              <>
                <div className="text-base font-semibold">{nomEnregistre ? 'Enregistre !' : nom || email}</div>
                <div className="text-[13px]" style={{ color: 'var(--color-ink-faint)' }}>{email}</div>
              </>
            )}
          </div>
          {!editionNom && (
            <button
              onClick={() => setEditionNom(true)}
              className="flex-none rounded-lg border px-3.5 py-2 text-[13px] font-semibold"
              style={{ borderColor: 'var(--color-border-soft)', color: 'var(--color-ink-muted)' }}
            >
              Editer
            </button>
          )}
        </div>
      </div>

      <div className="mt-6">
        <h2 className="mb-3 text-sm font-bold">Empreinte vocale</h2>
        <div className="rounded-2xl border bg-white p-5" style={{ borderColor: 'var(--color-border-soft)' }}>
          <p className="mb-3 text-xs" style={{ color: 'var(--color-ink-muted)' }}>
            Permet a Memoria de te reconnaitre automatiquement dans les transcriptions de sessions auxquelles tu
            participes (donnee biometrique). Optionnel, revocable a tout moment. L'audio enregistre sert uniquement a
            creer une empreinte technique aupres du fournisseur d'identification -- il n'est pas conserve tel quel par
            Memoria.
          </p>

          {empreinte?.statut && (
            <p className="mb-3 text-sm">
              Statut : <span className="font-semibold">{LIBELLE_STATUT[empreinte.statut]}</span>
              {empreinte.dateConsentement && empreinte.statut === 'PRETE' && (
                <span style={{ color: 'var(--color-ink-muted)' }}> (depuis le {new Date(empreinte.dateConsentement).toLocaleDateString('fr-FR')})</span>
              )}
            </p>
          )}

          {erreur && <p className="mb-3 text-sm" style={{ color: '#B02631' }}>{erreur}</p>}

          {!enregistrementEnCours && !envoiEnCours && (
            <div className="flex flex-col gap-3">
              <label className="flex items-start gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={consentement}
                  onChange={(e) => setConsentement(e.target.checked)}
                  className="mt-0.5"
                />
                <span>
                  Je consens explicitement a l'enregistrement d'un echantillon de ma voix pour creer une empreinte
                  vocale biometrique, utilisee uniquement pour la reconnaissance de locuteur dans mes sessions.
                </span>
              </label>
              <div className="flex gap-2">
                <button
                  onClick={() => void commencerEnrolement()}
                  disabled={!consentement}
                  className="rounded-md px-3 py-1.5 text-sm font-semibold text-white disabled:opacity-50"
                  style={{ background: 'var(--color-brand)' }}
                >
                  {empreinteActive ? 'Re-enregistrer ma voix' : 'Enregistrer ma voix (~20s)'}
                </button>
                {empreinteActive && (
                  <button
                    onClick={() => void gererRevocation()}
                    className="rounded-md border px-3 py-1.5 text-sm font-semibold"
                    style={{ borderColor: '#F1C7C9', color: '#D33A40' }}
                  >
                    Supprimer mon empreinte vocale
                  </button>
                )}
              </div>
            </div>
          )}

          {enregistrementEnCours && (
            <div className="flex items-center gap-3">
              <span className="text-sm">Enregistrement en cours... {secondesRestantes}s</span>
              <button
                onClick={arreterEnrolement}
                className="rounded-md px-3 py-1 text-xs font-semibold"
                style={{ background: '#F4F2EE', color: 'var(--color-ink-muted)' }}
              >
                Arreter maintenant
              </button>
            </div>
          )}

          {envoiEnCours && <p className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Envoi de l'echantillon...</p>}
        </div>
      </div>

      <div className="mt-6">
        <button
          onClick={() => {
            deconnecter()
            navigate('/choix-module')
          }}
          className="rounded-lg border px-4 py-2.5 text-sm font-semibold"
          style={{ borderColor: '#F1D4D4', color: '#B02631' }}
        >
          Se deconnecter
        </button>
      </div>
    </div>
  )
}

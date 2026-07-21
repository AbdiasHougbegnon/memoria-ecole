import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  enregistrerEmpreinteVocale,
  obtenirEmpreinteVocale,
  obtenirMonCompte,
  renseignerNom,
  revoquerEmpreinteVocale,
} from '../api'
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
  const [nomEnregistre, setNomEnregistre] = useState(false)
  const [empreinte, setEmpreinte] = useState<EmpreinteVocale | null>(null)
  const [consentement, setConsentement] = useState(false)
  const [enregistrementEnCours, setEnregistrementEnCours] = useState(false)
  const [secondesRestantes, setSecondesRestantes] = useState(0)
  const [envoiEnCours, setEnvoiEnCours] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)

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
    <div className="mx-auto max-w-2xl px-4 py-8">
      <Link to="/" className="mb-4 inline-block text-sm text-slate-500 hover:text-slate-700">
        ← Retour aux sessions
      </Link>
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">Parametres du compte</h1>

      <div className="mb-8 rounded-lg border border-slate-200 bg-white p-4">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">Nom affiche</h2>
        <p className="mb-3 text-xs text-slate-500">
          Utilise pour t'identifier dans les transcriptions (ex: reconnaissance vocale). Sans nom, ton email est
          utilise a la place.
        </p>
        <div className="flex gap-2">
          <input
            type="text"
            value={nom}
            onChange={(e) => setNom(e.target.value)}
            placeholder="Ton nom"
            className="flex-1 rounded-md border border-slate-300 px-3 py-1.5 text-sm"
          />
          <button
            onClick={() => void gererEnregistrementNom()}
            disabled={!nom.trim()}
            className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
          >
            {nomEnregistre ? 'Enregistre !' : 'Enregistrer'}
          </button>
        </div>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-4">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">Empreinte vocale</h2>
        <p className="mb-3 text-xs text-slate-500">
          Permet a Memoria de te reconnaitre automatiquement dans les transcriptions de sessions auxquelles tu
          participes (donnee biometrique). Optionnel, revocable a tout moment. L'audio enregistre sert uniquement a
          creer une empreinte technique aupres du fournisseur d'identification -- il n'est pas conserve tel quel par
          Memoria.
        </p>

        {empreinte?.statut && (
          <p className="mb-3 text-sm">
            Statut : <span className="font-medium">{LIBELLE_STATUT[empreinte.statut]}</span>
            {empreinte.dateConsentement && empreinte.statut === 'PRETE' && (
              <span className="text-slate-500"> (depuis le {new Date(empreinte.dateConsentement).toLocaleDateString('fr-FR')})</span>
            )}
          </p>
        )}

        {erreur && <p className="mb-3 text-sm text-red-600">{erreur}</p>}

        {!enregistrementEnCours && !envoiEnCours && (
          <div className="flex flex-col gap-3">
            <label className="flex items-start gap-2 text-sm text-slate-700">
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
                className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
              >
                {empreinteActive ? 'Re-enregistrer ma voix' : 'Enregistrer ma voix (~20s)'}
              </button>
              {empreinteActive && (
                <button
                  onClick={() => void gererRevocation()}
                  className="rounded-md border border-red-300 px-3 py-1.5 text-sm font-medium text-red-600 hover:bg-red-50"
                >
                  Supprimer mon empreinte vocale
                </button>
              )}
            </div>
          </div>
        )}

        {enregistrementEnCours && (
          <div className="flex items-center gap-3">
            <span className="text-sm text-slate-700">Enregistrement en cours... {secondesRestantes}s</span>
            <button
              onClick={arreterEnrolement}
              className="rounded-md bg-slate-100 px-3 py-1 text-xs text-slate-600 hover:bg-slate-200"
            >
              Arreter maintenant
            </button>
          </div>
        )}

        {envoiEnCours && <p className="text-sm text-slate-500">Envoi de l'echantillon...</p>}
      </div>
    </div>
  )
}

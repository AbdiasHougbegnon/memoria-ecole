import { useEffect, useRef, useState } from 'react'
import { creerSession, envoyerChunk, listerCouloirs, terminerSession } from '../api'
import { convertirBlobEnWav } from '../audioWav'
import type { Couloir } from '../types'

const DUREE_SEGMENT_MS = 30_000
const TYPE_MIME_PREFERE = 'audio/webm;codecs=opus'

interface RecorderProps {
  onSessionTerminee: (sessionId: string) => void
}

export function Recorder({ onSessionTerminee }: RecorderProps) {
  const [enregistrement, setEnregistrement] = useState(false)
  const [titre, setTitre] = useState('')
  const [erreur, setErreur] = useState<string | null>(null)
  const [couloirs, setCouloirs] = useState<Couloir[]>([])
  const [couloirId, setCouloirId] = useState('')

  useEffect(() => {
    listerCouloirs().then(setCouloirs).catch(() => {})
  }, [])

  const sessionIdRef = useRef<string | null>(null)
  const numeroChunkRef = useRef(0)
  const streamRef = useRef<MediaStream | null>(null)
  const recorderRef = useRef<MediaRecorder | null>(null)
  const minuteurRef = useRef<number | null>(null)
  const arretDemandeRef = useRef(false)
  const dernierEnvoiRef = useRef<Promise<void>>(Promise.resolve())

  function demarrerNouveauSegment() {
    const stream = streamRef.current
    if (!stream) return

    const typeMime = MediaRecorder.isTypeSupported(TYPE_MIME_PREFERE)
      ? TYPE_MIME_PREFERE
      : undefined
    const recorder = new MediaRecorder(stream, typeMime ? { mimeType: typeMime } : undefined)
    recorderRef.current = recorder

    // Sans argument, start() ne declenche ondataavailable qu'a l'arret :
    // c'est voulu, chaque instance de MediaRecorder produit un fichier
    // audio complet et independant (voir explication plus haut).
    recorder.ondataavailable = (evenement) => {
      const sessionId = sessionIdRef.current
      if (evenement.data.size === 0 || !sessionId) return
      const numero = numeroChunkRef.current
      numeroChunkRef.current += 1
      dernierEnvoiRef.current = convertirBlobEnWav(evenement.data)
        .then((wav) => envoyerChunk(sessionId, numero, wav))
        .catch((e) => {
          console.error('Echec envoi du chunk', numero, e)
        })
    }

    recorder.onstop = () => {
      if (arretDemandeRef.current) {
        void finaliser()
      } else {
        demarrerNouveauSegment()
      }
    }

    recorder.start()
    minuteurRef.current = window.setTimeout(() => {
      recorder.stop()
    }, DUREE_SEGMENT_MS)
  }

  async function finaliser() {
    streamRef.current?.getTracks().forEach((piste) => piste.stop())
    streamRef.current = null

    const sessionId = sessionIdRef.current
    setEnregistrement(false)
    if (!sessionId) return

    await dernierEnvoiRef.current
    try {
      await terminerSession(sessionId)
      onSessionTerminee(sessionId)
    } catch {
      setErreur('Impossible de terminer la session.')
    }
  }

  async function demarrer() {
    setErreur(null)
    if (!titre.trim()) {
      setErreur('Donne un titre a la session avant de demarrer.')
      return
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const { id } = await creerSession(titre.trim(), couloirId || undefined)
      sessionIdRef.current = id
      numeroChunkRef.current = 0
      streamRef.current = stream
      arretDemandeRef.current = false
      setEnregistrement(true)
      demarrerNouveauSegment()
    } catch {
      setErreur("Impossible d'acceder au microphone.")
    }
  }

  function arreter() {
    arretDemandeRef.current = true
    if (minuteurRef.current) window.clearTimeout(minuteurRef.current)
    recorderRef.current?.stop()
  }

  return (
    <div className="rounded-2xl border bg-white p-5" style={{ borderColor: 'var(--color-border-soft)', boxShadow: '0 1px 3px rgba(20,18,15,.04)' }}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <input
          type="text"
          placeholder="Titre de la session"
          value={titre}
          disabled={enregistrement}
          onChange={(e) => setTitre(e.target.value)}
          className="flex-1 rounded-lg border px-3.5 py-2.5 text-sm outline-none disabled:opacity-60"
          style={{ borderColor: 'var(--color-border-soft)', background: '#FCFBF9' }}
        />
        {couloirs.length > 0 && (
          <select
            value={couloirId}
            disabled={enregistrement}
            onChange={(e) => setCouloirId(e.target.value)}
            className="rounded-lg border px-3 py-2.5 text-sm outline-none disabled:opacity-60"
            style={{ borderColor: 'var(--color-border-soft)', background: '#FCFBF9' }}
          >
            <option value="">Aucun (personnel)</option>
            {couloirs.map((couloir) => (
              <option key={couloir.id} value={couloir.id}>
                {couloir.nom}
              </option>
            ))}
          </select>
        )}
        {!enregistrement ? (
          <button
            onClick={demarrer}
            className="flex items-center justify-center gap-2 rounded-lg px-5 py-2.5 text-sm font-semibold text-white"
            style={{ background: 'var(--color-brand)', boxShadow: '0 2px 10px rgba(75,70,214,.3)' }}
          >
            <span className="inline-block h-2 w-2 rounded-full bg-white" />
            Demarrer
          </button>
        ) : (
          <button
            onClick={arreter}
            className="flex items-center justify-center gap-2 rounded-lg px-5 py-2.5 text-sm font-semibold text-white"
            style={{ background: 'var(--color-ink)' }}
          >
            <span className="inline-block h-2.5 w-2.5 rounded-sm" style={{ background: 'var(--color-live)' }} />
            Terminer
          </button>
        )}
      </div>
      {enregistrement && (
        <p className="mt-3 flex items-center gap-2 text-sm font-semibold" style={{ color: 'var(--color-live)' }}>
          <span className="inline-block h-2 w-2 rounded-full" style={{ background: 'var(--color-live)', animation: 'mem-pulse 1.4s ease-in-out infinite' }} />
          Enregistrement en cours...
        </p>
      )}
      {erreur && <p className="mt-2 text-sm" style={{ color: '#B02631' }}>{erreur}</p>}
    </div>
  )
}

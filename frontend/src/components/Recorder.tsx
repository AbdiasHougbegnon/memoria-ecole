import { useEffect, useRef, useState } from 'react'
import { creerSession, envoyerChunk, listerCouloirs, terminerSession } from '../api'
import type { Couloir } from '../types'

const DUREE_SEGMENT_MS = 30_000
const TYPE_MIME_PREFERE = 'audio/webm;codecs=opus'

interface RecorderProps {
  onSessionTerminee: (sessionId: string) => void
}

async function convertirBlobEnWav(audio: Blob): Promise<Blob> {
  const buffer = await audio.arrayBuffer()
  const AudioContextCtor = window.AudioContext || (window as Window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext

  if (!AudioContextCtor) {
    throw new Error('AudioContext indisponible')
  }

  const contexte = new AudioContextCtor()
  try {
    const audioBuffer = await contexte.decodeAudioData(buffer.slice(0))
    const canaux = audioBuffer.numberOfChannels
    const longueur = audioBuffer.length * canaux
    const tampon = new ArrayBuffer(44 + longueur * 2)
    const vue = new DataView(tampon)

    const ecrireChaine = (offset: number, valeur: string) => {
      for (let i = 0; i < valeur.length; i += 1) {
        vue.setUint8(offset + i, valeur.charCodeAt(i))
      }
    }

    ecrireChaine(0, 'RIFF')
    vue.setUint32(4, 36 + longueur * 2, true)
    ecrireChaine(8, 'WAVE')
    ecrireChaine(12, 'fmt ')
    vue.setUint32(16, 16, true)
    vue.setUint16(20, 1, true)
    vue.setUint16(22, canaux, true)
    vue.setUint32(24, audioBuffer.sampleRate, true)
    vue.setUint32(28, audioBuffer.sampleRate * canaux * 2, true)
    vue.setUint16(32, canaux * 2, true)
    vue.setUint16(34, 16, true)
    ecrireChaine(36, 'data')
    vue.setUint32(40, longueur * 2, true)

    let offset = 44
    for (let i = 0; i < audioBuffer.length; i += 1) {
      for (let canal = 0; canal < canaux; canal += 1) {
        const sample = Math.max(-1, Math.min(1, audioBuffer.getChannelData(canal)[i]))
        vue.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
        offset += 2
      }
    }

    return new Blob([tampon], { type: 'audio/wav' })
  } finally {
    await contexte.close()
  }
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
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <input
          type="text"
          placeholder="Titre de la session"
          value={titre}
          disabled={enregistrement}
          onChange={(e) => setTitre(e.target.value)}
          className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none disabled:bg-slate-100"
        />
        {couloirs.length > 0 && (
          <select
            value={couloirId}
            disabled={enregistrement}
            onChange={(e) => setCouloirId(e.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none disabled:bg-slate-100"
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
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
          >
            Demarrer l'enregistrement
          </button>
        ) : (
          <button
            onClick={arreter}
            className="rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-500"
          >
            Arreter
          </button>
        )}
      </div>
      {enregistrement && (
        <p className="mt-2 flex items-center gap-2 text-sm text-red-600">
          <span className="h-2 w-2 animate-pulse rounded-full bg-red-600" />
          Enregistrement en cours...
        </p>
      )}
      {erreur && <p className="mt-2 text-sm text-red-600">{erreur}</p>}
    </div>
  )
}

import { useRef, useState } from 'react'

const TYPE_MIME_PREFERE = 'audio/webm;codecs=opus'

// Capture "push-to-talk" : demarrer() a l'appui, arreter() au relachement,
// resout un seul Blob par appui -- contrairement a components/Recorder.tsx
// qui decoupe un enregistrement long en segments de 30s, ce hook capture un
// seul tour de parole court. Voir docs/phases/phase-9-tuteur-vocal.md.
export function useTutorRecorder() {
  const [enregistrement, setEnregistrement] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const recorderRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])

  async function demarrer() {
    setErreur(null)
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      streamRef.current = stream
      chunksRef.current = []
      const typeMime = MediaRecorder.isTypeSupported(TYPE_MIME_PREFERE) ? TYPE_MIME_PREFERE : undefined
      const recorder = new MediaRecorder(stream, typeMime ? { mimeType: typeMime } : undefined)
      recorder.ondataavailable = (evenement) => {
        if (evenement.data.size > 0) chunksRef.current.push(evenement.data)
      }
      recorderRef.current = recorder
      recorder.start()
      setEnregistrement(true)
    } catch {
      setErreur("Impossible d'acceder au microphone.")
    }
  }

  function arreter(): Promise<Blob | null> {
    return new Promise((resolve) => {
      const recorder = recorderRef.current
      if (!recorder) {
        resolve(null)
        return
      }
      recorder.onstop = () => {
        streamRef.current?.getTracks().forEach((piste) => piste.stop())
        streamRef.current = null
        setEnregistrement(false)
        const type = recorder.mimeType || 'audio/webm'
        resolve(new Blob(chunksRef.current, { type }))
      }
      recorder.stop()
    })
  }

  return { enregistrement, erreur, demarrer, arreter }
}

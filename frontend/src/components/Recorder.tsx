import { useEffect, useRef, useState } from 'react'
import { creerSession, envoyerChunk, listerCouloirs, listerNumerosChunksRecus, obtenirSession, terminerSession } from '../api'
import { convertirBlobEnWav } from '../audioWav'
import type { Couloir } from '../types'

const DUREE_SEGMENT_MS = 30_000
const TYPE_MIME_PREFERE = 'audio/webm;codecs=opus'
const CLE_SESSION_ACTIVE = 'memoria_session_active'
const MAX_TENTATIVES_ENVOI = 5
const DELAIS_BACKOFF_MS = [1000, 2000, 4000, 8000, 16000]

interface RecorderProps {
  onSessionTerminee: (sessionId: string) => void
}

interface SessionActiveSauvegardee {
  sessionId: string
  titre: string
  couloirId: string | null
}

function lireSessionActiveSauvegardee(): SessionActiveSauvegardee | null {
  const brut = localStorage.getItem(CLE_SESSION_ACTIVE)
  if (!brut) return null
  try {
    return JSON.parse(brut) as SessionActiveSauvegardee
  } catch {
    return null
  }
}

export function Recorder({ onSessionTerminee }: RecorderProps) {
  const [enregistrement, setEnregistrement] = useState(false)
  const [titre, setTitre] = useState('')
  const [erreur, setErreur] = useState<string | null>(null)
  const [couloirs, setCouloirs] = useState<Couloir[]>([])
  const [couloirId, setCouloirId] = useState('')
  const [connexionInstable, setConnexionInstable] = useState(false)
  const [sessionInterrompue, setSessionInterrompue] = useState<SessionActiveSauvegardee | null>(null)

  useEffect(() => {
    listerCouloirs().then(setCouloirs).catch(() => {})
  }, [])

  // Reprise apres fermeture d'onglet/navigateur : une session laissee EN_COURS
  // cote serveur (voir localStorage.setItem dans demarrer()) declenche une
  // proposition de reprise plutot qu'une perte silencieuse.
  useEffect(() => {
    const sauvegarde = lireSessionActiveSauvegardee()
    if (!sauvegarde) return
    obtenirSession(sauvegarde.sessionId)
      .then((session) => {
        if (session.statut === 'EN_COURS') {
          setSessionInterrompue(sauvegarde)
        } else {
          localStorage.removeItem(CLE_SESSION_ACTIVE)
        }
      })
      .catch(() => {
        localStorage.removeItem(CLE_SESSION_ACTIVE)
      })
  }, [])

  // Bandeau "connexion instable" pilote aussi par les evenements navigateur,
  // pas seulement par un echec d'envoi deja en cours de retry.
  useEffect(() => {
    const surHorsLigne = () => setConnexionInstable(true)
    const surEnLigne = () => setConnexionInstable(false)
    window.addEventListener('offline', surHorsLigne)
    window.addEventListener('online', surEnLigne)
    return () => {
      window.removeEventListener('offline', surHorsLigne)
      window.removeEventListener('online', surEnLigne)
    }
  }, [])

  const sessionIdRef = useRef<string | null>(null)
  const numeroChunkRef = useRef(0)
  const streamRef = useRef<MediaStream | null>(null)
  const recorderRef = useRef<MediaRecorder | null>(null)
  const minuteurRef = useRef<number | null>(null)
  const arretDemandeRef = useRef(false)
  const dernierEnvoiRef = useRef<Promise<void>>(Promise.resolve())

  async function attendreReconnexion(): Promise<void> {
    if (navigator.onLine) return
    await new Promise<void>((resolve) => {
      window.addEventListener('online', () => resolve(), { once: true })
    })
  }

  // Coupure reseau pendant une session : le segment patiente et est
  // retente avec un backoff exponentiel plutot que d'etre perdu
  // silencieusement (comportement precedent).
  async function envoyerChunkAvecRetry(sessionId: string, numero: number, audio: Blob): Promise<void> {
    for (let tentative = 0; tentative < MAX_TENTATIVES_ENVOI; tentative++) {
      await attendreReconnexion()
      try {
        await envoyerChunk(sessionId, numero, audio)
        setConnexionInstable(false)
        return
      } catch (e) {
        setConnexionInstable(true)
        if (tentative === MAX_TENTATIVES_ENVOI - 1) {
          console.error('Echec definitif de l\'envoi du chunk', numero, e)
          setErreur("Un segment audio n'a pas pu etre envoye.")
          return
        }
        await new Promise((resolve) => window.setTimeout(resolve, DELAIS_BACKOFF_MS[tentative]))
      }
    }
  }

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
        .then((wav) => envoyerChunkAvecRetry(sessionId, numero, wav))
        .catch((e) => {
          console.error('Echec de la conversion du chunk', numero, e)
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
      localStorage.removeItem(CLE_SESSION_ACTIVE)
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
      localStorage.setItem(
        CLE_SESSION_ACTIVE,
        JSON.stringify({ sessionId: id, titre: titre.trim(), couloirId: couloirId || null }),
      )
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

  // Reprise d'une session laissee EN_COURS : repart juste apres le dernier
  // chunk reellement recu cote serveur (jamais depuis le compteur local, qui
  // peut avoir ete perdu avec l'onglet ferme).
  async function reprendre() {
    if (!sessionInterrompue) return
    const { sessionId, titre: titreSauve, couloirId: couloirSauve } = sessionInterrompue
    setErreur(null)
    try {
      const numeros = await listerNumerosChunksRecus(sessionId)
      numeroChunkRef.current = numeros.length ? Math.max(...numeros) + 1 : 0
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      sessionIdRef.current = sessionId
      streamRef.current = stream
      arretDemandeRef.current = false
      setTitre(titreSauve)
      setCouloirId(couloirSauve ?? '')
      setSessionInterrompue(null)
      setEnregistrement(true)
      demarrerNouveauSegment()
    } catch {
      setErreur("Impossible de reprendre l'enregistrement.")
    }
  }

  async function abandonner() {
    if (!sessionInterrompue) return
    try {
      await terminerSession(sessionInterrompue.sessionId)
    } catch {
      // On nettoie quand meme l'etat local : mieux vaut proposer une nouvelle
      // session que de rester bloque sur une bannière qui ne peut plus aboutir.
    } finally {
      localStorage.removeItem(CLE_SESSION_ACTIVE)
      setSessionInterrompue(null)
    }
  }

  function arreter() {
    arretDemandeRef.current = true
    if (minuteurRef.current) window.clearTimeout(minuteurRef.current)
    recorderRef.current?.stop()
  }

  return (
    <div className="rounded-2xl border bg-white p-5" style={{ borderColor: 'var(--color-border-soft)', boxShadow: '0 1px 3px rgba(20,18,15,.04)' }}>
      {sessionInterrompue && (
        <div
          className="mb-4 flex flex-col gap-2 rounded-lg border px-3.5 py-3 text-sm sm:flex-row sm:items-center sm:justify-between"
          style={{ borderColor: 'var(--color-warn)', background: 'var(--color-warn-wash)' }}
        >
          <span>
            Une session enregistree (<strong>{sessionInterrompue.titre}</strong>) semble interrompue. Reprendre l&apos;enregistrement ?
          </span>
          <div className="flex gap-2">
            <button
              onClick={reprendre}
              className="rounded-lg px-3 py-1.5 text-xs font-semibold text-white"
              style={{ background: 'var(--color-brand)' }}
            >
              Reprendre
            </button>
            <button
              onClick={abandonner}
              className="rounded-lg border px-3 py-1.5 text-xs font-semibold"
              style={{ borderColor: 'var(--color-border-soft)' }}
            >
              Abandonner
            </button>
          </div>
        </div>
      )}
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
      {enregistrement && connexionInstable && (
        <p className="mt-2 text-sm font-semibold" style={{ color: 'var(--color-warn)' }}>
          Connexion instable - les segments sont mis en attente et seront renvoyes automatiquement.
        </p>
      )}
      {erreur && <p className="mt-2 text-sm" style={{ color: '#B02631' }}>{erreur}</p>}
    </div>
  )
}

import type { Resume, Session, TranscriptionSegment } from './types'

const BASE = '/api/v1/sessions'

async function verifierReponse(reponse: Response): Promise<Response> {
  if (!reponse.ok) {
    throw new Error(`Erreur ${reponse.status} sur ${reponse.url}`)
  }
  return reponse
}

export async function listerSessions(): Promise<Session[]> {
  const reponse = await verifierReponse(await fetch(BASE))
  return reponse.json()
}

export async function obtenirSession(id: string): Promise<Session> {
  const reponse = await verifierReponse(await fetch(`${BASE}/${id}`))
  return reponse.json()
}

export async function creerSession(titre: string): Promise<{ id: string }> {
  const reponse = await verifierReponse(
    await fetch(BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ titre }),
    }),
  )
  return reponse.json()
}

export async function terminerSession(id: string): Promise<Session> {
  const reponse = await verifierReponse(
    await fetch(`${BASE}/${id}/terminer`, { method: 'POST' }),
  )
  return reponse.json()
}

export async function envoyerChunk(id: string, numeroSequence: number, audio: Blob): Promise<void> {
  await verifierReponse(
    await fetch(`${BASE}/${id}/chunks/${numeroSequence}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: audio,
    }),
  )
}

export async function obtenirTranscriptions(id: string): Promise<TranscriptionSegment[]> {
  const reponse = await verifierReponse(await fetch(`${BASE}/${id}/transcriptions`))
  return reponse.json()
}

export async function obtenirResume(id: string): Promise<Resume | null> {
  const reponse = await fetch(`${BASE}/${id}/resume`)
  if (reponse.status === 404) {
    return null
  }
  return (await verifierReponse(reponse)).json()
}

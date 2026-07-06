import type { CompteRendu, DocumentItem, RechercheResultat, Resume, ResumeType, Session, TranscriptionSegment } from './types'

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

export async function obtenirResume(id: string, type: ResumeType): Promise<Resume | null> {
  const reponse = await fetch(`${BASE}/${id}/resumes/${type}`)
  if (reponse.status === 404 || reponse.status === 204) {
    return null
  }
  return (await verifierReponse(reponse)).json()
}

export async function genererResume(id: string, type: ResumeType): Promise<Resume> {
  const reponse = await verifierReponse(
    await fetch(`${BASE}/${id}/resumes/${type}`, { method: 'POST' }),
  )
  return reponse.json()
}

export async function obtenirDocuments(id: string): Promise<DocumentItem[]> {
  const reponse = await verifierReponse(await fetch(`${BASE}/${id}/documents`))
  return reponse.json()
}

export async function televerserDocument(id: string, fichier: File): Promise<DocumentItem> {
  const corps = new FormData()
  corps.append('fichier', fichier)
  const reponse = await verifierReponse(
    await fetch(`${BASE}/${id}/documents`, { method: 'POST', body: corps }),
  )
  return reponse.json()
}

export async function obtenirCompteRendu(id: string): Promise<CompteRendu | null> {
  const reponse = await fetch(`${BASE}/${id}/compte-rendu`)
  if (reponse.status === 404 || reponse.status === 204) {
    return null
  }
  return (await verifierReponse(reponse)).json()
}

export async function genererCompteRendu(id: string): Promise<CompteRendu> {
  const reponse = await verifierReponse(
    await fetch(`${BASE}/${id}/compte-rendu`, { method: 'POST' }),
  )
  return reponse.json()
}

export async function rechercher(requete: string, limite = 10): Promise<RechercheResultat[]> {
  const parametres = new URLSearchParams({ q: requete, limite: String(limite) })
  const reponse = await verifierReponse(await fetch(`/api/v1/recherche?${parametres}`))
  return reponse.json()
}

export async function reindexerHistorique(): Promise<void> {
  await verifierReponse(await fetch('/api/v1/recherche/reindexation', { method: 'POST' }))
}

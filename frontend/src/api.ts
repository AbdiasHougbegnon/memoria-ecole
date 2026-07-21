import type { AuthResponse, CompteRendu, Couloir, DocumentItem, Engagement, FilMemoire, MembreCouloir, RechercheResultat, Resume, ResumeCours, ResumeType, Session, StatutEngagement, TranscriptionSegment } from './types'
import { deconnecter, obtenirToken } from './auth'

const BASE = '/api/v1/sessions'

export class ErreurApi extends Error {
  constructor(public status: number, message: string) {
    super(message)
    this.name = 'ErreurApi'
  }
}

async function appelApi(chemin: string, options: RequestInit = {}): Promise<Response> {
  const token = obtenirToken()
  const headers = new Headers(options.headers)
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const reponse = await fetch(chemin, { ...options, headers })
  if (reponse.status === 401) {
    deconnecter()
    window.location.href = '/connexion'
    throw new Error('Session expiree, reconnexion necessaire')
  }
  return reponse
}

async function verifierReponse(reponse: Response): Promise<Response> {
  if (!reponse.ok) {
    throw new ErreurApi(reponse.status, `Erreur ${reponse.status} sur ${reponse.url}`)
  }
  return reponse
}

export async function inscrire(email: string, motDePasse: string): Promise<AuthResponse> {
  const reponse = await verifierReponse(
    await fetch('/api/v1/auth/inscription', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, motDePasse }),
    }),
  )
  return reponse.json()
}

export async function connecter(email: string, motDePasse: string): Promise<AuthResponse> {
  const reponse = await verifierReponse(
    await fetch('/api/v1/auth/connexion', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, motDePasse }),
    }),
  )
  return reponse.json()
}

export async function listerSessions(): Promise<Session[]> {
  const reponse = await verifierReponse(await appelApi(BASE))
  return reponse.json()
}

export async function obtenirSession(id: string): Promise<Session> {
  const reponse = await verifierReponse(await appelApi(`${BASE}/${id}`))
  return reponse.json()
}

export async function creerSession(titre: string, couloirId?: string): Promise<{ id: string }> {
  const reponse = await verifierReponse(
    await appelApi(BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ titre, couloirId: couloirId ?? null }),
    }),
  )
  return reponse.json()
}

export async function terminerSession(id: string): Promise<Session> {
  const reponse = await verifierReponse(
    await appelApi(`${BASE}/${id}/terminer`, { method: 'POST' }),
  )
  return reponse.json()
}

export async function envoyerChunk(id: string, numeroSequence: number, audio: Blob): Promise<void> {
  await verifierReponse(
    await appelApi(`${BASE}/${id}/chunks/${numeroSequence}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: audio,
    }),
  )
}

export async function obtenirTranscriptions(id: string): Promise<TranscriptionSegment[]> {
  const reponse = await verifierReponse(await appelApi(`${BASE}/${id}/transcriptions`))
  return reponse.json()
}

export async function obtenirResume(id: string, type: ResumeType): Promise<Resume | null> {
  const reponse = await appelApi(`${BASE}/${id}/resumes/${type}`)
  if (reponse.status === 404 || reponse.status === 204) {
    return null
  }
  return (await verifierReponse(reponse)).json()
}

export async function genererResume(id: string, type: ResumeType): Promise<Resume> {
  const reponse = await verifierReponse(
    await appelApi(`${BASE}/${id}/resumes/${type}`, { method: 'POST' }),
  )
  return reponse.json()
}

export async function obtenirDocuments(id: string): Promise<DocumentItem[]> {
  const reponse = await verifierReponse(await appelApi(`${BASE}/${id}/documents`))
  return reponse.json()
}

export async function televerserDocument(id: string, fichier: File): Promise<DocumentItem> {
  const corps = new FormData()
  corps.append('fichier', fichier)
  const reponse = await verifierReponse(
    await appelApi(`${BASE}/${id}/documents`, { method: 'POST', body: corps }),
  )
  return reponse.json()
}

export async function obtenirCompteRendu(id: string): Promise<CompteRendu | null> {
  const reponse = await appelApi(`${BASE}/${id}/compte-rendu`)
  if (reponse.status === 404 || reponse.status === 204) {
    return null
  }
  return (await verifierReponse(reponse)).json()
}

export async function genererCompteRendu(id: string): Promise<CompteRendu> {
  const reponse = await verifierReponse(
    await appelApi(`${BASE}/${id}/compte-rendu`, { method: 'POST' }),
  )
  return reponse.json()
}

export async function obtenirResumeCours(id: string): Promise<ResumeCours | null> {
  const reponse = await appelApi(`${BASE}/${id}/resume-cours`)
  if (reponse.status === 404 || reponse.status === 204) {
    return null
  }
  return (await verifierReponse(reponse)).json()
}

export async function genererResumeCours(id: string): Promise<ResumeCours> {
  const reponse = await verifierReponse(
    await appelApi(`${BASE}/${id}/resume-cours`, { method: 'POST' }),
  )
  return reponse.json()
}

export async function rechercher(requete: string, limite = 10): Promise<RechercheResultat[]> {
  const parametres = new URLSearchParams({ q: requete, limite: String(limite) })
  const reponse = await verifierReponse(await appelApi(`/api/v1/recherche?${parametres}`))
  return reponse.json()
}

export async function reindexerHistorique(): Promise<void> {
  await verifierReponse(await appelApi('/api/v1/recherche/reindexation', { method: 'POST' }))
}

export async function listerFilsMemoire(): Promise<FilMemoire[]> {
  const reponse = await verifierReponse(await appelApi('/api/v1/fils-memoire'))
  return reponse.json()
}

export async function listerEngagements(statut?: StatutEngagement): Promise<Engagement[]> {
  const chemin = statut ? `/api/v1/engagements?statut=${statut}` : '/api/v1/engagements'
  const reponse = await verifierReponse(await appelApi(chemin))
  return reponse.json()
}

export async function listerEngagementsSession(id: string): Promise<Engagement[]> {
  const reponse = await verifierReponse(await appelApi(`${BASE}/${id}/engagements`))
  return reponse.json()
}

export async function confirmerEngagement(id: string): Promise<Engagement> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/engagements/${id}/confirmer`, { method: 'POST' }))
  return reponse.json()
}

export async function rejeterEngagement(id: string): Promise<Engagement> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/engagements/${id}/rejeter`, { method: 'POST' }))
  return reponse.json()
}

export async function terminerEngagement(id: string): Promise<Engagement> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/engagements/${id}/terminer`, { method: 'POST' }))
  return reponse.json()
}

export async function planifierEcheanceEngagement(id: string, dateEcheance: string): Promise<Engagement> {
  const reponse = await verifierReponse(
    await appelApi(`/api/v1/engagements/${id}/echeance`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ dateEcheance }),
    }),
  )
  return reponse.json()
}

export async function obtenirAdresseLocaleServeur(): Promise<string | null> {
  const reponse = await verifierReponse(await appelApi('/api/v1/reseau/adresse-locale'))
  const { adresseLocale } = await reponse.json()
  return adresseLocale
}

export async function creerCouloir(nom: string): Promise<Couloir> {
  const reponse = await verifierReponse(
    await appelApi('/api/v1/couloirs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nom }),
    }),
  )
  return reponse.json()
}

export async function listerCouloirs(): Promise<Couloir[]> {
  const reponse = await verifierReponse(await appelApi('/api/v1/couloirs'))
  return reponse.json()
}

export async function obtenirCouloir(id: string): Promise<Couloir> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/couloirs/${id}`))
  return reponse.json()
}

export async function rejoindreCouloir(id: string): Promise<Couloir> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/couloirs/${id}/rejoindre`, { method: 'POST' }))
  return reponse.json()
}

export async function listerSessionsCouloir(id: string): Promise<Session[]> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/couloirs/${id}/sessions`))
  return reponse.json()
}

export async function renommerCouloir(id: string, nom: string): Promise<Couloir> {
  const reponse = await verifierReponse(
    await appelApi(`/api/v1/couloirs/${id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nom }),
    }),
  )
  return reponse.json()
}

export async function supprimerCouloir(id: string): Promise<void> {
  await verifierReponse(await appelApi(`/api/v1/couloirs/${id}`, { method: 'DELETE' }))
}

export async function listerMembresCouloir(id: string): Promise<MembreCouloir[]> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/couloirs/${id}/membres`))
  return reponse.json()
}

export async function retirerMembreCouloir(couloirId: string, utilisateurId: string): Promise<void> {
  await verifierReponse(
    await appelApi(`/api/v1/couloirs/${couloirId}/membres/${utilisateurId}`, { method: 'DELETE' }),
  )
}

export async function quitterCouloir(id: string): Promise<void> {
  await verifierReponse(await appelApi(`/api/v1/couloirs/${id}/quitter`, { method: 'POST' }))
}

export async function transfererProprieteCouloir(id: string, nouveauProprietaireId: string): Promise<Couloir> {
  const reponse = await verifierReponse(
    await appelApi(`/api/v1/couloirs/${id}/transferer-propriete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nouveauProprietaireId }),
    }),
  )
  return reponse.json()
}

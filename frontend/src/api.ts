import type { AuthResponse, CompteRendu, Couloir, DocumentItem, DocumentMatiere, Engagement, EmpreinteVocale, EtatTutorat, ExerciceMatiere, ExercicePapier, FilMemoire, JournalRgpdEntry, Matiere, MembreCouloir, ModeTutorat, ModuleMemoria, NiveauMaitrise, Notion, NotionCandidate, OptionInscription, Qcm, QcmMatiere, RapportImportMatieres, RechercheResultat, Resume, ResumeCours, ResultatTour, ResumeType, Seance, Session, StatutEngagement, TableauDeBordEntreprise, TentativeExerciceSaisieLibre, TentativeQcm, TranscriptionSegment, TravailPapierMatiere } from './types'
import { deconnecter, obtenirToken } from './auth'

const BASE = '/api/v1/sessions'

export class ErreurApi extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
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
    window.location.href = '/choix-module'
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

export async function inscrire(email: string, motDePasse: string, module: ModuleMemoria): Promise<AuthResponse> {
  const reponse = await verifierReponse(
    await fetch('/api/v1/auth/inscription', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, motDePasse, module }),
    }),
  )
  return reponse.json()
}

export async function obtenirOptionsInscriptionEcole(): Promise<OptionInscription[]> {
  const reponse = await verifierReponse(await fetch('/api/v1/ecole/options-inscription'))
  return reponse.json()
}

export async function inscrireEcole(
  email: string,
  motDePasse: string,
  anneeAcademique: string,
  filiere: string,
  specialite: string,
): Promise<AuthResponse> {
  const reponse = await verifierReponse(
    await fetch('/api/v1/ecole/inscription', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, motDePasse, anneeAcademique, filiere, specialite }),
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

export async function creerSession(titre: string, couloirId: string | undefined, consentementEnregistrement: boolean): Promise<{ id: string }> {
  const reponse = await verifierReponse(
    await appelApi(BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ titre, couloirId: couloirId ?? null, consentementEnregistrement }),
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

export async function listerNumerosChunksRecus(id: string): Promise<number[]> {
  const reponse = await verifierReponse(await appelApi(`${BASE}/${id}/chunks`))
  return reponse.json()
}

// Drill-down resume/compte-rendu -> transcription -> audio. Meme motif que
// obtenirAudioTutorat : fetch authentifie + URL de blob, jamais un
// <audio src=...> direct (le navigateur n'attache pas l'en-tete
// Authorization a une requete declenchee par une balise media).
export async function obtenirAudioChunk(id: string, numeroSequence: number): Promise<Blob> {
  const reponse = await verifierReponse(await appelApi(`${BASE}/${id}/chunks/${numeroSequence}/audio`))
  return reponse.blob()
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

export async function telechargerResumeCours(id: string): Promise<Blob> {
  const reponse = await verifierReponse(await appelApi(`${BASE}/${id}/resume-cours/telechargement`))
  return reponse.blob()
}

export async function obtenirQcm(id: string): Promise<Qcm | null> {
  const reponse = await appelApi(`${BASE}/${id}/qcm`)
  if (reponse.status === 404 || reponse.status === 204) {
    return null
  }
  return (await verifierReponse(reponse)).json()
}

export async function genererQcm(id: string): Promise<Qcm> {
  const reponse = await verifierReponse(
    await appelApi(`${BASE}/${id}/qcm`, { method: 'POST' }),
  )
  return reponse.json()
}

export async function soumettreTentativeQcm(id: string, reponses: number[]): Promise<TentativeQcm> {
  const reponse = await verifierReponse(
    await appelApi(`${BASE}/${id}/qcm/tentatives`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reponses }),
    }),
  )
  return reponse.json()
}

export async function obtenirMaTentativeQcm(id: string): Promise<TentativeQcm | null> {
  const reponse = await appelApi(`${BASE}/${id}/qcm/tentatives/moi`)
  if (reponse.status === 404 || reponse.status === 204) {
    return null
  }
  return (await verifierReponse(reponse)).json()
}

const BASE_MATIERES = '/api/v1/matieres'

export async function obtenirQcmMatiere(matiereId: string): Promise<QcmMatiere | null> {
  const reponse = await appelApi(`${BASE_MATIERES}/${matiereId}/qcm-matiere`)
  if (reponse.status === 404 || reponse.status === 204) {
    return null
  }
  return (await verifierReponse(reponse)).json()
}

export async function genererQcmMatiere(matiereId: string): Promise<QcmMatiere> {
  const reponse = await verifierReponse(
    await appelApi(`${BASE_MATIERES}/${matiereId}/qcm-matiere`, { method: 'POST' }),
  )
  return reponse.json()
}

export async function soumettreTentativeQcmMatiere(matiereId: string, reponses: number[]): Promise<TentativeQcm> {
  const reponse = await verifierReponse(
    await appelApi(`${BASE_MATIERES}/${matiereId}/qcm-matiere/tentatives`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reponses }),
    }),
  )
  return reponse.json()
}

export async function obtenirMaTentativeQcmMatiere(matiereId: string): Promise<TentativeQcm | null> {
  const reponse = await appelApi(`${BASE_MATIERES}/${matiereId}/qcm-matiere/tentatives/moi`)
  if (reponse.status === 404 || reponse.status === 204) {
    return null
  }
  return (await verifierReponse(reponse)).json()
}

export async function obtenirExercices(matiereId: string): Promise<ExerciceMatiere | null> {
  const reponse = await appelApi(`${BASE_MATIERES}/${matiereId}/exercices`)
  if (reponse.status === 404 || reponse.status === 204) {
    return null
  }
  return (await verifierReponse(reponse)).json()
}

export async function genererExercices(matiereId: string): Promise<ExerciceMatiere> {
  const reponse = await verifierReponse(
    await appelApi(`${BASE_MATIERES}/${matiereId}/exercices`, { method: 'POST' }),
  )
  return reponse.json()
}

export async function soumettreReponsesExercices(matiereId: string, reponses: string[]): Promise<TentativeExerciceSaisieLibre> {
  const reponse = await verifierReponse(
    await appelApi(`${BASE_MATIERES}/${matiereId}/exercices/tentatives`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reponses }),
    }),
  )
  return reponse.json()
}

export async function obtenirMaTentativeExercices(matiereId: string): Promise<TentativeExerciceSaisieLibre | null> {
  const reponse = await appelApi(`${BASE_MATIERES}/${matiereId}/exercices/tentatives/moi`)
  if (reponse.status === 404 || reponse.status === 204) {
    return null
  }
  return (await verifierReponse(reponse)).json()
}

// Deux photos separees (phase 28) : l'enonce et la reponse de l'etudiant,
// pour que la correction s'appuie sur l'enonce reel.
export async function soumettreTravailPapier(matiereId: string, fichierEnonce: File, fichierReponse: File): Promise<TravailPapierMatiere> {
  const corps = new FormData()
  corps.append('fichierEnonce', fichierEnonce)
  corps.append('fichierReponse', fichierReponse)
  const reponse = await verifierReponse(
    await appelApi(`${BASE_MATIERES}/${matiereId}/travaux-papier`, { method: 'POST', body: corps }),
  )
  return reponse.json()
}

export async function listerMesTravauxPapier(matiereId: string): Promise<TravailPapierMatiere[]> {
  const reponse = await verifierReponse(await appelApi(`${BASE_MATIERES}/${matiereId}/travaux-papier`))
  return reponse.json()
}

// Reessai manuel pour un travail soumis avant l'ajout de la correction
// automatique (ou dont la premiere tentative avait echoue).
export async function reessayerCorrectionTravailPapier(matiereId: string, travailId: string): Promise<TravailPapierMatiere> {
  const reponse = await verifierReponse(
    await appelApi(`${BASE_MATIERES}/${matiereId}/travaux-papier/${travailId}/corriger`, { method: 'POST' }),
  )
  return reponse.json()
}

// Verification de comprehension (phase 30, brique C) : mode progressif
// uniquement, ne bloque jamais la navigation.
export async function genererQuestionVerification(matiereId: string, travailId: string, exerciceId: string): Promise<ExercicePapier> {
  const reponse = await verifierReponse(
    await appelApi(`${BASE_MATIERES}/${matiereId}/travaux-papier/${travailId}/exercices/${exerciceId}/verification/question`, {
      method: 'POST',
    }),
  )
  return reponse.json()
}

export async function soumettreReponseChoixVerification(
  matiereId: string,
  travailId: string,
  exerciceId: string,
  indicesCoches: number[],
): Promise<ExercicePapier> {
  const reponse = await verifierReponse(
    await appelApi(`${BASE_MATIERES}/${matiereId}/travaux-papier/${travailId}/exercices/${exerciceId}/verification/reponse-choix`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ indicesCoches }),
    }),
  )
  return reponse.json()
}

export async function soumettreReponseLibreVerification(
  matiereId: string,
  travailId: string,
  exerciceId: string,
  reponse: string,
): Promise<ExercicePapier> {
  const resultat = await verifierReponse(
    await appelApi(`${BASE_MATIERES}/${matiereId}/travaux-papier/${travailId}/exercices/${exerciceId}/verification/reponse-libre`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reponse }),
    }),
  )
  return resultat.json()
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

export async function importerMatieres(fichier: File): Promise<RapportImportMatieres> {
  const corps = new FormData()
  corps.append('fichier', fichier)
  const reponse = await verifierReponse(
    await appelApi('/api/v1/ecole/import-matieres', { method: 'POST', body: corps }),
  )
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

export async function obtenirTableauDeBordEntreprise(): Promise<TableauDeBordEntreprise> {
  const reponse = await verifierReponse(await appelApi('/api/v1/entreprise/tableau-de-bord'))
  return reponse.json()
}

export async function obtenirMonCompte(): Promise<{ id: string; email: string; nom: string | null; module: ModuleMemoria }> {
  const reponse = await verifierReponse(await appelApi('/api/v1/utilisateurs/moi'))
  return reponse.json()
}

export async function renseignerNom(nom: string): Promise<void> {
  await verifierReponse(
    await appelApi('/api/v1/utilisateurs/moi', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nom }),
    }),
  )
}

export async function enregistrerEmpreinteVocale(audio: Blob, consentement: boolean): Promise<EmpreinteVocale> {
  const corps = new FormData()
  corps.append('audio', audio, 'empreinte.wav')
  corps.append('consentement', String(consentement))
  const reponse = await verifierReponse(
    await appelApi('/api/v1/utilisateurs/moi/empreinte-vocale', { method: 'POST', body: corps }),
  )
  return reponse.json()
}

export async function obtenirEmpreinteVocale(): Promise<EmpreinteVocale> {
  const reponse = await verifierReponse(await appelApi('/api/v1/utilisateurs/moi/empreinte-vocale'))
  return reponse.json()
}

export async function revoquerEmpreinteVocale(): Promise<void> {
  await verifierReponse(await appelApi('/api/v1/utilisateurs/moi/empreinte-vocale', { method: 'DELETE' }))
}

export async function exporterDonnees(): Promise<unknown> {
  const reponse = await verifierReponse(await appelApi('/api/v1/utilisateurs/moi/export'))
  return reponse.json()
}

export async function supprimerCompte(): Promise<void> {
  await verifierReponse(await appelApi('/api/v1/utilisateurs/moi', { method: 'DELETE' }))
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

// --- Tuteur vocal (Matiere / Notion / Seance / dialogue) ---

export async function creerMatiere(nom: string, couloirId: string): Promise<Matiere> {
  const reponse = await verifierReponse(
    await appelApi('/api/v1/matieres', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nom, couloirId }),
    }),
  )
  return reponse.json()
}

export async function listerMatieresParCouloir(couloirId: string): Promise<Matiere[]> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/couloirs/${couloirId}/matieres`))
  return reponse.json()
}

export async function obtenirMatiere(id: string): Promise<Matiere> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/matieres/${id}`))
  return reponse.json()
}

// Vue transverse (tous couloirs confondus), alimente les entrees de menu
// Revision/Tutorat -- eviter d'obliger l'etudiant a se souvenir de quel
// couloir contient quelle matiere avant de pouvoir reviser.
export async function listerMesMatieres(): Promise<Matiere[]> {
  const reponse = await verifierReponse(await appelApi('/api/v1/matieres'))
  return reponse.json()
}

export async function rattacherMatiereSession(sessionId: string, matiereId: string): Promise<void> {
  await verifierReponse(
    await appelApi(`/api/v1/ecole/sessions/${sessionId}/matiere`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ matiereId }),
    }),
  )
}

export async function creerNotion(matiereId: string, terme: string, definition: string, ordre: number): Promise<Notion> {
  const reponse = await verifierReponse(
    await appelApi(`/api/v1/matieres/${matiereId}/notions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ terme, definition, ordre }),
    }),
  )
  return reponse.json()
}

export async function listerNotionsParMatiere(matiereId: string): Promise<Notion[]> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/matieres/${matiereId}/notions`))
  return reponse.json()
}

export async function supprimerNotion(matiereId: string, notionId: string): Promise<void> {
  await verifierReponse(await appelApi(`/api/v1/matieres/${matiereId}/notions/${notionId}`, { method: 'DELETE' }))
}

// --- Contenu pilote par documents (phase 18 : fiche televersee -> notions candidates -> validation enseignant) ---

export async function televerserDocumentMatiere(matiereId: string, fichier: File): Promise<DocumentMatiere> {
  const corps = new FormData()
  corps.append('fichier', fichier)
  const reponse = await verifierReponse(
    await appelApi(`/api/v1/matieres/${matiereId}/documents`, { method: 'POST', body: corps }),
  )
  return reponse.json()
}

export async function listerDocumentsMatiere(matiereId: string): Promise<DocumentMatiere[]> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/matieres/${matiereId}/documents`))
  return reponse.json()
}

export async function listerNotionsCandidates(matiereId: string): Promise<NotionCandidate[]> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/matieres/${matiereId}/notions-candidates`))
  return reponse.json()
}

export async function validerNotionCandidate(matiereId: string, candidateId: string, terme: string, definition: string): Promise<Notion> {
  const reponse = await verifierReponse(
    await appelApi(`/api/v1/matieres/${matiereId}/notions-candidates/${candidateId}/valider`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ terme, definition }),
    }),
  )
  return reponse.json()
}

export async function rejeterNotionCandidate(matiereId: string, candidateId: string): Promise<NotionCandidate> {
  const reponse = await verifierReponse(
    await appelApi(`/api/v1/matieres/${matiereId}/notions-candidates/${candidateId}/rejeter`, { method: 'POST' }),
  )
  return reponse.json()
}

export async function creerSeance(matiereId: string, titre: string): Promise<Seance> {
  const reponse = await verifierReponse(
    await appelApi(`/api/v1/matieres/${matiereId}/seances`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ titre }),
    }),
  )
  return reponse.json()
}

export async function listerSeancesParMatiere(matiereId: string): Promise<Seance[]> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/matieres/${matiereId}/seances`))
  return reponse.json()
}

export async function obtenirSeance(seanceId: string): Promise<Seance> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/seances/${seanceId}`))
  return reponse.json()
}

export async function supprimerSeance(seanceId: string): Promise<void> {
  await verifierReponse(await appelApi(`/api/v1/seances/${seanceId}`, { method: 'DELETE' }))
}

export async function rattacherNotions(seanceId: string, notionIds: string[]): Promise<Notion[]> {
  const reponse = await verifierReponse(
    await appelApi(`/api/v1/seances/${seanceId}/notions`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ notionIds }),
    }),
  )
  return reponse.json()
}

export async function listerNotionsDeSeance(seanceId: string): Promise<Notion[]> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/seances/${seanceId}/notions`))
  return reponse.json()
}

export async function obtenirMaitriseSeance(seanceId: string): Promise<Record<string, NiveauMaitrise>> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/seances/${seanceId}/maitrise`))
  return reponse.json()
}

export async function demarrerTutorat(seanceId: string, mode: ModeTutorat): Promise<ResultatTour> {
  const reponse = await verifierReponse(
    await appelApi(`/api/v1/seances/${seanceId}/tutorat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mode }),
    }),
  )
  return reponse.json()
}

// Entree directe depuis le menu de navigation (pas besoin de choisir une
// seance au prealable) : demarre toujours en mode LIBRE, sur une seance
// "Discussion libre" partagee resolue/creee cote serveur.
export async function demarrerTutoratMatiere(matiereId: string): Promise<ResultatTour> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/matieres/${matiereId}/tutorat`, { method: 'POST' }))
  return reponse.json()
}

export async function obtenirEtatTutorat(id: string): Promise<EtatTutorat> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/tutorat/${id}`))
  return reponse.json()
}

export async function soumettreReponseTutorat(id: string, audio: Blob): Promise<ResultatTour> {
  const corps = new FormData()
  corps.append('audio', audio, 'reponse.webm')
  const reponse = await verifierReponse(
    await appelApi(`/api/v1/tutorat/${id}/reponse`, { method: 'POST', body: corps }),
  )
  return reponse.json()
}

export async function arreterTutorat(id: string): Promise<EtatTutorat> {
  const reponse = await verifierReponse(await appelApi(`/api/v1/tutorat/${id}/arreter`, { method: 'POST' }))
  return reponse.json()
}

// Recupere l'audio via fetch authentifie (pas un <audio src=...> direct) :
// le navigateur n'attache pas l'en-tete Authorization a une requete src
// declenchee par une balise media, contrairement a fetch() ici.
export async function obtenirAudioTutorat(audioUrl: string): Promise<Blob> {
  const reponse = await verifierReponse(await appelApi(audioUrl))
  return reponse.blob()
}

export async function effacerCompteAdmin(email: string): Promise<void> {
  await verifierReponse(
    await appelApi('/api/v1/admin/utilisateurs/effacement', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
    }),
  )
}

export async function listerJournalRgpd(): Promise<JournalRgpdEntry[]> {
  const reponse = await verifierReponse(await appelApi('/api/v1/admin/journal-rgpd'))
  return reponse.json()
}

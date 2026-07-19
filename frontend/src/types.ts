export type SessionStatus = 'EN_COURS' | 'TERMINEE' | 'ERREUR'

export interface Session {
  id: string
  titre: string
  dateCreation: string
  statut: SessionStatus
  cheminFichierAudio: string | null
  couloirId: string | null
  createurId: string | null
}

export type TranscriptionStatut = 'REUSSIE' | 'ECHEC'

export interface SegmentLocuteur {
  locuteur: number
  texte: string
  offsetMillisecondes: number
  dureeMillisecondes: number
}

export interface TranscriptionSegment {
  numeroSequence: number
  texte: string | null
  statut: TranscriptionStatut
  dateCreation: string
  segmentsLocuteur: SegmentLocuteur[]
}

export type ResumeStatut = 'REUSSI' | 'ECHEC'

export type ResumeType = 'COURT' | 'DETAILLE' | 'ACTIONS'

export interface Resume {
  type: ResumeType
  texteResume: string | null
  pointsCles: string[]
  segmentsSources: number[]
  statut: ResumeStatut
  dateCreation: string
}

export type TypeDocument = 'PDF' | 'PHOTO'
export type StatutDocument = 'EN_ATTENTE' | 'REUSSI' | 'ECHEC'

export interface DocumentItem {
  id: string
  type: TypeDocument
  nomFichier: string
  texteExtrait: string | null
  statut: StatutDocument
  dateCreation: string
}

export type StatutCompteRendu = 'REUSSI' | 'ECHEC'

export interface ActionCompteRendu {
  description: string
  responsable: string | null
  echeance: string | null
}

export interface CompteRendu {
  synthese: string | null
  decisions: string[]
  actions: ActionCompteRendu[]
  segmentsSources: number[]
  statut: StatutCompteRendu
  dateCreation: string
}

export interface RechercheResultat {
  sessionId: string
  titreSession: string
  dateSession: string
  texte: string
  locuteur: number
  offsetMillisecondes: number
  dureeMillisecondes: number
  numeroSequence: number
  score: number
}

export interface SessionSommaire {
  id: string
  titre: string
  dateCreation: string
}

export interface FilMemoire {
  id: string
  nom: string
  resumeCumulatif: string
  sessions: SessionSommaire[]
  dateCreation: string
  dateMiseAJour: string
}

export type StatutEngagement = 'EN_ATTENTE' | 'CONFIRME' | 'REJETE' | 'TERMINE'

export interface Engagement {
  id: string
  sessionId: string
  sessionTitre: string
  description: string
  responsable: string | null
  echeance: string | null
  statut: StatutEngagement
  dateCreation: string
  dateDerniereMaj: string
}

export type StatutResumeCours = 'REUSSI' | 'ECHEC'

export interface NotionCours {
  terme: string
  definition: string
}

export interface ResumeCours {
  synthese: string | null
  notions: NotionCours[]
  pointsARevoir: string[]
  segmentsSources: number[]
  statut: StatutResumeCours
  dateCreation: string
}

export interface AuthResponse {
  token: string
  utilisateurId: string
  email: string
}

export interface Couloir {
  id: string
  nom: string
  proprietaireId: string
  dateCreation: string
  nombreMembres: number
}

export interface MembreCouloir {
  utilisateurId: string
  email: string
  dateAdhesion: string
}

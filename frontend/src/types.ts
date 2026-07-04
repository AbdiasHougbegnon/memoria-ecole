export type SessionStatus = 'EN_COURS' | 'TERMINEE' | 'ERREUR'

export interface Session {
  id: string
  titre: string
  dateCreation: string
  statut: SessionStatus
  cheminFichierAudio: string | null
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

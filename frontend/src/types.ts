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
  utilisateurIdentifieId: string | null
  nomUtilisateurIdentifie: string | null
  confianceIdentification: number | null
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
  dateEcheance: string | null
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

export type StatutQcm = 'REUSSI' | 'ECHEC'

export interface QuestionQcm {
  enonce: string
  choix: string[]
  reponseCorrecte: number
  explication: string
}

export interface Qcm {
  questions: QuestionQcm[]
  segmentsSources: number[]
  statut: StatutQcm
  dateCreation: string
}

export interface TentativeQcm {
  reponsesChoisies: number[]
  score: number
  nombreQuestions: number
  nombreTentatives: number
  dateMiseAJour: string
}

export type ModuleMemoria = 'ECOLE' | 'ENTREPRISE'

export interface AuthResponse {
  token: string
  utilisateurId: string
  email: string
  module: ModuleMemoria
  admin: boolean
}

export type TypeActionRgpd = 'EFFACEMENT_COMPTE' | 'EXPORT_DONNEES' | 'PURGE_RETENTION'

export interface JournalRgpdEntry {
  id: string
  type: TypeActionRgpd
  utilisateurCibleId: string | null
  initiateurId: string | null
  dateAction: string
  details: string
}

export interface Couloir {
  id: string
  nom: string
  proprietaireId: string
  dateCreation: string
  nombreMembres: number
}

export interface ErreurImport {
  numeroLigne: number
  message: string
}

export interface RapportImportMatieres {
  couloirsCrees: number
  couloirsExistants: number
  matieresCreees: number
  matieresExistantes: number
  erreurs: ErreurImport[]
}

export interface OptionInscription {
  anneeAcademique: string
  filiere: string
  specialite: string | null
}

export interface MembreCouloir {
  utilisateurId: string
  email: string
  dateAdhesion: string
}

export type StatutEmpreinteVocale = 'EN_ATTENTE' | 'PRETE' | 'ECHEC'

export interface EmpreinteVocale {
  id: string | null
  statut: StatutEmpreinteVocale | null
  dateConsentement: string | null
}

export interface PointTendanceHebdomadaire {
  debutSemaine: string
  crees: number
  termines: number
}

export interface TableauDeBordEntreprise {
  total: number
  parStatut: Partial<Record<StatutEngagement, number>>
  tauxCompletion: number
  enRetard: number
  tauxRejet: number
  delaiMoyenTraitementJours: number | null
  tendanceHebdomadaire: PointTendanceHebdomadaire[]
}

export interface Matiere {
  id: string
  nom: string
  couloirId: string
  createurId: string
  dateCreation: string
}

export interface Notion {
  id: string
  matiereId: string
  terme: string
  definition: string
  ordre: number
  dateCreation: string
  documentSourceId: string | null
}

export interface DocumentMatiere {
  id: string
  matiereId: string
  type: TypeDocument
  nomFichier: string
  texteExtrait: string | null
  statut: StatutDocument
  dateCreation: string
}

export type StatutNotionCandidate = 'EN_ATTENTE' | 'VALIDEE' | 'REJETEE'

export interface NotionCandidate {
  id: string
  documentMatiereId: string
  matiereId: string
  terme: string
  definition: string
  statut: StatutNotionCandidate
  dateCreation: string
}

export type NiveauMaitrise = 'NON_ABORDEE' | 'EN_COURS' | 'MAITRISEE'

export interface Seance {
  id: string
  titre: string
  matiereId: string
  couloirId: string
  sessionId: string | null
  dateCreation: string
}

export type StatutSeanceTutorat = 'EN_COURS' | 'TERMINEE'
export type LocuteurTutorat = 'ETUDIANT' | 'TUTEUR'
export type ModeTutorat = 'EXPLICATION' | 'EXERCICE' | 'LIBRE'

export interface TourDialogue {
  id: string
  locuteur: LocuteurTutorat
  texte: string
  dateCreation: string
  audioUrl: string | null
}

export interface EtatTutorat {
  id: string
  seanceId: string
  statut: StatutSeanceTutorat
  notionCouranteId: string | null
  mode: ModeTutorat
  dateDebut: string
  dateFin: string | null
  tours: TourDialogue[]
}

export interface ResultatTour {
  seanceTutoratId: string
  tourId: string | null
  texteTuteur: string
  audioUrl: string | null
  notionCouranteId: string | null
  niveauMaitrise: NiveauMaitrise | null
  seanceTerminee: boolean
}

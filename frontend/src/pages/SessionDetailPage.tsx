import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import QRCode from 'qrcode'
import {
  confirmerEngagement,
  genererCompteRendu,
  genererResume,
  genererResumeCours,
  listerEngagementsSession,
  obtenirAdresseLocaleServeur,
  obtenirCompteRendu,
  obtenirDocuments,
  obtenirResume,
  obtenirResumeCours,
  obtenirSession,
  obtenirTranscriptions,
  rejeterEngagement,
  televerserDocument,
  terminerEngagement,
} from '../api'
import { redimensionnerImageSiNecessaire } from '../redimensionnerImage'
import type { CompteRendu, DocumentItem, Engagement, Resume, ResumeCours, ResumeType, Session, TranscriptionSegment } from '../types'

const LIBELLE_STATUT_ENGAGEMENT: Record<Engagement['statut'], string> = {
  EN_ATTENTE: 'A confirmer',
  CONFIRME: 'Confirme',
  REJETE: 'Rejete',
  TERMINE: 'Termine',
}

// Nombre de relectures tentees apres la fin de la session avant d'abandonner
// l'attente d'un resume (environ 15s a raison d'une tentative toutes les 3s).
const MAX_TENTATIVES_APRES_FIN = 5

// Seul le resume DETAILLE est genere automatiquement a la fin de session.
// Les autres types sont generes a la demande, la premiere fois que
// l'utilisateur ouvre l'onglet correspondant (voir afficherOnglet).
const ONGLETS_RESUME: { valeur: ResumeType; libelle: string }[] = [
  { valeur: 'DETAILLE', libelle: 'Detaille' },
  { valeur: 'COURT', libelle: 'Court' },
  { valeur: 'ACTIONS', libelle: 'Actions' },
]

function ContenuResume({ resume }: { resume: Resume }) {
  if (resume.statut === 'ECHEC') {
    return <p className="text-sm text-red-600">La generation du resume a echoue.</p>
  }
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <p className="text-sm text-slate-800">{resume.texteResume}</p>
      {resume.pointsCles.length > 0 && (
        <ul className="mt-3 list-disc pl-5 text-sm text-slate-700">
          {resume.pointsCles.map((point, index) => (
            <li key={index}>{point}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function SessionDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [session, setSession] = useState<Session | null>(null)
  const [transcriptions, setTranscriptions] = useState<TranscriptionSegment[]>([])
  const [resumesParType, setResumesParType] = useState<Partial<Record<ResumeType, Resume | null>>>({})
  const [chargement, setChargement] = useState(true)
  const [resumeAbandonne, setResumeAbandonne] = useState(false)
  const [ongletActif, setOngletActif] = useState<ResumeType>('DETAILLE')
  const [generationEnCours, setGenerationEnCours] = useState<ResumeType | null>(null)
  const [erreurGeneration, setErreurGeneration] = useState<string | null>(null)
  const [documents, setDocuments] = useState<DocumentItem[]>([])
  const [televersementEnCours, setTeleversementEnCours] = useState(false)
  const [erreurTeleversement, setErreurTeleversement] = useState<string | null>(null)
  const [qrCodeUrl, setQrCodeUrl] = useState<string | null>(null)
  const [qrCodeImage, setQrCodeImage] = useState<string | null>(null)
  const [compteRendu, setCompteRendu] = useState<CompteRendu | null>(null)
  const [compteRenduEnCours, setCompteRenduEnCours] = useState(false)
  const [erreurCompteRendu, setErreurCompteRendu] = useState<string | null>(null)
  const [resumeCours, setResumeCours] = useState<ResumeCours | null>(null)
  const [resumeCoursEnCours, setResumeCoursEnCours] = useState(false)
  const [erreurResumeCours, setErreurResumeCours] = useState<string | null>(null)
  const [engagements, setEngagements] = useState<Engagement[]>([])
  const [engagementEnCours, setEngagementEnCours] = useState<string | null>(null)
  // Le microphone n'est accessible que sur localhost ou en HTTPS (contexte
  // securise impose par les navigateurs) : on reste donc sur localhost pour
  // enregistrer, et on ne demande l'IP reseau du PC que pour construire le
  // lien du QR code, sans changer l'origine de la page elle-meme.
  const [adresseReseau, setAdresseReseau] = useState(() =>
    /^(localhost|127\.0\.0\.1)$/.test(window.location.hostname) ? '' : window.location.host,
  )

  useEffect(() => {
    if (!id) return
    let annule = false
    let tentativesApresFin = 0
    let intervalle: number

    async function charger() {
      const [s, t, r, d, cr, rc, eng] = await Promise.all([
        obtenirSession(id!),
        obtenirTranscriptions(id!),
        obtenirResume(id!, 'DETAILLE'),
        obtenirDocuments(id!),
        obtenirCompteRendu(id!),
        obtenirResumeCours(id!),
        listerEngagementsSession(id!),
      ])
      if (annule) return

      setSession(s)
      setTranscriptions(t)
      setResumesParType((precedent) => ({ ...precedent, DETAILLE: r }))
      setDocuments(d)
      setCompteRendu(cr)
      setResumeCours(rc)
      setEngagements(eng)
      setChargement(false)

      if (r || s.statut === 'EN_COURS') {
        tentativesApresFin = 0
        return
      }

      // Session terminee et toujours pas de resume detaille : soit il est
      // encore en cours de generation, soit aucune transcription n'a abouti
      // et aucun resume ne sera jamais produit. On n'interroge pas
      // indefiniment.
      tentativesApresFin += 1
      if (tentativesApresFin >= MAX_TENTATIVES_APRES_FIN) {
        setResumeAbandonne(true)
        window.clearInterval(intervalle)
      }
    }

    void charger()

    intervalle = window.setInterval(() => void charger(), 3000)
    return () => {
      annule = true
      window.clearInterval(intervalle)
    }
  }, [id])

  async function afficherOnglet(type: ResumeType) {
    setOngletActif(type)
    if (type === 'DETAILLE' || resumesParType[type] !== undefined || generationEnCours === type) {
      return
    }

    setErreurGeneration(null)
    setGenerationEnCours(type)
    try {
      const resume = await genererResume(id!, type)
      setResumesParType((precedent) => ({ ...precedent, [type]: resume }))
    } catch {
      setErreurGeneration("Impossible de generer ce resume (aucune transcription disponible pour le moment ?).")
    } finally {
      setGenerationEnCours(null)
    }
  }

  async function genererLeCompteRendu() {
    if (!id || compteRenduEnCours) return
    setErreurCompteRendu(null)
    setCompteRenduEnCours(true)
    try {
      setCompteRendu(await genererCompteRendu(id))
    } catch {
      setErreurCompteRendu(
        "Impossible de generer le compte rendu (aucune transcription disponible pour le moment ?).",
      )
    } finally {
      setCompteRenduEnCours(false)
    }
  }

  async function genererLeResumeCours() {
    if (!id || resumeCoursEnCours) return
    setErreurResumeCours(null)
    setResumeCoursEnCours(true)
    try {
      setResumeCours(await genererResumeCours(id))
    } catch {
      setErreurResumeCours(
        "Impossible de generer le resume de cours (aucune transcription disponible pour le moment ?).",
      )
    } finally {
      setResumeCoursEnCours(false)
    }
  }

  async function agirSurEngagement(engagementId: string, action: (id: string) => Promise<Engagement>) {
    setEngagementEnCours(engagementId)
    try {
      const misAJour = await action(engagementId)
      setEngagements((precedent) => precedent.map((e) => (e.id === engagementId ? misAJour : e)))
    } finally {
      setEngagementEnCours(null)
    }
  }

  async function surSelectionFichier(fichier: File | undefined) {
    if (!fichier || !id) return
    setErreurTeleversement(null)
    setTeleversementEnCours(true)
    try {
      const fichierEnvoye = await redimensionnerImageSiNecessaire(fichier)
      const document = await televerserDocument(id, fichierEnvoye)
      setDocuments((precedent) => [...precedent, document])
    } catch {
      setErreurTeleversement("Echec de l'envoi du document.")
    } finally {
      setTeleversementEnCours(false)
    }
  }

  async function afficherQrCode() {
    if (!id) return
    if (qrCodeImage) {
      setQrCodeImage(null)
      return
    }
    let hote = adresseReseau.trim()
    // Sur localhost, le navigateur ne peut pas connaitre l'IP reseau du PC
    // (restriction de vie privee) : on la demande au serveur, qui peut la
    // lire directement sur la machine. Evite a l'utilisateur de devoir
    // aller la chercher lui-meme (ipconfig/ifconfig) et la copier.
    if (!hote) {
      const adresseDetectee = await obtenirAdresseLocaleServeur().catch(() => null)
      if (adresseDetectee) {
        hote = `${adresseDetectee}:${window.location.port}`
        setAdresseReseau(hote)
      }
    }
    await regenererQrCode(hote || undefined)
  }

  async function regenererQrCode(hoteOverride?: string) {
    if (!id) return
    const hote = (hoteOverride ?? adresseReseau).trim() || window.location.host
    const url = `http://${hote}/mobile/sessions/${id}`
    setQrCodeUrl(url)
    setQrCodeImage(await QRCode.toDataURL(url, { width: 220, margin: 1 }))
  }

  useEffect(() => {
    if (qrCodeImage) {
      void regenererQrCode()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [adresseReseau])

  if (chargement) {
    return <p className="p-8 text-sm text-slate-500">Chargement...</p>
  }

  if (!session) {
    return <p className="p-8 text-sm text-red-600">Session introuvable.</p>
  }

  const resumeOnglet = resumesParType[ongletActif]

  return (
    <div className="mx-auto max-w-2xl px-4 py-8">
      <Link to="/" className="text-sm text-slate-500 hover:underline">
        &larr; Retour aux sessions
      </Link>

      <h1 className="mt-2 mb-1 text-2xl font-semibold text-slate-900">{session.titre}</h1>
      <p className="mb-6 text-sm text-slate-500">
        {new Date(session.dateCreation).toLocaleString('fr-FR')} - {session.statut}
      </p>

      <section className="mb-8">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
          Resume
        </h2>

        <div className="mb-3 flex gap-2">
          {ONGLETS_RESUME.map(({ valeur, libelle }) => (
            <button
              key={valeur}
              onClick={() => void afficherOnglet(valeur)}
              className={`rounded-lg px-3 py-1 text-sm ${
                ongletActif === valeur
                  ? 'bg-slate-900 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {libelle}
            </button>
          ))}
        </div>

        {ongletActif === 'DETAILLE' && !resumeOnglet && (
          <p className="text-sm text-slate-500">
            {session.statut === 'EN_COURS'
              ? 'Le resume sera genere a la fin de la session.'
              : !resumeAbandonne
                ? 'Resume en cours de generation...'
                : transcriptions.some((segment) => segment.statut === 'REUSSIE')
                  ? 'La generation du resume prend plus de temps que prevu.'
                  : "Aucun resume : aucune transcription n'a abouti pour cette session."}
          </p>
        )}

        {ongletActif !== 'DETAILLE' && generationEnCours === ongletActif && (
          <p className="text-sm text-slate-500">Generation en cours...</p>
        )}
        {ongletActif !== 'DETAILLE' && generationEnCours !== ongletActif && erreurGeneration && (
          <p className="text-sm text-red-600">{erreurGeneration}</p>
        )}
        {ongletActif !== 'DETAILLE' &&
          generationEnCours !== ongletActif &&
          !erreurGeneration &&
          resumeOnglet === undefined && <p className="text-sm text-slate-500">Chargement...</p>}

        {resumeOnglet && <ContenuResume resume={resumeOnglet} />}
      </section>

      <section className="mb-8">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
          Compte rendu complet (Entreprise)
        </h2>

        {!compteRendu && (
          <button
            onClick={() => void genererLeCompteRendu()}
            disabled={compteRenduEnCours}
            className="rounded-lg bg-slate-100 px-3 py-1 text-sm text-slate-600 hover:bg-slate-200"
          >
            {compteRenduEnCours ? 'Generation en cours...' : 'Generer le compte rendu complet'}
          </button>
        )}

        {erreurCompteRendu && <p className="mt-2 text-sm text-red-600">{erreurCompteRendu}</p>}

        {compteRendu && compteRendu.statut === 'ECHEC' && (
          <p className="text-sm text-red-600">La generation du compte rendu a echoue.</p>
        )}

        {compteRendu && compteRendu.statut === 'REUSSI' && (
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="text-sm text-slate-800">{compteRendu.synthese}</p>

            {compteRendu.decisions.length > 0 && (
              <>
                <h3 className="mt-4 mb-1 text-xs font-medium uppercase tracking-wide text-slate-500">
                  Decisions
                </h3>
                <ul className="list-disc pl-5 text-sm text-slate-700">
                  {compteRendu.decisions.map((decision, index) => (
                    <li key={index}>{decision}</li>
                  ))}
                </ul>
              </>
            )}

            {compteRendu.actions.length > 0 && (
              <>
                <h3 className="mt-4 mb-1 text-xs font-medium uppercase tracking-wide text-slate-500">
                  Actions
                </h3>
                <ul className="flex flex-col gap-1 text-sm text-slate-700">
                  {compteRendu.actions.map((action, index) => (
                    <li key={index}>
                      {action.description}
                      {action.responsable && (
                        <span className="ml-2 text-xs text-slate-500">({action.responsable})</span>
                      )}
                      {action.echeance && (
                        <span className="ml-2 text-xs text-slate-400">- {action.echeance}</span>
                      )}
                    </li>
                  ))}
                </ul>
              </>
            )}
          </div>
        )}
      </section>

      <section className="mb-8">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
          Resume de cours (Ecole)
        </h2>

        {!resumeCours && (
          <button
            onClick={() => void genererLeResumeCours()}
            disabled={resumeCoursEnCours}
            className="rounded-lg bg-slate-100 px-3 py-1 text-sm text-slate-600 hover:bg-slate-200"
          >
            {resumeCoursEnCours ? 'Generation en cours...' : 'Generer le resume de cours'}
          </button>
        )}

        {erreurResumeCours && <p className="mt-2 text-sm text-red-600">{erreurResumeCours}</p>}

        {resumeCours && resumeCours.statut === 'ECHEC' && (
          <p className="text-sm text-red-600">La generation du resume de cours a echoue.</p>
        )}

        {resumeCours && resumeCours.statut === 'REUSSI' && (
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="text-sm text-slate-800">{resumeCours.synthese}</p>

            {resumeCours.notions.length > 0 && (
              <>
                <h3 className="mt-4 mb-1 text-xs font-medium uppercase tracking-wide text-slate-500">
                  Notions
                </h3>
                <ul className="flex flex-col gap-1 text-sm text-slate-700">
                  {resumeCours.notions.map((notion, index) => (
                    <li key={index}>
                      <span className="font-medium">{notion.terme}</span> : {notion.definition}
                    </li>
                  ))}
                </ul>
              </>
            )}

            {resumeCours.pointsARevoir.length > 0 && (
              <>
                <h3 className="mt-4 mb-1 text-xs font-medium uppercase tracking-wide text-slate-500">
                  Points a revoir
                </h3>
                <ul className="list-disc pl-5 text-sm text-slate-700">
                  {resumeCours.pointsARevoir.map((point, index) => (
                    <li key={index}>{point}</li>
                  ))}
                </ul>
              </>
            )}
          </div>
        )}
      </section>

      {engagements.length > 0 && (
        <section className="mb-8">
          <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
            Engagements
          </h2>
          <ul className="flex flex-col gap-2">
            {engagements.map((engagement) => (
              <li key={engagement.id} className="rounded-lg border border-slate-200 bg-white p-3 text-sm">
                <div className="mb-1 flex items-start justify-between gap-2">
                  <p className="text-slate-800">{engagement.description}</p>
                  <span className="shrink-0 text-xs font-medium text-slate-500">
                    {LIBELLE_STATUT_ENGAGEMENT[engagement.statut]}
                  </span>
                </div>
                {(engagement.responsable || engagement.echeance) && (
                  <p className="mb-2 text-xs text-slate-500">
                    {engagement.responsable && <span>{engagement.responsable}</span>}
                    {engagement.responsable && engagement.echeance && <span> - </span>}
                    {engagement.echeance && <span>{engagement.echeance}</span>}
                  </p>
                )}
                {engagement.statut === 'EN_ATTENTE' && (
                  <div className="flex gap-2">
                    <button
                      disabled={engagementEnCours === engagement.id}
                      onClick={() => void agirSurEngagement(engagement.id, confirmerEngagement)}
                      className="rounded-lg bg-slate-900 px-3 py-1 text-xs font-medium text-white hover:bg-slate-700 disabled:opacity-50"
                    >
                      Confirmer
                    </button>
                    <button
                      disabled={engagementEnCours === engagement.id}
                      onClick={() => void agirSurEngagement(engagement.id, rejeterEngagement)}
                      className="rounded-lg bg-slate-100 px-3 py-1 text-xs text-slate-600 hover:bg-slate-200 disabled:opacity-50"
                    >
                      Rejeter
                    </button>
                  </div>
                )}
                {engagement.statut === 'CONFIRME' && (
                  <button
                    disabled={engagementEnCours === engagement.id}
                    onClick={() => void agirSurEngagement(engagement.id, terminerEngagement)}
                    className="rounded-lg bg-slate-100 px-3 py-1 text-xs text-slate-600 hover:bg-slate-200 disabled:opacity-50"
                  >
                    Marquer comme termine
                  </button>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      <section>
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
          Transcription
        </h2>
        {transcriptions.length === 0 && (
          <p className="text-sm text-slate-500">Aucun segment transcrit pour le moment.</p>
        )}
        <ol className="flex flex-col gap-2">
          {transcriptions.map((segment) => (
            <li
              key={segment.numeroSequence}
              className="rounded-lg border border-slate-200 bg-white p-3 text-sm"
            >
              <span className="mr-2 font-mono text-xs text-slate-400">
                #{segment.numeroSequence}
              </span>
              {segment.statut !== 'REUSSIE' ? (
                <span className="italic text-red-600">Echec de la transcription</span>
              ) : segment.segmentsLocuteur.length > 0 ? (
                <div className="mt-1 flex flex-col gap-1">
                  {segment.segmentsLocuteur.map((locuteur, index) => (
                    <p key={index}>
                      <span className="mr-2 font-medium text-slate-500">
                        Intervenant {locuteur.locuteur}
                        {locuteur.nomUtilisateurIdentifie && ` (${locuteur.nomUtilisateurIdentifie})`}
                      </span>
                      <span className="text-slate-800">{locuteur.texte}</span>
                    </p>
                  ))}
                </div>
              ) : (
                <span className="text-slate-800">{segment.texte}</span>
              )}
            </li>
          ))}
        </ol>
      </section>

      <section className="mt-8">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-slate-500">
          Documents
        </h2>

        <div className="mb-3 flex flex-wrap gap-2">
          <label className="inline-block cursor-pointer rounded-lg bg-slate-100 px-3 py-1 text-sm text-slate-600 hover:bg-slate-200">
            {televersementEnCours ? 'Envoi en cours...' : 'Ajouter un PDF ou une photo'}
            <input
              type="file"
              accept="application/pdf,image/*"
              className="hidden"
              disabled={televersementEnCours}
              onChange={(e) => {
                void surSelectionFichier(e.target.files?.[0])
                e.target.value = ''
              }}
            />
          </label>

          <input
            type="text"
            value={adresseReseau}
            onChange={(e) => setAdresseReseau(e.target.value)}
            placeholder="Detectee automatiquement (modifiable si besoin)"
            title="Rempli automatiquement par le serveur. A ajuster seulement si plusieurs reseaux sont disponibles sur ce PC."
            className="w-64 rounded-lg border border-slate-200 px-2 py-1 text-sm"
          />

          <button
            onClick={() => void afficherQrCode()}
            className="rounded-lg bg-slate-100 px-3 py-1 text-sm text-slate-600 hover:bg-slate-200"
          >
            {qrCodeImage ? 'Masquer le QR code' : 'Ajouter depuis un telephone (QR code)'}
          </button>
        </div>

        {qrCodeImage && (
          <div className="mb-4 flex flex-col items-center gap-2 rounded-lg border border-slate-200 bg-white p-4">
            <img src={qrCodeImage} alt="QR code pour ajouter une photo depuis un telephone" />
            <p className="text-xs break-all text-slate-400">{qrCodeUrl}</p>
            <p className="text-xs text-slate-500">
              Scanne ce QR code avec un telephone connecte au meme reseau Wi-Fi. Le micro reste
              utilisable ici car cette page continue de tourner sur localhost.
            </p>
          </div>
        )}

        {erreurTeleversement && <p className="mb-2 text-sm text-red-600">{erreurTeleversement}</p>}

        {documents.length === 0 && (
          <p className="text-sm text-slate-500">Aucun document depose pour le moment.</p>
        )}
        <ul className="flex flex-col gap-2">
          {documents.map((document) => (
            <li
              key={document.id}
              className="rounded-lg border border-slate-200 bg-white p-3 text-sm"
            >
              <div className="mb-1 flex items-center gap-2">
                <span className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-500">
                  {document.type}
                </span>
                <span className="font-medium text-slate-800">{document.nomFichier}</span>
              </div>
              {document.statut === 'EN_ATTENTE' && (
                <p className="text-slate-500 italic">Extraction du texte en cours...</p>
              )}
              {document.statut === 'ECHEC' && (
                <p className="text-red-600 italic">Echec de l'extraction du texte.</p>
              )}
              {document.statut === 'REUSSI' && (
                <p className="whitespace-pre-wrap text-slate-800">{document.texteExtrait}</p>
              )}
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}

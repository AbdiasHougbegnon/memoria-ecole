import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import QRCode from 'qrcode'
import {
  genererResume,
  obtenirAdresseLocaleServeur,
  obtenirDocuments,
  obtenirResume,
  obtenirSession,
  obtenirTranscriptions,
  televerserDocument,
} from '../api'
import { obtenirModuleConnecte } from '../auth'
import { redimensionnerImageSiNecessaire } from '../redimensionnerImage'
import type { DocumentItem, Resume, ResumeType, Session, TranscriptionSegment } from '../types'
import { SessionDetailEntreprise } from './SessionDetailEntreprise'
import { SessionDetailEcole } from './SessionDetailEcole'

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

export function SectionTitre({ children }: { children: string }) {
  return <h2 className="mb-3 text-[15px] font-bold">{children}</h2>
}

export function Carte({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-2xl border bg-white p-5" style={{ borderColor: 'var(--color-border-soft)' }}>
      {children}
    </div>
  )
}

export function BoutonSecondaire(props: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const { className, style, ...rest } = props
  return (
    <button
      {...rest}
      className={`rounded-lg px-3 py-1.5 text-sm font-semibold disabled:opacity-50 ${className ?? ''}`}
      style={{ background: '#F4F2EE', color: 'var(--color-ink-muted)', ...style }}
    />
  )
}

function ContenuResume({ resume }: { resume: Resume }) {
  if (resume.statut === 'ECHEC') {
    return <p className="text-sm" style={{ color: '#B02631' }}>La generation du resume a echoue.</p>
  }
  return (
    <div
      className="rounded-2xl border p-5"
      style={{ borderColor: '#E4E2F6', background: 'linear-gradient(180deg,#F6F5FE,#FBFAFE)' }}
    >
      <p className="text-sm leading-relaxed">{resume.texteResume}</p>
      {resume.pointsCles.length > 0 && (
        <ul className="mt-3 list-disc pl-5 text-sm" style={{ color: 'var(--color-ink-muted)' }}>
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
  const module = obtenirModuleConnecte()
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
      const [s, t, r, d] = await Promise.all([
        obtenirSession(id!),
        obtenirTranscriptions(id!),
        obtenirResume(id!, 'DETAILLE'),
        obtenirDocuments(id!),
      ])
      if (annule) return

      setSession(s)
      setTranscriptions(t)
      setResumesParType((precedent) => ({ ...precedent, DETAILLE: r }))
      setDocuments(d)
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
    return <p className="p-8 text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>
  }

  if (!session) {
    return <p className="p-8 text-sm" style={{ color: '#B02631' }}>Session introuvable.</p>
  }

  const resumeOnglet = resumesParType[ongletActif]

  return (
    <div className="mx-auto max-w-[1020px] px-8 py-9">
      <Link to="/" className="text-[13px]" style={{ color: 'var(--color-ink-faint)' }}>
        &larr; Sessions
      </Link>

      <h1 className="mt-2.5 text-[27px] font-bold leading-tight tracking-tight">{session.titre}</h1>
      <div
        className="mt-3 flex items-center gap-4 text-[13px]"
        style={{ fontFamily: 'var(--font-mono)', color: 'var(--color-ink-faint)' }}
      >
        <span>{new Date(session.dateCreation).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' })}</span>
        <span>&middot;</span>
        <span>{session.statut}</span>
      </div>

      <div className="mt-7 grid grid-cols-[1fr_290px] items-start gap-8">
        <div className="flex min-w-0 flex-col gap-7">

          <section>
            <div className="mb-3 flex gap-2">
              {ONGLETS_RESUME.map(({ valeur, libelle }) => (
                <button
                  key={valeur}
                  onClick={() => void afficherOnglet(valeur)}
                  className="rounded-lg px-3 py-1 text-sm font-semibold transition-colors"
                  style={ongletActif === valeur
                    ? { background: 'var(--color-ink)', color: '#fff' }
                    : { background: '#F4F2EE', color: 'var(--color-ink-muted)' }}
                >
                  {libelle}
                </button>
              ))}
            </div>

            {ongletActif === 'DETAILLE' && !resumeOnglet && (
              <p className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>
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
              <p className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Generation en cours...</p>
            )}
            {ongletActif !== 'DETAILLE' && generationEnCours !== ongletActif && erreurGeneration && (
              <p className="text-sm" style={{ color: '#B02631' }}>{erreurGeneration}</p>
            )}
            {ongletActif !== 'DETAILLE' &&
              generationEnCours !== ongletActif &&
              !erreurGeneration &&
              resumeOnglet === undefined && <p className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>}

            {resumeOnglet && <ContenuResume resume={resumeOnglet} />}
          </section>

          {module === 'ENTREPRISE' && <SessionDetailEntreprise sessionId={id!} />}
          {module === 'ECOLE' && <SessionDetailEcole sessionId={id!} />}

          <section>
            <SectionTitre>Transcription</SectionTitre>
            {transcriptions.length === 0 && (
              <p className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Aucun segment transcrit pour le moment.</p>
            )}
            <div className="rounded-2xl border bg-white" style={{ borderColor: 'var(--color-border-soft)' }}>
              {transcriptions.map((segment, index) => (
                <div
                  key={segment.numeroSequence}
                  className="p-3.5 text-sm"
                  style={index > 0 ? { borderTop: '1px solid var(--color-border-softer)' } : undefined}
                >
                  <span className="mr-2 text-xs" style={{ fontFamily: 'var(--font-mono)', color: 'var(--color-ink-faint)' }}>
                    #{segment.numeroSequence}
                  </span>
                  {segment.statut !== 'REUSSIE' ? (
                    <span className="italic" style={{ color: '#B02631' }}>Echec de la transcription</span>
                  ) : segment.segmentsLocuteur.length > 0 ? (
                    <div className="mt-1 flex flex-col gap-1">
                      {segment.segmentsLocuteur.map((locuteur, i) => (
                        <p key={i}>
                          <span className="mr-2 font-medium" style={{ color: 'var(--color-ink-muted)' }}>
                            Intervenant {locuteur.locuteur}
                            {locuteur.nomUtilisateurIdentifie && ` (${locuteur.nomUtilisateurIdentifie})`}
                          </span>
                          <span>{locuteur.texte}</span>
                        </p>
                      ))}
                    </div>
                  ) : (
                    <span>{segment.texte}</span>
                  )}
                </div>
              ))}
            </div>
          </section>
        </div>

        <aside className="sticky top-6 flex flex-col gap-4">
          <div className="rounded-2xl border bg-white p-4" style={{ borderColor: 'var(--color-border-soft)' }}>
            <div className="mb-3 text-[11px] font-bold uppercase tracking-wide" style={{ color: 'var(--color-ink-faint-2)' }}>
              Documents lies
            </div>

            <div className="flex flex-col gap-2">
              <label
                className="inline-block cursor-pointer rounded-lg px-3 py-1.5 text-center text-xs font-semibold"
                style={{ background: '#F4F2EE', color: 'var(--color-ink-muted)' }}
              >
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
              <button
                onClick={() => void afficherQrCode()}
                className="rounded-lg px-3 py-1.5 text-xs font-semibold"
                style={{ background: '#F4F2EE', color: 'var(--color-ink-muted)' }}
              >
                {qrCodeImage ? 'Masquer le QR code' : 'Depuis un telephone (QR)'}
              </button>
            </div>

            {qrCodeImage && (
              <div className="mt-3 flex flex-col items-center gap-2 rounded-xl border p-3" style={{ borderColor: 'var(--color-border-softer)' }}>
                <img src={qrCodeImage} alt="QR code pour ajouter une photo depuis un telephone" />
                <p className="break-all text-[10.5px]" style={{ color: 'var(--color-ink-faint)' }}>{qrCodeUrl}</p>
                <p className="text-[10.5px]" style={{ color: 'var(--color-ink-muted)' }}>
                  Meme reseau Wi-Fi requis. Le micro reste utilisable ici.
                </p>
              </div>
            )}

            {erreurTeleversement && <p className="mt-2 text-xs" style={{ color: '#B02631' }}>{erreurTeleversement}</p>}

            {documents.length === 0 ? (
              <p className="mt-3 text-xs" style={{ color: 'var(--color-ink-muted)' }}>Aucun document depose.</p>
            ) : (
              <div className="mt-3 flex flex-col gap-2">
                {documents.map((document) => (
                  <details key={document.id} className="rounded-lg border p-2.5 text-xs" style={{ borderColor: 'var(--color-border-softer)' }}>
                    <summary className="flex cursor-pointer items-center gap-2">
                      <span className="rounded px-1.5 py-0.5" style={{ background: '#F4F2EE', color: 'var(--color-ink-muted)', fontFamily: 'var(--font-mono)', fontSize: '9px' }}>
                        {document.type}
                      </span>
                      <span className="truncate font-medium">{document.nomFichier}</span>
                    </summary>
                    {document.statut === 'EN_ATTENTE' && (
                      <p className="mt-2 italic" style={{ color: 'var(--color-ink-muted)' }}>Extraction en cours...</p>
                    )}
                    {document.statut === 'ECHEC' && (
                      <p className="mt-2 italic" style={{ color: '#B02631' }}>Echec de l'extraction.</p>
                    )}
                    {document.statut === 'REUSSI' && (
                      <p className="mt-2 whitespace-pre-wrap">{document.texteExtrait}</p>
                    )}
                  </details>
                ))}
              </div>
            )}
          </div>

          <Link
            to="/fils-memoire"
            className="flex items-center justify-between gap-2 rounded-2xl border p-4 transition-colors"
            style={{ borderColor: '#E4E2F6', background: 'var(--color-brand-wash)' }}
          >
            <div>
              <div className="text-[12.5px] font-semibold" style={{ color: 'var(--color-brand)' }}>Voir les fils de memoire</div>
              <div className="mt-0.5 text-[11.5px]" style={{ color: 'var(--color-ink-faint)' }}>Sessions du meme sujet</div>
            </div>
            <span style={{ color: 'var(--color-brand)' }}>&rarr;</span>
          </Link>
        </aside>
      </div>
    </div>
  )
}

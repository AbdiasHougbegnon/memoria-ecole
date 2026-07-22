import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { obtenirDocuments, obtenirSession, televerserDocument } from '../api'
import { redimensionnerImageSiNecessaire } from '../redimensionnerImage'
import type { DocumentItem, Session } from '../types'

export function MobileUploadPage() {
  const { id } = useParams<{ id: string }>()
  const [session, setSession] = useState<Session | null>(null)
  const [sessionIntrouvable, setSessionIntrouvable] = useState(false)
  const [documents, setDocuments] = useState<DocumentItem[]>([])
  const [envoiEnCours, setEnvoiEnCours] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)
  const [derniereEnvoyee, setDerniereEnvoyee] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    obtenirSession(id)
      .then(setSession)
      .catch(() => setSessionIntrouvable(true))
    obtenirDocuments(id)
      .then(setDocuments)
      .catch(() => {})
  }, [id])

  async function surPhotoCapturee(fichier: File | undefined) {
    if (!fichier || !id) return
    setErreur(null)
    setDerniereEnvoyee(null)
    setEnvoiEnCours(true)
    try {
      const fichierEnvoye = await redimensionnerImageSiNecessaire(fichier)
      const document = await televerserDocument(id, fichierEnvoye)
      setDocuments((precedent) => [...precedent, document])
      setDerniereEnvoyee(document.nomFichier)
    } catch {
      setErreur("Echec de l'envoi de la photo. Verifie ta connexion et reessaie.")
    } finally {
      setEnvoiEnCours(false)
    }
  }

  if (sessionIntrouvable) {
    return (
      <div style={{ background: 'var(--color-cream)', minHeight: '100vh' }} className="flex items-center justify-center p-6">
        <p className="text-sm" style={{ color: '#B02631' }}>Session introuvable.</p>
      </div>
    )
  }

  if (!session) {
    return (
      <div style={{ background: 'var(--color-cream)', minHeight: '100vh' }} className="flex items-center justify-center p-6">
        <p className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>
      </div>
    )
  }

  return (
    <div style={{ background: 'var(--color-cream)', minHeight: '100vh' }} className="mx-auto flex max-w-md flex-col items-center gap-4 px-4 py-12 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-2xl text-lg font-extrabold text-white" style={{ background: 'var(--color-brand)' }}>M</span>
      <h1 className="text-xl font-bold tracking-tight">{session.titre}</h1>
      <p className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>
        Prends en photo le tableau ou un document pour l'ajouter a cette session.
      </p>

      <label
        className="mt-4 w-full cursor-pointer rounded-2xl px-6 py-4 text-base font-semibold text-white"
        style={{ background: 'var(--color-brand)', boxShadow: '0 2px 10px rgba(75,70,214,.3)' }}
      >
        {envoiEnCours ? 'Envoi en cours...' : 'Prendre une photo'}
        <input
          type="file"
          accept="image/*"
          capture="environment"
          className="hidden"
          disabled={envoiEnCours}
          onChange={(e) => {
            void surPhotoCapturee(e.target.files?.[0])
            e.target.value = ''
          }}
        />
      </label>

      {derniereEnvoyee && (
        <p className="text-sm font-semibold" style={{ color: 'var(--color-ok)' }}>Photo envoyee : {derniereEnvoyee}</p>
      )}
      {erreur && <p className="text-sm" style={{ color: '#B02631' }}>{erreur}</p>}

      <p className="mt-6 text-xs" style={{ color: 'var(--color-ink-faint)' }}>
        {documents.length} document(s) deja envoye(s) pour cette session.
      </p>
    </div>
  )
}

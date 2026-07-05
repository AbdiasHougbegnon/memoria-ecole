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
    return <p className="p-6 text-center text-sm text-red-600">Session introuvable.</p>
  }

  if (!session) {
    return <p className="p-6 text-center text-sm text-slate-500">Chargement...</p>
  }

  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col items-center gap-4 bg-slate-50 px-4 py-10 text-center">
      <h1 className="text-lg font-semibold text-slate-900">{session.titre}</h1>
      <p className="text-sm text-slate-500">
        Prends en photo le tableau ou un document pour l'ajouter a cette session.
      </p>

      <label className="mt-4 w-full cursor-pointer rounded-xl bg-slate-900 px-6 py-4 text-base font-medium text-white active:bg-slate-800">
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
        <p className="text-sm text-green-700">Photo envoyee : {derniereEnvoyee}</p>
      )}
      {erreur && <p className="text-sm text-red-600">{erreur}</p>}

      <p className="mt-6 text-xs text-slate-400">
        {documents.length} document(s) deja envoye(s) pour cette session.
      </p>
    </div>
  )
}

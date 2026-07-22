import { useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { connecter, ErreurApi, inscrire } from '../api'
import { enregistrerSession } from '../auth'
import { IDENTITES_MODULE } from '../moduleIdentite'
import type { ModuleMemoria } from '../types'

function messageErreur(e: unknown, mode: 'connexion' | 'inscription'): string {
  if (e instanceof TypeError) {
    return 'Impossible de joindre le serveur. Verifie que le backend est demarre.'
  }
  if (e instanceof ErreurApi) {
    if (mode === 'connexion' && e.status === 401) {
      return 'Email ou mot de passe incorrect.'
    }
    if (mode === 'inscription' && e.status === 409) {
      return 'Cet email est deja utilise.'
    }
    if (mode === 'inscription' && e.status === 400) {
      return 'Email invalide ou mot de passe trop court (8 caracteres minimum).'
    }
  }
  return 'Une erreur inattendue est survenue. Reessaie.'
}

function estModuleValide(valeur: string | null): valeur is ModuleMemoria {
  return valeur === 'ECOLE' || valeur === 'ENTREPRISE'
}

export function LoginPage() {
  const [searchParams] = useSearchParams()
  const moduleParam = searchParams.get('module')

  if (!estModuleValide(moduleParam)) {
    return <Navigate to="/choix-module" replace />
  }

  return <FormulaireConnexion module={moduleParam} />
}

function FormulaireConnexion({ module }: { module: ModuleMemoria }) {
  const [mode, setMode] = useState<'connexion' | 'inscription'>('connexion')
  const [email, setEmail] = useState('')
  const [motDePasse, setMotDePasse] = useState('')
  const [erreur, setErreur] = useState<string | null>(null)
  const [enCours, setEnCours] = useState(false)
  const navigate = useNavigate()
  const identite = IDENTITES_MODULE[module]

  async function soumettre(e: React.FormEvent) {
    e.preventDefault()
    setErreur(null)
    setEnCours(true)
    try {
      // La connexion ne transmet jamais le module choisi sur cet ecran : le
      // module reel du compte vient toujours du serveur (voir enregistrerSession),
      // ce qui rend silencieux le cas d'un compte qui se connecte depuis le
      // "mauvais" ecran -- il atterrit simplement dans son propre module.
      const auth = mode === 'connexion'
        ? await connecter(email, motDePasse)
        : await inscrire(email, motDePasse, module)
      enregistrerSession(auth)
      navigate('/')
    } catch (e) {
      setErreur(messageErreur(e, mode))
    } finally {
      setEnCours(false)
    }
  }

  return (
    <div className="mx-auto flex min-h-screen max-w-sm flex-col justify-center px-4">
      <Link to="/choix-module" className="mb-4 text-sm text-slate-500 hover:text-slate-700">
        &larr; Choisir un autre module
      </Link>
      <h1 className={`mb-6 text-center text-2xl font-semibold ${identite.classeAccent}`}>
        {identite.nomProduit}
      </h1>

      <div className="mb-4 flex rounded-lg border border-slate-200 bg-white p-1 text-sm">
        <button
          type="button"
          onClick={() => setMode('connexion')}
          className={`flex-1 rounded-md py-1.5 font-medium ${mode === 'connexion' ? 'bg-slate-900 text-white' : 'text-slate-500'}`}
        >
          Connexion
        </button>
        <button
          type="button"
          onClick={() => setMode('inscription')}
          className={`flex-1 rounded-md py-1.5 font-medium ${mode === 'inscription' ? 'bg-slate-900 text-white' : 'text-slate-500'}`}
        >
          Inscription
        </button>
      </div>

      <form onSubmit={soumettre} className="flex flex-col gap-3">
        <input
          type="email"
          required
          placeholder="Email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="rounded-lg border border-slate-200 px-3 py-2 text-sm"
        />
        <input
          type="password"
          required
          minLength={8}
          placeholder="Mot de passe (8 caracteres minimum)"
          value={motDePasse}
          onChange={(e) => setMotDePasse(e.target.value)}
          className="rounded-lg border border-slate-200 px-3 py-2 text-sm"
        />
        {erreur && <p className="text-sm text-red-600">{erreur}</p>}
        <button
          type="submit"
          disabled={enCours}
          className={`rounded-lg px-4 py-2 text-sm font-medium text-white disabled:opacity-50 ${identite.classeBouton}`}
        >
          {enCours ? 'Patiente...' : mode === 'connexion' ? 'Se connecter' : "Creer le compte"}
        </button>
      </form>
    </div>
  )
}

import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { connecter, inscrire } from '../api'
import { enregistrerSession } from '../auth'

export function LoginPage() {
  const [mode, setMode] = useState<'connexion' | 'inscription'>('connexion')
  const [email, setEmail] = useState('')
  const [motDePasse, setMotDePasse] = useState('')
  const [erreur, setErreur] = useState<string | null>(null)
  const [enCours, setEnCours] = useState(false)
  const navigate = useNavigate()

  async function soumettre(e: React.FormEvent) {
    e.preventDefault()
    setErreur(null)
    setEnCours(true)
    try {
      const auth = mode === 'connexion'
        ? await connecter(email, motDePasse)
        : await inscrire(email, motDePasse)
      enregistrerSession(auth)
      navigate('/')
    } catch {
      setErreur(
        mode === 'connexion'
          ? 'Email ou mot de passe incorrect.'
          : "Impossible de creer le compte (email deja utilise ou mot de passe trop court).",
      )
    } finally {
      setEnCours(false)
    }
  }

  return (
    <div className="mx-auto flex min-h-screen max-w-sm flex-col justify-center px-4">
      <h1 className="mb-6 text-center text-2xl font-semibold text-slate-900">Memoria</h1>

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
          className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {enCours ? 'Patiente...' : mode === 'connexion' ? 'Se connecter' : "Creer le compte"}
        </button>
      </form>
    </div>
  )
}

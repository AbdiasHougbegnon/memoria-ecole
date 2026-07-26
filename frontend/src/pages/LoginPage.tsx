import { useEffect, useMemo, useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { connecter, ErreurApi, inscrire, inscrireEcole, obtenirOptionsInscriptionEcole } from '../api'
import { enregistrerSession } from '../auth'
import { IDENTITES_MODULE } from '../moduleIdentite'
import type { ModuleMemoria, OptionInscription } from '../types'

function messageErreur(e: unknown, mode: 'connexion' | 'inscription'): string {
  if (e instanceof TypeError) {
    return 'Impossible de joindre le serveur. Verifie que le backend est demarre.'
  }
  if (e instanceof ErreurApi) {
    if (mode === 'connexion' && e.status === 401) {
      return 'Email ou mot de passe incorrect.'
    }
    if (mode === 'inscription' && e.status === 409 && e.message.includes('ecole/inscription')) {
      return "Cette combinaison annee/filiere/specialite n'est pas encore disponible. Contacte ton etablissement."
    }
    if (mode === 'inscription' && e.status === 409) {
      return 'Cet email est deja utilise.'
    }
    if (mode === 'inscription' && e.status === 400) {
      return 'Email invalide ou mot de passe trop court (8 caracteres minimum).'
    }
    if (mode === 'inscription' && e.status === 403) {
      return "L'inscription est reservee aux emails de l'etablissement partenaire."
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
  const [options, setOptions] = useState<OptionInscription[] | null>(null)
  const [anneeAcademique, setAnneeAcademique] = useState('')
  const [filiere, setFiliere] = useState('')
  const [specialite, setSpecialite] = useState('')
  const navigate = useNavigate()
  const identite = IDENTITES_MODULE[module]
  const inscriptionEcole = module === 'ECOLE' && mode === 'inscription'

  // Le fichier importe par l'etablissement (voir docs/phases/phase-17a-import-matieres.md)
  // peuple ces options -- recuperees uniquement quand elles deviennent utiles,
  // pas a chaque chargement de l'ecran de connexion.
  useEffect(() => {
    if (inscriptionEcole && options === null) {
      obtenirOptionsInscriptionEcole().then(setOptions).catch(() => setOptions([]))
    }
  }, [inscriptionEcole, options])

  const annees = useMemo(
    () => [...new Set((options ?? []).map((o) => o.anneeAcademique))],
    [options],
  )
  const filieres = useMemo(
    () => [...new Set((options ?? []).filter((o) => o.anneeAcademique === anneeAcademique).map((o) => o.filiere))],
    [options, anneeAcademique],
  )
  const specialites = useMemo(
    () => [...new Set((options ?? [])
      .filter((o) => o.anneeAcademique === anneeAcademique && o.filiere === filiere)
      .map((o) => o.specialite ?? ''))],
    [options, anneeAcademique, filiere],
  )

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
        : inscriptionEcole
          ? await inscrireEcole(email, motDePasse, anneeAcademique, filiere, specialite)
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
    <div style={{ display: 'flex', minHeight: '100vh', background: 'var(--color-cream)' }}>
      <div
        className="hidden flex-1 flex-col justify-between p-14 text-white sm:flex"
        style={{
          background: 'radial-gradient(circle at 20% 20%, #6C67E8, transparent 55%), radial-gradient(circle at 80% 80%, #3B37B4, transparent 50%), var(--color-brand)',
        }}
      >
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-[11px] bg-white text-xl font-extrabold" style={{ color: 'var(--color-brand)' }}>M</div>
          <span className="text-xl font-bold tracking-tight">{identite.nomProduit}</span>
        </div>
        <div className="max-w-md">
          <div className="text-[30px] font-bold leading-tight tracking-tight">{identite.descriptif}</div>
        </div>
        <div />
      </div>

      <div className="flex flex-1 items-center justify-center p-10">
        <div className="w-full max-w-sm">
          <Link to="/choix-module" className="mb-6 inline-block text-sm" style={{ color: 'var(--color-ink-muted)' }}>
            &larr; Choisir un autre espace
          </Link>
          <h1 className="mb-1 text-2xl font-bold tracking-tight">
            {mode === 'connexion' ? 'Connexion' : 'Creer un compte'}
          </h1>
          <p className="mb-7 text-sm" style={{ color: 'var(--color-ink-muted)' }}>{identite.nomProduit}</p>

          <div
            className="mb-5 flex rounded-lg p-1 text-sm"
            style={{ background: '#E8E3DA' }}
          >
            <button
              type="button"
              onClick={() => setMode('connexion')}
              className="flex-1 rounded-md py-1.5 font-semibold transition-colors"
              style={mode === 'connexion' ? { background: '#fff', color: 'var(--color-ink)', boxShadow: '0 1px 3px rgba(20,18,15,.12)' } : { color: 'var(--color-ink-muted)' }}
            >
              Connexion
            </button>
            <button
              type="button"
              onClick={() => setMode('inscription')}
              className="flex-1 rounded-md py-1.5 font-semibold transition-colors"
              style={mode === 'inscription' ? { background: '#fff', color: 'var(--color-ink)', boxShadow: '0 1px 3px rgba(20,18,15,.12)' } : { color: 'var(--color-ink-muted)' }}
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
              className="rounded-lg border px-3.5 py-2.5 text-sm outline-none"
              style={{ borderColor: 'var(--color-border-soft)', background: '#FCFBF9' }}
            />
            <input
              type="password"
              required
              minLength={8}
              placeholder="Mot de passe (8 caracteres minimum)"
              value={motDePasse}
              onChange={(e) => setMotDePasse(e.target.value)}
              className="rounded-lg border px-3.5 py-2.5 text-sm outline-none"
              style={{ borderColor: 'var(--color-border-soft)', background: '#FCFBF9' }}
            />
            {inscriptionEcole && (
              <>
                <select
                  required
                  value={anneeAcademique}
                  onChange={(e) => { setAnneeAcademique(e.target.value); setFiliere(''); setSpecialite('') }}
                  className="rounded-lg border px-3.5 py-2.5 text-sm outline-none"
                  style={{ borderColor: 'var(--color-border-soft)', background: '#FCFBF9' }}
                >
                  <option value="" disabled>Annee academique</option>
                  {annees.map((annee) => <option key={annee} value={annee}>{annee}</option>)}
                </select>
                {anneeAcademique && (
                  <select
                    required
                    value={filiere}
                    onChange={(e) => { setFiliere(e.target.value); setSpecialite('') }}
                    className="rounded-lg border px-3.5 py-2.5 text-sm outline-none"
                    style={{ borderColor: 'var(--color-border-soft)', background: '#FCFBF9' }}
                  >
                    <option value="" disabled>Filiere</option>
                    {filieres.map((f) => <option key={f} value={f}>{f}</option>)}
                  </select>
                )}
                {filiere && specialites.some((s) => s !== '') && (
                  <select
                    required
                    value={specialite}
                    onChange={(e) => setSpecialite(e.target.value)}
                    className="rounded-lg border px-3.5 py-2.5 text-sm outline-none"
                    style={{ borderColor: 'var(--color-border-soft)', background: '#FCFBF9' }}
                  >
                    <option value="" disabled>Specialite</option>
                    {specialites.map((s) => <option key={s} value={s}>{s || 'Aucune specialite'}</option>)}
                  </select>
                )}
                {options !== null && annees.length === 0 && (
                  <p className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>
                    Aucune classe disponible pour le moment -- contacte ton etablissement.
                  </p>
                )}
              </>
            )}
            {erreur && <p className="text-sm" style={{ color: '#B02631' }}>{erreur}</p>}
            <button
              type="submit"
              disabled={enCours || (inscriptionEcole && (!anneeAcademique || !filiere))}
              className="rounded-lg py-2.5 text-sm font-semibold text-white transition-colors disabled:opacity-50"
              style={{ background: 'var(--color-brand)', boxShadow: '0 2px 10px rgba(75,70,214,.3)' }}
            >
              {enCours ? 'Patiente...' : mode === 'connexion' ? 'Se connecter' : 'Creer le compte'}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}

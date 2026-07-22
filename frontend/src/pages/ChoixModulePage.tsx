import { useNavigate } from 'react-router-dom'
import { IDENTITES_MODULE } from '../moduleIdentite'
import type { ModuleMemoria } from '../types'

const MODULES: ModuleMemoria[] = ['ECOLE', 'ENTREPRISE']

export function ChoixModulePage() {
  const navigate = useNavigate()

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col justify-center px-4">
      <h1 className="mb-2 text-center text-2xl font-semibold text-slate-900">Memoria</h1>
      <p className="mb-8 text-center text-sm text-slate-500">Vous etes...</p>

      <div className="grid gap-4 sm:grid-cols-2">
        {MODULES.map((module) => {
          const identite = IDENTITES_MODULE[module]
          return (
            <button
              key={module}
              type="button"
              onClick={() => navigate(`/connexion?module=${module}`)}
              className="flex flex-col items-start gap-2 rounded-xl border border-slate-200 bg-white p-6 text-left hover:border-slate-300 hover:shadow-sm"
            >
              <span className={`text-lg font-semibold ${identite.classeAccent}`}>{identite.nomProduit}</span>
              <span className="text-sm text-slate-500">{identite.descriptif}</span>
            </button>
          )
        })}
      </div>
    </div>
  )
}

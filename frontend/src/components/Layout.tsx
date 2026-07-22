import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { deconnecter, obtenirModuleConnecte } from '../auth'
import { IDENTITES_MODULE } from '../moduleIdentite'
import type { ModuleMemoria } from '../types'

const LIENS_PAR_MODULE: Record<ModuleMemoria, { to: string; libelle: string }[]> = {
  ENTREPRISE: [
    { to: '/fils-memoire', libelle: 'Fils de memoire' },
    { to: '/engagements', libelle: 'Engagements' },
    { to: '/tableau-de-bord', libelle: 'Tableau de bord' },
    { to: '/couloirs', libelle: 'Couloirs' },
    { to: '/recherche', libelle: 'Rechercher' },
    { to: '/parametres', libelle: 'Parametres' },
  ],
  ECOLE: [
    { to: '/fils-memoire', libelle: 'Fils de memoire' },
    { to: '/couloirs', libelle: 'Couloirs' },
    { to: '/recherche', libelle: 'Rechercher' },
    { to: '/parametres', libelle: 'Parametres' },
  ],
}

export function Layout({ children }: { children: ReactNode }) {
  // Repli defensif sur ENTREPRISE : ne devrait pas se produire, RouteProtegee
  // garantit deja une session authentifiee (donc un module persiste) avant de
  // rendre ce composant.
  const module = obtenirModuleConnecte() ?? 'ENTREPRISE'
  const identite = IDENTITES_MODULE[module]
  const navigate = useNavigate()

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white px-4 py-3">
        <div className="mx-auto flex max-w-2xl items-center justify-between">
          <Link to="/" className={`text-lg font-semibold ${identite.classeAccent}`}>
            {identite.nomProduit}
          </Link>
          <nav className="flex gap-4">
            {LIENS_PAR_MODULE[module].map((lien) => (
              <Link key={lien.to} to={lien.to} className="text-sm text-slate-500 hover:text-slate-700">
                {lien.libelle}
              </Link>
            ))}
            <button
              type="button"
              onClick={() => {
                deconnecter()
                navigate('/choix-module')
              }}
              className="text-sm text-slate-500 hover:text-slate-700"
            >
              Deconnexion
            </button>
          </nav>
        </div>
      </header>
      <main>{children}</main>
    </div>
  )
}

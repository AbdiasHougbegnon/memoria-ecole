import type { ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { deconnecter, obtenirEmailConnecte, obtenirModuleConnecte } from '../auth'
import { IDENTITES_MODULE } from '../moduleIdentite'

// Icones et structure de sidebar reprises de docs/maquette-initiale.html
// (prototype pre-Phase 1) : logo, bouton "Nouvelle session", nav commune,
// section specifique au module actif, profil + deconnexion en bas.
function IconeEcole() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
      <path d="M8 2 L15 5.5 L8 9 L1 5.5 Z" fill="currentColor" />
      <path d="M4 7.5 V11 C4 11 5.5 12.5 8 12.5 C10.5 12.5 12 11 12 11 V7.5" stroke="currentColor" strokeWidth="1.4" fill="none" />
    </svg>
  )
}

function IconeEntreprise() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
      <rect x="2" y="5" width="12" height="9" rx="1.4" stroke="currentColor" strokeWidth="1.4" />
      <path d="M6 5 V3.5 C6 3 6.3 2.5 7 2.5 H9 C9.7 2.5 10 3 10 3.5 V5" stroke="currentColor" strokeWidth="1.4" fill="none" />
    </svg>
  )
}

function IconeThread() {
  return (
    <svg width="17" height="17" viewBox="0 0 18 18" fill="none">
      <rect x="2.5" y="3.8" width="13" height="4" rx="1.5" stroke="currentColor" strokeWidth="1.5" />
      <rect x="2.5" y="10.2" width="13" height="4" rx="1.5" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  )
}

function IconeRecherche() {
  return (
    <svg width="17" height="17" viewBox="0 0 18 18" fill="none">
      <circle cx="8" cy="8" r="5" stroke="currentColor" strokeWidth="1.8" />
      <line x1="11.8" y1="11.8" x2="15.5" y2="15.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

function IconeCouloirs() {
  return (
    <svg width="17" height="17" viewBox="0 0 18 18" fill="none">
      <rect x="2.5" y="4" width="5" height="10" rx="1.4" stroke="currentColor" strokeWidth="1.5" />
      <rect x="10.5" y="4" width="5" height="10" rx="1.4" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  )
}

function IconeEngagements() {
  return (
    <svg width="17" height="17" viewBox="0 0 18 18" fill="none">
      <rect x="3" y="2.5" width="12" height="13" rx="1.6" stroke="currentColor" strokeWidth="1.5" />
      <path d="M6 7 L7.5 8.5 L10.5 5.5" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round" strokeLinejoin="round" />
      <line x1="6" y1="11.5" x2="12" y2="11.5" stroke="currentColor" strokeWidth="1.4" />
    </svg>
  )
}

function IconeTableauDeBord() {
  return (
    <svg width="17" height="17" viewBox="0 0 18 18" fill="none">
      <rect x="2.5" y="2.5" width="6" height="6" rx="1.6" fill="currentColor" />
      <rect x="9.5" y="2.5" width="6" height="6" rx="1.6" fill="currentColor" opacity=".55" />
      <rect x="2.5" y="9.5" width="6" height="6" rx="1.6" fill="currentColor" opacity=".55" />
      <rect x="9.5" y="9.5" width="6" height="6" rx="1.6" fill="currentColor" opacity=".35" />
    </svg>
  )
}

function IconeReglages() {
  return (
    <svg width="17" height="17" viewBox="0 0 18 18" fill="none">
      <circle cx="9" cy="9" r="2.4" stroke="currentColor" strokeWidth="1.5" />
      <path d="M9 2.5 V4 M9 14 V15.5 M15.5 9 H14 M4 9 H2.5 M13.6 4.4 L12.5 5.5 M5.5 12.5 L4.4 13.6 M13.6 13.6 L12.5 12.5 M5.5 5.5 L4.4 4.4" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  )
}

function IconePlus() {
  return (
    <svg width="15" height="15" viewBox="0 0 16 16" fill="none">
      <line x1="8" y1="3" x2="8" y2="13" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      <line x1="3" y1="8" x2="13" y2="8" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

function IconeDeconnexion() {
  return (
    <svg width="16" height="16" viewBox="0 0 18 18" fill="none">
      <path d="M11 3 H14 V15 H11 M7.5 6 L4 9 L7.5 12 M4 9 H11" stroke="currentColor" strokeWidth="1.4" fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function LienNav({ to, actif, children }: { to: string; actif: boolean; children: ReactNode }) {
  return (
    <Link
      to={to}
      className="flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13.5px] font-medium transition-colors"
      style={actif ? { background: 'var(--color-brand-wash)', color: 'var(--color-brand)', fontWeight: 600 } : { color: '#5C5850' }}
    >
      {children}
    </Link>
  )
}

export function Layout({ children }: { children: ReactNode }) {
  // Repli defensif sur ENTREPRISE : ne devrait pas se produire, RouteProtegee
  // garantit deja une session authentifiee (donc un module persiste) avant de
  // rendre ce composant.
  const module = obtenirModuleConnecte() ?? 'ENTREPRISE'
  const identite = IDENTITES_MODULE[module]
  const navigate = useNavigate()
  const location = useLocation()
  const email = obtenirEmailConnecte() ?? ''
  const initiales = email.slice(0, 2).toUpperCase()

  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden', background: 'var(--color-cream)' }}>
      <aside
        style={{ width: 266, flex: 'none', background: 'var(--color-sidebar)', borderRight: '1px solid var(--color-border-soft)' }}
        className="flex flex-col p-3.5 pb-3"
      >
        <div className="flex items-center gap-2.5 px-2 pb-4">
          <div
            className="flex h-8 w-8 items-center justify-center rounded-[9px] text-[17px] font-extrabold text-white"
            style={{ background: 'var(--color-brand)', boxShadow: '0 2px 6px rgba(75,70,214,.35)' }}
          >
            M
          </div>
          <span className="text-base font-bold tracking-tight">Memoria</span>
        </div>

        <div
          className="mb-3.5 flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-semibold"
          style={{ background: '#fff', color: 'var(--color-brand)', boxShadow: '0 1px 3px rgba(20,18,15,.12)' }}
        >
          {module === 'ECOLE' ? <IconeEcole /> : <IconeEntreprise />}
          {identite.nomProduit.replace('Memoria ', '')}
        </div>

        <button
          type="button"
          onClick={() => navigate('/')}
          className="flex items-center justify-center gap-2 rounded-[11px] px-3.5 py-2.5 text-[13.5px] font-semibold text-white transition-colors"
          style={{ background: 'var(--color-brand)', boxShadow: '0 2px 8px rgba(75,70,214,.28)' }}
        >
          <IconePlus />
          Nouvelle session
        </button>

        <nav className="mt-4 flex flex-1 flex-col gap-0.5 overflow-y-auto">
          <LienNav to="/fils-memoire" actif={location.pathname === '/fils-memoire'}>
            <IconeThread />
            Fils de memoire
          </LienNav>
          <LienNav to="/recherche" actif={location.pathname === '/recherche'}>
            <IconeRecherche />
            Rechercher
          </LienNav>
          <LienNav to="/couloirs" actif={location.pathname.startsWith('/couloirs')}>
            <IconeCouloirs />
            {identite.libelleCouloirs}
          </LienNav>

          {module === 'ENTREPRISE' && (
            <>
              <div className="px-2.5 pt-3.5 pb-1.5 text-[10px] font-bold uppercase tracking-wide" style={{ color: 'var(--color-ink-faint-2)' }}>
                Entreprise
              </div>
              <LienNav to="/engagements" actif={location.pathname === '/engagements'}>
                <IconeEngagements />
                Engagements
              </LienNav>
              <LienNav to="/tableau-de-bord" actif={location.pathname === '/tableau-de-bord'}>
                <IconeTableauDeBord />
                Tableau de bord
              </LienNav>
            </>
          )}

          <div className="mx-1.5 my-3" style={{ height: 1, background: 'var(--color-border-soft)' }} />
          <LienNav to="/parametres" actif={location.pathname === '/parametres'}>
            <IconeReglages />
            Parametres
          </LienNav>
        </nav>

        <button
          type="button"
          onClick={() => {
            deconnecter()
            navigate('/choix-module')
          }}
          className="mt-3 flex items-center gap-2.5 rounded-xl border p-2 text-left transition-colors"
          style={{ borderColor: 'var(--color-border-soft)', background: '#FBFAF7' }}
        >
          <div
            className="flex h-8 w-8 flex-none items-center justify-center rounded-full text-[12.5px] font-semibold text-white"
            style={{ background: 'var(--color-brand)' }}
          >
            {initiales || '?'}
          </div>
          <div className="min-w-0 flex-1 leading-tight">
            <div className="truncate text-[13px] font-semibold">{email}</div>
            <div className="truncate text-[11px]" style={{ color: 'var(--color-ink-faint)' }}>Deconnexion</div>
          </div>
          <span style={{ color: 'var(--color-ink-faint-2)' }}>
            <IconeDeconnexion />
          </span>
        </button>
      </aside>

      <main style={{ flex: 1, minWidth: 0, overflowY: 'auto' }}>{children}</main>
    </div>
  )
}

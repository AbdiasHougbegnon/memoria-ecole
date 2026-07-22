import { useNavigate } from 'react-router-dom'
import { IDENTITES_MODULE } from '../moduleIdentite'
import type { ModuleMemoria } from '../types'

const MODULES: ModuleMemoria[] = ['ECOLE', 'ENTREPRISE']

function IconeModule({ module }: { module: ModuleMemoria }) {
  if (module === 'ECOLE') {
    return (
      <svg width="20" height="20" viewBox="0 0 16 16" fill="none">
        <path d="M8 2 L15 5.5 L8 9 L1 5.5 Z" fill="currentColor" />
        <path d="M4 7.5 V11 C4 11 5.5 12.5 8 12.5 C10.5 12.5 12 11 12 11 V7.5" stroke="currentColor" strokeWidth="1.4" fill="none" />
      </svg>
    )
  }
  return (
    <svg width="20" height="20" viewBox="0 0 16 16" fill="none">
      <rect x="2" y="5" width="12" height="9" rx="1.4" stroke="currentColor" strokeWidth="1.4" />
      <path d="M6 5 V3.5 C6 3 6.3 2.5 7 2.5 H9 C9.7 2.5 10 3 10 3.5 V5" stroke="currentColor" strokeWidth="1.4" fill="none" />
    </svg>
  )
}

export function ChoixModulePage() {
  const navigate = useNavigate()

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
          <span className="text-xl font-bold tracking-tight">Memoria</span>
        </div>
        <div className="max-w-md">
          <div className="text-[34px] font-bold leading-tight tracking-tight">
            Chaque reunion, chaque cours — capture, resume, retrouvable.
          </div>
          <p className="mt-5 text-[15.5px] leading-relaxed" style={{ color: '#DEDCFB' }}>
            Un seul moteur, deux produits. Memoria transcrit vos sessions, en extrait les decisions et les notions, et repond a vos questions en langage naturel.
          </p>
        </div>
        <div className="flex gap-6 text-[13px]" style={{ color: '#CFCCF6' }}>
          <span>Transcription temps reel</span>
          <span>Resumes IA</span>
          <span>Recherche semantique</span>
        </div>
      </div>

      <div className="flex flex-1 items-center justify-center p-10">
        <div className="w-full max-w-md">
          <h1 className="mb-1 text-2xl font-bold tracking-tight">Vous etes...</h1>
          <p className="mb-8 text-sm" style={{ color: 'var(--color-ink-muted)' }}>Choisissez votre espace pour continuer.</p>

          <div className="flex flex-col gap-3">
            {MODULES.map((module) => {
              const identite = IDENTITES_MODULE[module]
              return (
                <button
                  key={module}
                  type="button"
                  onClick={() => navigate(`/connexion?module=${module}`)}
                  className="flex items-center gap-4 rounded-2xl border bg-white p-5 text-left transition-shadow hover:shadow-md"
                  style={{ borderColor: 'var(--color-border-soft)' }}
                >
                  <div
                    className="flex h-11 w-11 flex-none items-center justify-center rounded-xl"
                    style={{ background: 'var(--color-brand-wash)', color: 'var(--color-brand)' }}
                  >
                    <IconeModule module={module} />
                  </div>
                  <div>
                    <div className="text-[15.5px] font-semibold">{identite.nomProduit}</div>
                    <div className="mt-0.5 text-[13px]" style={{ color: 'var(--color-ink-muted)' }}>{identite.descriptif}</div>
                  </div>
                </button>
              )
            })}
          </div>
        </div>
      </div>
    </div>
  )
}

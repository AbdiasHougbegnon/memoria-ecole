import { Link } from 'react-router-dom'
import type { Couloir, Matiere } from '../types'

const ONGLETS = [
  { chemin: '', label: 'Aperçu' },
  { chemin: '/documents', label: 'Documents' },
  { chemin: '/revision', label: 'Révision' },
] as const

// Une matiere avait toutes ses fonctionnalites empilees sur un seul ecran
// (notions, seances, documents, QCM, exercices, travail papier) -- separe en
// pages distinctes par fonctionnalite, avec une sous-navigation partagee,
// plutot que de continuer a tout entasser sur /matieres/:id.
export function MatiereSousNav({
  matiere,
  couloir,
  actif,
}: {
  matiere: Matiere
  couloir: Couloir
  actif: '' | '/documents' | '/revision'
}) {
  return (
    <>
      <Link to={`/couloirs/${couloir.id}/matieres`} className="mb-4 inline-flex items-center gap-1.5 text-sm" style={{ color: 'var(--color-ink-faint)' }}>
        &larr; Retour aux matieres
      </Link>
      <h1 className="text-[26px] font-bold tracking-tight">{matiere.nom}</h1>
      <nav className="mt-5 flex gap-1 border-b" style={{ borderColor: 'var(--color-border-soft)' }}>
        {ONGLETS.map((onglet) => (
          <Link
            key={onglet.chemin}
            to={`/matieres/${matiere.id}${onglet.chemin}`}
            className="px-3.5 py-2.5 text-sm font-semibold"
            style={
              actif === onglet.chemin
                ? { color: 'var(--color-brand)', borderBottom: '2px solid var(--color-brand)', marginBottom: -1 }
                : { color: 'var(--color-ink-muted)' }
            }
          >
            {onglet.label}
          </Link>
        ))}
      </nav>
    </>
  )
}

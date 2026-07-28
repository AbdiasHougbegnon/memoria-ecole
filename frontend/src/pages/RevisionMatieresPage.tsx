import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listerCouloirs, listerMesMatieres } from '../api'
import type { Couloir, Matiere } from '../types'

// Point d'entree "Revision" du menu de navigation (voir Layout.tsx) : avant
// cet increment, reviser une matiere obligeait a se souvenir de son couloir
// (Couloirs -> matieres -> matiere -> Revision), sans raccourci direct.
export function RevisionMatieresPage() {
  const [matieres, setMatieres] = useState<Matiere[]>([])
  const [couloirs, setCouloirs] = useState<Couloir[]>([])
  const [chargement, setChargement] = useState(true)

  useEffect(() => {
    Promise.all([listerMesMatieres(), listerCouloirs()])
      .then(([m, c]) => {
        setMatieres(m)
        setCouloirs(c)
      })
      .finally(() => setChargement(false))
  }, [])

  function nomCouloir(couloirId: string): string {
    return couloirs.find((c) => c.id === couloirId)?.nom ?? ''
  }

  if (chargement) {
    return <p className="p-6 text-center text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>
  }

  return (
    <div className="mx-auto max-w-[900px] px-8 py-10">
      <h1 className="text-[26px] font-bold tracking-tight">Revision</h1>
      <p className="mt-1 text-sm" style={{ color: 'var(--color-ink-muted)' }}>
        Choisis une matiere pour reviser progressivement : QCM, exercices a reponse libre et travail papier, generes
        a partir de tous ses cours et documents.
      </p>

      {matieres.length === 0 && (
        <p className="mt-8 text-sm" style={{ color: 'var(--color-ink-muted)' }}>
          Aucune matiere pour le moment -- rejoins un couloir de classe pour en voir apparaitre.
        </p>
      )}

      <ul className="mt-7 flex flex-col gap-2.5">
        {matieres.map((matiere) => (
          <li key={matiere.id}>
            <Link
              to={`/matieres/${matiere.id}/revision`}
              className="flex items-center justify-between rounded-2xl border bg-white px-5 py-4 transition-all hover:-translate-y-px hover:shadow-md"
              style={{ borderColor: 'var(--color-border-soft)' }}
            >
              <p className="font-semibold">{matiere.nom}</p>
              <span className="text-xs" style={{ color: 'var(--color-ink-faint)' }}>{nomCouloir(matiere.couloirId)}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}

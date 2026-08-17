import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listerCouloirs, listerMesMatieres } from '../api'
import type { Couloir, Matiere } from '../types'

// Point d'entree "Tutorat" du menu de navigation (voir Layout.tsx) : liste
// les matieres, chacune menant a MatiereApercuPage (ses seances, chacune
// avec son propre tutorat structure a notions cochees -- voir
// SeanceDetailPage -- et un bouton "Discussion libre" separe pour toute la
// matiere). Ne demarre plus rien directement ici : avant, cliquer une
// matiere lancait toujours une discussion libre, ce qui rendait le tutorat
// structure inaccessible depuis ce menu.
export function TutoratMatieresPage() {
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
      <h1 className="text-[26px] font-bold tracking-tight">Tutorat</h1>
      <p className="mt-1 text-sm" style={{ color: 'var(--color-ink-muted)' }}>
        Choisis une matiere : tu y retrouveras ses seances (tutorat structure, notions a maitriser puis
        exercices) et une discussion libre pour poser rapidement une question.
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
              to={`/matieres/${matiere.id}`}
              className="flex w-full items-center justify-between rounded-2xl border bg-white px-5 py-4 text-left transition-all hover:-translate-y-px hover:shadow-md"
              style={{ borderColor: 'var(--color-border-soft)' }}
            >
              <span className="font-semibold">{matiere.nom}</span>
              <span className="text-xs" style={{ color: 'var(--color-ink-faint)' }}>{nomCouloir(matiere.couloirId)}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}

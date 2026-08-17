import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { demarrerTutoratMatiere, listerCouloirs, listerMesMatieres } from '../api'
import type { Couloir, Matiere } from '../types'

// Seule porte d'entree vers la discussion libre (voir Layout.tsx, entree de
// menu "Tuteur de cours") : demarre directement une discussion libre sur la
// matiere choisie, sans notion cochee ni exercice -- distinct du tutorat
// structure ("Etudier", TutoratMatieresPage -> MatiereApercuPage -> seances).
export function TuteurDeCoursMatieresPage() {
  const [matieres, setMatieres] = useState<Matiere[]>([])
  const [couloirs, setCouloirs] = useState<Couloir[]>([])
  const [chargement, setChargement] = useState(true)
  const [demarrageEnCoursId, setDemarrageEnCoursId] = useState<string | null>(null)
  const [erreur, setErreur] = useState<string | null>(null)
  const navigate = useNavigate()

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

  async function demarrer(matiereId: string) {
    setErreur(null)
    setDemarrageEnCoursId(matiereId)
    try {
      const resultat = await demarrerTutoratMatiere(matiereId)
      navigate(`/tutorat/${resultat.seanceTutoratId}`)
    } catch {
      setErreur('Impossible de demarrer la discussion avec le tuteur.')
      setDemarrageEnCoursId(null)
    }
  }

  if (chargement) {
    return <p className="p-6 text-center text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>
  }

  return (
    <div className="mx-auto max-w-[900px] px-8 py-10">
      <h1 className="text-[26px] font-bold tracking-tight">Tuteur de cours</h1>
      <p className="mt-1 text-sm" style={{ color: 'var(--color-ink-muted)' }}>
        Choisis une matiere pour discuter directement avec le tuteur vocal -- il s'appuie sur tous ses cours,
        documents et travaux papier deja soumis. Pas de notion a cocher, pas d'exercice : juste une question rapide.
      </p>
      {erreur && <p className="mt-3 text-sm" style={{ color: '#B02631' }}>{erreur}</p>}

      {matieres.length === 0 && (
        <p className="mt-8 text-sm" style={{ color: 'var(--color-ink-muted)' }}>
          Aucune matiere pour le moment -- rejoins un couloir de classe pour en voir apparaitre.
        </p>
      )}

      <ul className="mt-7 flex flex-col gap-2.5">
        {matieres.map((matiere) => (
          <li key={matiere.id}>
            <button
              onClick={() => void demarrer(matiere.id)}
              disabled={demarrageEnCoursId === matiere.id}
              className="flex w-full items-center justify-between rounded-2xl border bg-white px-5 py-4 text-left transition-all hover:-translate-y-px hover:shadow-md disabled:opacity-60"
              style={{ borderColor: 'var(--color-border-soft)' }}
            >
              <span className="font-semibold">{matiere.nom}</span>
              <span className="text-xs" style={{ color: 'var(--color-ink-faint)' }}>
                {demarrageEnCoursId === matiere.id ? 'Ouverture...' : nomCouloir(matiere.couloirId)}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

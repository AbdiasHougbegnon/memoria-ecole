import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  demarrerTutorat,
  listerNotionsDeSeance,
  listerNotionsParMatiere,
  obtenirCouloir,
  obtenirMaitriseSeance,
  obtenirSeance,
  rattacherNotions,
} from '../api'
import type { Couloir, NiveauMaitrise, Notion, Seance } from '../types'

const LIBELLE_MAITRISE: Record<NiveauMaitrise, string> = {
  NON_ABORDEE: 'Non abordee',
  EN_COURS: 'En cours',
  MAITRISEE: 'Maitrisee',
}

const COULEUR_MAITRISE: Record<NiveauMaitrise, string> = {
  NON_ABORDEE: 'var(--color-ink-faint)',
  EN_COURS: '#C99A2E',
  MAITRISEE: '#2E9E6B',
}

export function SeanceDetailPage() {
  const { id: seanceId } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [seance, setSeance] = useState<Seance | null>(null)
  const [couloir, setCouloir] = useState<Couloir | null>(null)
  const [toutesLesNotions, setToutesLesNotions] = useState<Notion[]>([])
  const [notionsRattachees, setNotionsRattachees] = useState<string[]>([])
  const [maitrise, setMaitrise] = useState<Record<string, NiveauMaitrise>>({})
  const [chargement, setChargement] = useState(true)
  const [modeExercice, setModeExercice] = useState(false)
  const [demarrageEnCours, setDemarrageEnCours] = useState(false)
  const [demarrageLibreEnCours, setDemarrageLibreEnCours] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)

  async function rafraichir() {
    if (!seanceId) return
    setChargement(true)
    try {
      const s = await obtenirSeance(seanceId)
      const [c, toutes, rattachees, maitriseObtenue] = await Promise.all([
        obtenirCouloir(s.couloirId),
        listerNotionsParMatiere(s.matiereId),
        listerNotionsDeSeance(seanceId),
        obtenirMaitriseSeance(seanceId),
      ])
      setSeance(s)
      setCouloir(c)
      setToutesLesNotions(toutes)
      setNotionsRattachees(rattachees.map((n) => n.id))
      setMaitrise(maitriseObtenue)
    } finally {
      setChargement(false)
    }
  }

  useEffect(() => {
    void rafraichir()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [seanceId])

  function basculerNotion(notionId: string) {
    setNotionsRattachees((precedent) =>
      precedent.includes(notionId) ? precedent.filter((id) => id !== notionId) : [...precedent, notionId],
    )
  }

  async function enregistrerNotionsRattachees() {
    if (!seanceId) return
    setErreur(null)
    try {
      await rattacherNotions(seanceId, notionsRattachees)
      await rafraichir()
    } catch {
      setErreur('Impossible de mettre a jour les notions de la seance.')
    }
  }

  async function gererDemarrage() {
    if (!seanceId) return
    setErreur(null)
    setDemarrageEnCours(true)
    try {
      // Sauvegarde automatiquement l'etat des cases cochees a l'ecran avant
      // de demarrer, plutot que de dependre d'un clic separe sur
      // "Enregistrer" qui peut etre oublie -- une notion ajoutee puis cochee
      // sans cliquer "Enregistrer" bloquait sinon le tutorat sur "toutes les
      // notions sont deja maitrisees" (voir docs/phases/phase-16-corrections-tuteur-vocal.md).
      await rattacherNotions(seanceId, notionsRattachees)
      const resultat = await demarrerTutorat(seanceId, modeExercice ? 'EXERCICE' : 'EXPLICATION')
      navigate(`/tutorat/${resultat.seanceTutoratId}`)
    } catch {
      setErreur('Impossible de demarrer le tutorat.')
    } finally {
      setDemarrageEnCours(false)
    }
  }

  // Mode conversation libre : aucune notion a rattacher au prealable, le
  // bouton reste actif meme sans aucune notion cochee (voir
  // docs/phases/phase-19-mode-conversation-libre.md).
  async function gererDemarrageLibre() {
    if (!seanceId) return
    setErreur(null)
    setDemarrageLibreEnCours(true)
    try {
      const resultat = await demarrerTutorat(seanceId, 'LIBRE')
      navigate(`/tutorat/${resultat.seanceTutoratId}`)
    } catch {
      setErreur('Impossible de demarrer la discussion libre.')
    } finally {
      setDemarrageLibreEnCours(false)
    }
  }

  if (chargement || !seance || !couloir) {
    return <p className="p-6 text-center text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>
  }

  return (
    <div className="mx-auto max-w-[800px] px-8 py-10">
      <Link to={`/matieres/${seance.matiereId}`} className="mb-4 inline-flex items-center gap-1.5 text-sm" style={{ color: 'var(--color-ink-faint)' }}>
        &larr; Retour a la matiere
      </Link>
      <h1 className="text-[26px] font-bold tracking-tight">{seance.titre}</h1>
      {erreur && <p className="mt-3 text-sm" style={{ color: '#B02631' }}>{erreur}</p>}

      <div className="mt-6">
        <h2 className="text-sm font-bold">Notions de cette seance</h2>
        <p className="mt-1 text-xs" style={{ color: 'var(--color-ink-muted)' }}>
          Coche les notions de la matiere que le tuteur doit couvrir pour cette seance.
        </p>
        <ul className="mt-3 flex flex-col gap-1.5">
          {toutesLesNotions.map((notion) => (
            <li key={notion.id} className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={notionsRattachees.includes(notion.id)}
                onChange={() => basculerNotion(notion.id)}
              />
              <span>{notion.terme}</span>
            </li>
          ))}
        </ul>
        <button
          onClick={enregistrerNotionsRattachees}
          className="mt-3 rounded-lg px-3.5 py-1.5 text-xs font-semibold text-white"
          style={{ background: 'var(--color-brand)' }}
        >
          Enregistrer
        </button>
      </div>

      <div className="mt-7">
        <h2 className="text-sm font-bold">Progression</h2>
        <ul className="mt-3 flex flex-wrap gap-2">
          {toutesLesNotions
            .filter((n) => notionsRattachees.includes(n.id))
            .map((notion) => (
              <li
                key={notion.id}
                className="rounded-full px-3 py-1 text-xs font-semibold"
                style={{ background: '#F4F2EE', color: COULEUR_MAITRISE[maitrise[notion.id] ?? 'NON_ABORDEE'] }}
              >
                {notion.terme} &middot; {LIBELLE_MAITRISE[maitrise[notion.id] ?? 'NON_ABORDEE']}
              </li>
            ))}
        </ul>
      </div>

      <div className="mt-8 rounded-2xl border bg-white p-5" style={{ borderColor: 'var(--color-border-soft)' }}>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={modeExercice} onChange={(e) => setModeExercice(e.target.checked)} />
          Mode exercices (le tuteur pose directement des exercices, sans re-expliquer)
        </label>
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <button
            onClick={gererDemarrage}
            disabled={demarrageEnCours || notionsRattachees.length === 0}
            className="rounded-lg px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-50"
            style={{ background: 'var(--color-brand)' }}
          >
            {demarrageEnCours ? 'Demarrage...' : 'Demarrer le tutorat'}
          </button>
          <button
            onClick={gererDemarrageLibre}
            disabled={demarrageLibreEnCours}
            className="rounded-lg border px-5 py-2.5 text-sm font-semibold disabled:opacity-50"
            style={{ borderColor: 'var(--color-border-soft)', color: 'var(--color-ink)' }}
          >
            {demarrageLibreEnCours ? 'Demarrage...' : 'Discussion libre'}
          </button>
        </div>
        {notionsRattachees.length === 0 && (
          <p className="mt-2 text-xs" style={{ color: 'var(--color-ink-faint)' }}>
            Aucune notion rattachee a cette seance pour le moment. La discussion libre reste disponible.
          </p>
        )}
      </div>
    </div>
  )
}

import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { arreterTutorat, listerNotionsDeSeance, obtenirAudioTutorat, obtenirEtatTutorat, obtenirMaitriseSeance, soumettreReponseTutorat } from '../api'
import { useTutorRecorder } from '../hooks/useTutorRecorder'
import type { EtatTutorat, NiveauMaitrise, Notion } from '../types'

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

export function TuteurVocalPage() {
  const { seanceTutoratId: id } = useParams<{ seanceTutoratId: string }>()
  const navigate = useNavigate()
  const [etat, setEtat] = useState<EtatTutorat | null>(null)
  const [notions, setNotions] = useState<Notion[]>([])
  const [maitrise, setMaitrise] = useState<Record<string, NiveauMaitrise>>({})
  const [chargement, setChargement] = useState(true)
  const [envoiEnCours, setEnvoiEnCours] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)
  const { enregistrement, erreur: erreurMicro, demarrer, arreter } = useTutorRecorder()

  const dernierTourJoueRef = useRef<string | null>(null)
  const lecteurAudioRef = useRef<HTMLAudioElement | null>(null)

  async function rafraichir() {
    if (!id) return
    const nouvelEtat = await obtenirEtatTutorat(id)
    setEtat(nouvelEtat)
    const [n, m] = await Promise.all([listerNotionsDeSeance(nouvelEtat.seanceId), obtenirMaitriseSeance(nouvelEtat.seanceId)])
    setNotions(n)
    setMaitrise(m)
    return nouvelEtat
  }

  useEffect(() => {
    setChargement(true)
    rafraichir().finally(() => setChargement(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  // Joue automatiquement l'audio du dernier tour du tuteur des qu'il arrive
  // -- fetch authentifie + URL de blob, pas un <audio src=...> direct (voir
  // api.ts, obtenirAudioTutorat).
  useEffect(() => {
    if (!etat) return
    const tours = etat.tours
    const dernier = tours.length > 0 ? tours[tours.length - 1] : null
    if (!dernier || dernier.locuteur !== 'TUTEUR' || !dernier.audioUrl) return
    if (dernierTourJoueRef.current === dernier.id) return
    dernierTourJoueRef.current = dernier.id
    obtenirAudioTutorat(dernier.audioUrl)
      .then((blob) => {
        const url = URL.createObjectURL(blob)
        if (lecteurAudioRef.current) {
          lecteurAudioRef.current.src = url
          void lecteurAudioRef.current.play().catch(() => {})
        }
      })
      .catch(() => {})
  }, [etat])

  async function gererRelachement() {
    if (!id) return
    const blob = await arreter()
    if (!blob || blob.size === 0) return
    setEnvoiEnCours(true)
    setErreur(null)
    try {
      await soumettreReponseTutorat(id, blob)
      await rafraichir()
    } catch {
      setErreur("Le tuteur n'a pas pu repondre, reessaie.")
    } finally {
      setEnvoiEnCours(false)
    }
  }

  async function gererArret() {
    if (!id || !etat) return
    await arreterTutorat(id)
    navigate(`/seances/${etat.seanceId}`)
  }

  if (chargement || !etat) {
    return <p className="p-6 text-center text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>
  }

  const seanceTerminee = etat.statut === 'TERMINEE'

  return (
    <div className="mx-auto flex max-w-[700px] flex-col gap-5 px-8 py-10">
      <audio ref={lecteurAudioRef} />

      <div>
        <h1 className="text-[22px] font-bold tracking-tight">Tuteur vocal</h1>
        <ul className="mt-3 flex flex-wrap gap-2">
          {notions.map((notion) => (
            <li
              key={notion.id}
              className="rounded-full px-3 py-1 text-xs font-semibold"
              style={{
                background: notion.id === etat.notionCouranteId ? '#EDEBFB' : '#F4F2EE',
                color: COULEUR_MAITRISE[maitrise[notion.id] ?? 'NON_ABORDEE'],
              }}
            >
              {notion.terme} &middot; {LIBELLE_MAITRISE[maitrise[notion.id] ?? 'NON_ABORDEE']}
            </li>
          ))}
        </ul>
      </div>

      <div className="flex flex-col gap-3 rounded-2xl border bg-white p-5" style={{ borderColor: 'var(--color-border-soft)', minHeight: 300 }}>
        {etat.tours.length === 0 && (
          <p className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Le tuteur va commencer...</p>
        )}
        {etat.tours.map((tour) => (
          <div key={tour.id} className={tour.locuteur === 'TUTEUR' ? 'self-start' : 'self-end'}>
            <div
              className="max-w-[80%] rounded-2xl px-4 py-2.5 text-sm"
              style={{
                background: tour.locuteur === 'TUTEUR' ? '#F4F2EE' : 'var(--color-brand)',
                color: tour.locuteur === 'TUTEUR' ? 'var(--color-ink)' : 'white',
              }}
            >
              {tour.texte}
            </div>
          </div>
        ))}
      </div>

      {erreur && <p className="text-sm" style={{ color: '#B02631' }}>{erreur}</p>}
      {erreurMicro && <p className="text-sm" style={{ color: '#B02631' }}>{erreurMicro}</p>}

      {seanceTerminee ? (
        <div className="rounded-2xl border p-5 text-center" style={{ borderColor: '#BEE3CE', background: '#EAF7EF' }}>
          <p className="font-semibold" style={{ color: '#2E9E6B' }}>Toutes les notions de cette seance sont maitrisees.</p>
          <button
            onClick={() => navigate(`/seances/${etat.seanceId}`)}
            className="mt-3 rounded-lg px-4 py-2 text-sm font-semibold text-white"
            style={{ background: 'var(--color-brand)' }}
          >
            Retour a la seance
          </button>
        </div>
      ) : (
        <div className="flex items-center justify-between gap-4">
          <button
            onMouseDown={demarrer}
            onMouseUp={gererRelachement}
            onTouchStart={demarrer}
            onTouchEnd={gererRelachement}
            disabled={envoiEnCours}
            className="flex-1 rounded-2xl py-5 text-sm font-semibold text-white disabled:opacity-50"
            style={{ background: enregistrement ? 'var(--color-live)' : 'var(--color-brand)' }}
          >
            {envoiEnCours ? 'Le tuteur reflechit...' : enregistrement ? 'Relache pour envoyer...' : 'Maintiens pour parler'}
          </button>
          <button
            onClick={gererArret}
            className="rounded-2xl border px-4 py-5 text-sm font-semibold"
            style={{ borderColor: 'var(--color-border-soft)', color: 'var(--color-ink-muted)' }}
          >
            Terminer
          </button>
        </div>
      )}
    </div>
  )
}

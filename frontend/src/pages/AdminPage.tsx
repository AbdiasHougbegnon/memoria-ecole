import { useEffect, useState } from 'react'
import { effacerCompteAdmin, listerJournalRgpd } from '../api'
import type { JournalRgpdEntry } from '../types'

const LIBELLE_TYPE: Record<JournalRgpdEntry['type'], string> = {
  EFFACEMENT_COMPTE: 'Effacement de compte',
  EXPORT_DONNEES: 'Export de donnees',
  PURGE_RETENTION: 'Purge de retention',
}

export function AdminPage() {
  const [email, setEmail] = useState('')
  const [effacementEnCours, setEffacementEnCours] = useState(false)
  const [erreurEffacement, setErreurEffacement] = useState<string | null>(null)
  const [confirmationEffacement, setConfirmationEffacement] = useState<string | null>(null)

  const [journal, setJournal] = useState<JournalRgpdEntry[]>([])
  const [chargementJournal, setChargementJournal] = useState(true)
  const [erreurJournal, setErreurJournal] = useState<string | null>(null)

  async function rafraichirJournal() {
    setChargementJournal(true)
    try {
      setJournal(await listerJournalRgpd())
      setErreurJournal(null)
    } catch {
      setErreurJournal('Impossible de charger le journal RGPD.')
    } finally {
      setChargementJournal(false)
    }
  }

  useEffect(() => {
    void rafraichirJournal()
  }, [])

  async function gererEffacement(e: React.FormEvent) {
    e.preventDefault()
    if (!email.trim()) return
    if (
      !window.confirm(
        `Effacer definitivement le compte ${email.trim()} ? Ses sessions personnelles, transcriptions, resumes et son empreinte vocale seront effaces. Cette action est irreversible -- assure-toi que la demande a ete verifiee avant de continuer.`,
      )
    ) {
      return
    }
    setErreurEffacement(null)
    setConfirmationEffacement(null)
    setEffacementEnCours(true)
    try {
      await effacerCompteAdmin(email.trim())
      setConfirmationEffacement(`Compte ${email.trim()} efface.`)
      setEmail('')
      await rafraichirJournal()
    } catch {
      setErreurEffacement("Impossible d'effacer ce compte -- verifie que l'email est correct.")
    } finally {
      setEffacementEnCours(false)
    }
  }

  return (
    <div className="mx-auto max-w-[900px] px-8 py-10">
      <h1 className="text-[28px] font-bold tracking-tight">Administration</h1>
      <p className="mt-1.5 text-sm" style={{ color: 'var(--color-ink-muted)' }}>
        Reserve aux comptes administrateur. Effacement RGPD au nom d'un utilisateur qui en a fait la demande par un
        autre canal (email, support) -- verifie son identite avant de continuer, cette page ne le fait pas a ta place.
      </p>

      <div className="mt-7">
        <h2 className="mb-3 text-sm font-bold">Effacer un compte</h2>
        <div className="rounded-2xl border bg-white p-5" style={{ borderColor: 'var(--color-border-soft)' }}>
          <form onSubmit={gererEffacement} className="flex gap-2">
            <input
              type="email"
              placeholder="email@exemple.fr"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="flex-1 rounded-lg border px-3.5 py-2.5 text-sm outline-none"
              style={{ borderColor: 'var(--color-border-soft)', background: '#FCFBF9' }}
            />
            <button
              type="submit"
              disabled={effacementEnCours || !email.trim()}
              className="rounded-lg px-4 py-2.5 text-sm font-semibold disabled:opacity-50"
              style={{ border: '1px solid #F1D4D4', color: '#B02631' }}
            >
              {effacementEnCours ? 'Effacement...' : 'Effacer ce compte'}
            </button>
          </form>
          {erreurEffacement && <p className="mt-3 text-sm" style={{ color: '#B02631' }}>{erreurEffacement}</p>}
          {confirmationEffacement && <p className="mt-3 text-sm" style={{ color: 'var(--color-ok)' }}>{confirmationEffacement}</p>}
        </div>
      </div>

      <div className="mt-6">
        <h2 className="mb-3 text-sm font-bold">Journal RGPD</h2>
        {erreurJournal && <p className="mb-3 text-sm" style={{ color: '#B02631' }}>{erreurJournal}</p>}
        {chargementJournal && <p className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Chargement...</p>}
        {!chargementJournal && journal.length === 0 && (
          <p className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Aucune entree pour le moment.</p>
        )}
        {!chargementJournal && journal.length > 0 && (
          <div className="overflow-x-auto rounded-2xl border bg-white" style={{ borderColor: 'var(--color-border-soft)' }}>
            <table className="w-full text-left text-[13px]">
              <thead>
                <tr style={{ borderBottom: '1px solid var(--color-border-softer)' }}>
                  <th className="px-4 py-2.5 font-semibold" style={{ color: 'var(--color-ink-faint-2)' }}>Type</th>
                  <th className="px-4 py-2.5 font-semibold" style={{ color: 'var(--color-ink-faint-2)' }}>Cible</th>
                  <th className="px-4 py-2.5 font-semibold" style={{ color: 'var(--color-ink-faint-2)' }}>Initiateur</th>
                  <th className="px-4 py-2.5 font-semibold" style={{ color: 'var(--color-ink-faint-2)' }}>Date</th>
                  <th className="px-4 py-2.5 font-semibold" style={{ color: 'var(--color-ink-faint-2)' }}>Details</th>
                </tr>
              </thead>
              <tbody>
                {journal.map((entree) => (
                  <tr key={entree.id} style={{ borderBottom: '1px solid var(--color-border-softer)' }}>
                    <td className="px-4 py-2.5">{LIBELLE_TYPE[entree.type]}</td>
                    <td className="px-4 py-2.5" style={{ fontFamily: 'var(--font-mono)', color: 'var(--color-ink-muted)' }}>
                      {entree.utilisateurCibleId ?? '—'}
                    </td>
                    <td className="px-4 py-2.5" style={{ fontFamily: 'var(--font-mono)', color: 'var(--color-ink-muted)' }}>
                      {entree.initiateurId ?? 'self-service'}
                    </td>
                    <td className="px-4 py-2.5" style={{ color: 'var(--color-ink-muted)' }}>
                      {new Date(entree.dateAction).toLocaleString('fr-FR')}
                    </td>
                    <td className="px-4 py-2.5" style={{ color: 'var(--color-ink-muted)' }}>{entree.details}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

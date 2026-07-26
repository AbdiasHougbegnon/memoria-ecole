import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { importerMatieres } from '../api'
import type { RapportImportMatieres } from '../types'

export function ImporterMatieresPage() {
  const [enCours, setEnCours] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)
  const [rapport, setRapport] = useState<RapportImportMatieres | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  async function importer(e: React.ChangeEvent<HTMLInputElement>) {
    const fichier = e.target.files?.[0]
    if (!fichier) return
    setErreur(null)
    setRapport(null)
    setEnCours(true)
    try {
      setRapport(await importerMatieres(fichier))
    } catch {
      setErreur("Impossible d'importer ce fichier.")
    } finally {
      setEnCours(false)
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  return (
    <div className="mx-auto max-w-[760px] px-8 py-10">
      <Link to="/couloirs" className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>&larr; Couloirs</Link>
      <h1 className="mt-3 text-[28px] font-bold tracking-tight">Importer les classes de l'ecole</h1>
      <p className="mt-1.5 max-w-lg text-sm" style={{ color: 'var(--color-ink-muted)' }}>
        Envoie un fichier CSV listant les classes de l'ecole (annee academique, filiere,
        specialite) avec les matieres de chacune : les couloirs et matieres correspondants sont
        crees automatiquement. Les etudiants retrouvent ensuite directement ces matieres au
        moment de lancer une session ou une seance de tutorat, sans avoir a les saisir eux-memes.
        Relancer le meme fichier ne duplique rien, ne complete que ce qui manque.
      </p>

      <div
        className="mt-6 rounded-2xl border p-5 text-sm"
        style={{ borderColor: 'var(--color-border-soft)', background: '#FCFBF9' }}
      >
        <p className="font-semibold">Format attendu (en-tete obligatoire) :</p>
        <pre className="mt-2 overflow-x-auto rounded-lg bg-white p-3 text-xs" style={{ border: '1px solid var(--color-border-softer)' }}>
{`annee_academique,filiere,specialite,nom_matiere
2026-2027,Informatique,Genie Logiciel,Algorithmique
2026-2027,Informatique,Genie Logiciel,Bases de donnees`}
        </pre>
        <p className="mt-2 text-xs" style={{ color: 'var(--color-ink-faint)' }}>La colonne specialite peut rester vide.</p>
      </div>

      <div className="mt-6 flex items-center gap-3">
        <input ref={inputRef} type="file" accept=".csv,text/csv" onChange={(e) => void importer(e)} disabled={enCours} className="text-sm" />
        {enCours && <span className="text-sm" style={{ color: 'var(--color-ink-muted)' }}>Import en cours...</span>}
      </div>
      {erreur && <p className="mt-3 text-sm" style={{ color: '#B02631' }}>{erreur}</p>}

      {rapport && (
        <div className="mt-7 rounded-2xl border bg-white p-5" style={{ borderColor: 'var(--color-border-soft)' }}>
          <h2 className="text-base font-bold">Resultat</h2>
          <div className="mt-3 grid grid-cols-4 gap-3 text-center">
            <div>
              <div className="text-xl font-bold" style={{ fontFamily: 'var(--font-mono)' }}>{rapport.couloirsCrees}</div>
              <div className="text-[11px]" style={{ color: 'var(--color-ink-faint)' }}>couloir(s) cree(s)</div>
            </div>
            <div>
              <div className="text-xl font-bold" style={{ fontFamily: 'var(--font-mono)' }}>{rapport.couloirsExistants}</div>
              <div className="text-[11px]" style={{ color: 'var(--color-ink-faint)' }}>couloir(s) existant(s)</div>
            </div>
            <div>
              <div className="text-xl font-bold" style={{ fontFamily: 'var(--font-mono)' }}>{rapport.matieresCreees}</div>
              <div className="text-[11px]" style={{ color: 'var(--color-ink-faint)' }}>matiere(s) creee(s)</div>
            </div>
            <div>
              <div className="text-xl font-bold" style={{ fontFamily: 'var(--font-mono)' }}>{rapport.matieresExistantes}</div>
              <div className="text-[11px]" style={{ color: 'var(--color-ink-faint)' }}>matiere(s) deja presente(s)</div>
            </div>
          </div>

          {rapport.erreurs.length > 0 && (
            <div className="mt-5 border-t pt-4" style={{ borderColor: 'var(--color-border-softer)' }}>
              <p className="text-sm font-semibold" style={{ color: '#B02631' }}>
                {rapport.erreurs.length} ligne(s) ignoree(s)
              </p>
              <ul className="mt-2 space-y-1 text-xs" style={{ color: 'var(--color-ink-muted)' }}>
                {rapport.erreurs.map((err) => (
                  <li key={err.numeroLigne}>Ligne {err.numeroLigne} : {err.message}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

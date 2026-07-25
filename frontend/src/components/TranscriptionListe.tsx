import type { TranscriptionSegment } from '../types'

interface TranscriptionListeProps {
  segments: TranscriptionSegment[]
  // Drill-down resume/compte-rendu -> transcription -> audio (voir
  // docs/phases/phase-14-drilldown-source-audio.md) : optionnels, pour que
  // la vue en direct pendant l'enregistrement (Recorder.tsx) reste simple
  // sans avoir a cabler surbrillance/audio.
  segmentsEnSurbrillance?: Set<number>
  onRefSegment?: (numeroSequence: number, el: HTMLDivElement | null) => void
  onEcouterSegment?: (numeroSequence: number) => void
}

export function TranscriptionListe({ segments, segmentsEnSurbrillance, onRefSegment, onEcouterSegment }: TranscriptionListeProps) {
  return (
    <div className="rounded-2xl border bg-white" style={{ borderColor: 'var(--color-border-soft)' }}>
      {segments.map((segment, index) => (
        <div
          key={segment.numeroSequence}
          ref={onRefSegment ? (el) => onRefSegment(segment.numeroSequence, el) : undefined}
          className="p-3.5 text-sm transition-colors"
          style={{
            ...(index > 0 ? { borderTop: '1px solid var(--color-border-softer)' } : undefined),
            ...(segmentsEnSurbrillance?.has(segment.numeroSequence) ? { background: 'var(--color-brand-wash)' } : undefined),
          }}
        >
          <span className="mr-2 text-xs" style={{ fontFamily: 'var(--font-mono)', color: 'var(--color-ink-faint)' }}>
            #{segment.numeroSequence}
          </span>
          {onEcouterSegment && segment.statut === 'REUSSIE' && (
            <button
              onClick={() => onEcouterSegment(segment.numeroSequence)}
              className="mr-2 text-xs"
              style={{ color: 'var(--color-ink-faint)' }}
              aria-label="Ecouter ce passage"
              title="Ecouter ce passage"
            >
              &#128266;
            </button>
          )}
          {segment.statut !== 'REUSSIE' ? (
            <span className="italic" style={{ color: '#B02631' }}>Echec de la transcription</span>
          ) : segment.segmentsLocuteur.length > 0 ? (
            <div className="mt-1 flex flex-col gap-1">
              {segment.segmentsLocuteur.map((locuteur, i) => (
                <p key={i}>
                  <span className="mr-2 font-medium" style={{ color: 'var(--color-ink-muted)' }}>
                    Intervenant {locuteur.locuteur}
                    {locuteur.nomUtilisateurIdentifie && ` (${locuteur.nomUtilisateurIdentifie})`}
                  </span>
                  <span>{locuteur.texte}</span>
                </p>
              ))}
            </div>
          ) : (
            <span>{segment.texte}</span>
          )}
        </div>
      ))}
    </div>
  )
}

import type { TranscriptionSegment } from '../types'

export function TranscriptionListe({ segments }: { segments: TranscriptionSegment[] }) {
  return (
    <div className="rounded-2xl border bg-white" style={{ borderColor: 'var(--color-border-soft)' }}>
      {segments.map((segment, index) => (
        <div
          key={segment.numeroSequence}
          className="p-3.5 text-sm"
          style={index > 0 ? { borderTop: '1px solid var(--color-border-softer)' } : undefined}
        >
          <span className="mr-2 text-xs" style={{ fontFamily: 'var(--font-mono)', color: 'var(--color-ink-faint)' }}>
            #{segment.numeroSequence}
          </span>
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

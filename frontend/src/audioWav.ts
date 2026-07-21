// Conversion d'un Blob audio (webm/opus, tel que produit par MediaRecorder)
// vers un WAV PCM 16 bits canonique (entete 44 octets). Partage entre
// Recorder.tsx (chunks de session) et ParametresCompteePage.tsx (enrolement
// vocal) -- meme format attendu cote backend (ExtracteurAudioLocuteur).
export async function convertirBlobEnWav(audio: Blob): Promise<Blob> {
  const buffer = await audio.arrayBuffer()
  const AudioContextCtor = window.AudioContext || (window as Window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext

  if (!AudioContextCtor) {
    throw new Error('AudioContext indisponible')
  }

  const contexte = new AudioContextCtor()
  try {
    const audioBuffer = await contexte.decodeAudioData(buffer.slice(0))
    const canaux = audioBuffer.numberOfChannels
    const longueur = audioBuffer.length * canaux
    const tampon = new ArrayBuffer(44 + longueur * 2)
    const vue = new DataView(tampon)

    const ecrireChaine = (offset: number, valeur: string) => {
      for (let i = 0; i < valeur.length; i += 1) {
        vue.setUint8(offset + i, valeur.charCodeAt(i))
      }
    }

    ecrireChaine(0, 'RIFF')
    vue.setUint32(4, 36 + longueur * 2, true)
    ecrireChaine(8, 'WAVE')
    ecrireChaine(12, 'fmt ')
    vue.setUint32(16, 16, true)
    vue.setUint16(20, 1, true)
    vue.setUint16(22, canaux, true)
    vue.setUint32(24, audioBuffer.sampleRate, true)
    vue.setUint32(28, audioBuffer.sampleRate * canaux * 2, true)
    vue.setUint16(32, canaux * 2, true)
    vue.setUint16(34, 16, true)
    ecrireChaine(36, 'data')
    vue.setUint32(40, longueur * 2, true)

    let offset = 44
    for (let i = 0; i < audioBuffer.length; i += 1) {
      for (let canal = 0; canal < canaux; canal += 1) {
        const sample = Math.max(-1, Math.min(1, audioBuffer.getChannelData(canal)[i]))
        vue.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
        offset += 2
      }
    }

    return new Blob([tampon], { type: 'audio/wav' })
  } finally {
    await contexte.close()
  }
}

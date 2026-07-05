// Azure Document Intelligence (niveau gratuit) rejette les images de plus de
// quelques Mo, et une vraie photo de telephone moderne les depasse souvent.
// On redimensionne/compresse cote navigateur avant l'envoi plutot que de
// rejeter l'upload.
const TAILLE_MAX_OCTETS = 3_500_000
const DIMENSION_MAX_PIXELS = 2000
const QUALITE_JPEG = 0.8

export async function redimensionnerImageSiNecessaire(fichier: File): Promise<File> {
  if (!fichier.type.startsWith('image/') || fichier.size <= TAILLE_MAX_OCTETS) {
    return fichier
  }

  const bitmap = await createImageBitmap(fichier)
  const ratio = Math.min(1, DIMENSION_MAX_PIXELS / Math.max(bitmap.width, bitmap.height))
  const largeur = Math.round(bitmap.width * ratio)
  const hauteur = Math.round(bitmap.height * ratio)

  const canvas = document.createElement('canvas')
  canvas.width = largeur
  canvas.height = hauteur
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, largeur, hauteur)
  bitmap.close()

  const blob = await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (b) => (b ? resolve(b) : reject(new Error('Echec de la compression de l’image'))),
      'image/jpeg',
      QUALITE_JPEG,
    )
  })

  const nomRedimensionne = fichier.name.replace(/\.\w+$/, '') + '.jpg'
  return new File([blob], nomRedimensionne, { type: 'image/jpeg' })
}

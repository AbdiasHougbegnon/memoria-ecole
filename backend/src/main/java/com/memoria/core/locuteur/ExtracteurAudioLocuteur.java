package com.memoria.core.locuteur;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import com.memoria.core.transcription.SegmentLocuteur;

// Utilitaire statique pur (pas de Spring, testable sans mock) : decoupe le
// WAV d'un chunk audio pour n'en garder que les tranches correspondant a un
// locuteur donne, et reconstruit un WAV autonome a partir de ces tranches.
// Suppose le format canonique 44 octets / PCM 16 bits produit par
// convertirBlobEnWav (frontend/src/components/Recorder.tsx) -- c'est le
// seul format que ce projet produit aujourd'hui, pas de support generique
// d'autres formats WAV.
public final class ExtracteurAudioLocuteur {

    private static final int TAILLE_ENTETE = 44;

    private ExtracteurAudioLocuteur() {
    }

    public static byte[] extraire(byte[] wavChunk, List<SegmentLocuteur> segmentsDuLocuteur) {
        ByteBuffer entete = ByteBuffer.wrap(wavChunk, 0, TAILLE_ENTETE).order(ByteOrder.LITTLE_ENDIAN);
        int nombreCanaux = entete.getShort(22) & 0xFFFF;
        int tauxEchantillonnage = entete.getInt(24);
        int alignementBloc = entete.getShort(32) & 0xFFFF;
        int longueurDonnees = wavChunk.length - TAILLE_ENTETE;

        ByteArrayOutputStream tranches = new ByteArrayOutputStream();
        for (SegmentLocuteur segment : segmentsDuLocuteur) {
            long octetDebut = TAILLE_ENTETE + alignerSurBloc(
                    millisecondesVersOctets(segment.getOffsetMillisecondes(), tauxEchantillonnage, alignementBloc), alignementBloc);
            long octetFin = octetDebut + alignerSurBloc(
                    millisecondesVersOctets(segment.getDureeMillisecondes(), tauxEchantillonnage, alignementBloc), alignementBloc);

            long debutClampe = Math.max(TAILLE_ENTETE, Math.min(octetDebut, (long) wavChunk.length));
            long finClampe = Math.max(debutClampe, Math.min(octetFin, (long) wavChunk.length));
            if (finClampe > debutClampe) {
                tranches.write(wavChunk, (int) debutClampe, (int) (finClampe - debutClampe));
            }
        }

        return construireWav(tranches.toByteArray(), nombreCanaux, tauxEchantillonnage, alignementBloc);
    }

    private static long millisecondesVersOctets(long millisecondes, int tauxEchantillonnage, int alignementBloc) {
        return (long) (millisecondes / 1000.0 * tauxEchantillonnage) * alignementBloc;
    }

    private static long alignerSurBloc(long octets, int alignementBloc) {
        return (octets / alignementBloc) * alignementBloc;
    }

    private static byte[] construireWav(byte[] donnees, int nombreCanaux, int tauxEchantillonnage, int alignementBloc) {
        int bitsParEchantillon = (alignementBloc / Math.max(nombreCanaux, 1)) * 8;
        ByteBuffer tampon = ByteBuffer.allocate(TAILLE_ENTETE + donnees.length).order(ByteOrder.LITTLE_ENDIAN);

        ecrireChaine(tampon, "RIFF");
        tampon.putInt(tampon.capacity() - 8);
        ecrireChaine(tampon, "WAVE");
        ecrireChaine(tampon, "fmt ");
        tampon.putInt(16);
        tampon.putShort((short) 1);
        tampon.putShort((short) nombreCanaux);
        tampon.putInt(tauxEchantillonnage);
        tampon.putInt(tauxEchantillonnage * alignementBloc);
        tampon.putShort((short) alignementBloc);
        tampon.putShort((short) bitsParEchantillon);
        ecrireChaine(tampon, "data");
        tampon.putInt(donnees.length);
        tampon.put(donnees);

        return tampon.array();
    }

    private static void ecrireChaine(ByteBuffer tampon, String valeur) {
        for (int i = 0; i < valeur.length(); i++) {
            tampon.put((byte) valeur.charAt(i));
        }
    }
}

package com.memoria.core.filmemoire;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// Conversion float[] <-> byte[] (4 octets par composante, little-endian) pour
// stocker un embedding dans une colonne binaire, et similarite cosinus pour
// comparer deux vecteurs -- le nombre de fils reste petit (dizaines, pas
// millions), une comparaison directe en Java suffit, pas besoin d'un index
// vectoriel dedie pour ca.
final class VecteurUtils {

    private VecteurUtils() {
    }

    static byte[] versOctets(float[] vecteur) {
        ByteBuffer tampon = ByteBuffer.allocate(vecteur.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float composante : vecteur) {
            tampon.putFloat(composante);
        }
        return tampon.array();
    }

    static float[] depuisOctets(byte[] octets) {
        ByteBuffer tampon = ByteBuffer.wrap(octets).order(ByteOrder.LITTLE_ENDIAN);
        float[] vecteur = new float[octets.length / Float.BYTES];
        for (int i = 0; i < vecteur.length; i++) {
            vecteur[i] = tampon.getFloat();
        }
        return vecteur;
    }

    static double similariteCosinus(float[] a, float[] b) {
        double produitScalaire = 0;
        double normeA = 0;
        double normeB = 0;
        for (int i = 0; i < a.length; i++) {
            produitScalaire += a[i] * b[i];
            normeA += a[i] * a[i];
            normeB += b[i] * b[i];
        }
        if (normeA == 0 || normeB == 0) {
            return 0;
        }
        return produitScalaire / (Math.sqrt(normeA) * Math.sqrt(normeB));
    }
}

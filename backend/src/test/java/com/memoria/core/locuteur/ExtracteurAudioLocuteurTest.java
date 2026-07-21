package com.memoria.core.locuteur;

import com.memoria.core.transcription.SegmentLocuteur;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtracteurAudioLocuteurTest {

    // 8000 Hz, mono, 16 bits : 1000ms de chunk = 8000 echantillons, chacun
    // rempli avec sa propre valeur d'index pour verifier precisement quelle
    // tranche est extraite.
    private static final int TAUX_ECHANTILLONNAGE = 8000;

    private byte[] construireWavSynthetique(int nombreEchantillons) {
        ByteBuffer tampon = ByteBuffer.allocate(44 + nombreEchantillons * 2).order(ByteOrder.LITTLE_ENDIAN);
        ecrireChaine(tampon, "RIFF");
        tampon.putInt(tampon.capacity() - 8);
        ecrireChaine(tampon, "WAVE");
        ecrireChaine(tampon, "fmt ");
        tampon.putInt(16);
        tampon.putShort((short) 1);
        tampon.putShort((short) 1); // mono
        tampon.putInt(TAUX_ECHANTILLONNAGE);
        tampon.putInt(TAUX_ECHANTILLONNAGE * 2);
        tampon.putShort((short) 2); // block align
        tampon.putShort((short) 16);
        ecrireChaine(tampon, "data");
        tampon.putInt(nombreEchantillons * 2);
        for (int i = 0; i < nombreEchantillons; i++) {
            tampon.putShort((short) i);
        }
        return tampon.array();
    }

    private void ecrireChaine(ByteBuffer tampon, String valeur) {
        for (int i = 0; i < valeur.length(); i++) {
            tampon.put((byte) valeur.charAt(i));
        }
    }

    private short[] lireEchantillons(byte[] wav) {
        ByteBuffer donnees = ByteBuffer.wrap(wav, 44, wav.length - 44).order(ByteOrder.LITTLE_ENDIAN);
        short[] echantillons = new short[donnees.remaining() / 2];
        for (int i = 0; i < echantillons.length; i++) {
            echantillons[i] = donnees.getShort();
        }
        return echantillons;
    }

    @Test
    void extraire_isole_la_tranche_correspondant_au_segment() {
        byte[] wav = construireWavSynthetique(8000); // 1000ms
        SegmentLocuteur segment = new SegmentLocuteur(1, "texte", 0, 500); // 0-500ms -> echantillons 0..3999

        byte[] resultat = ExtracteurAudioLocuteur.extraire(wav, List.of(segment));

        short[] echantillons = lireEchantillons(resultat);
        assertThat(echantillons).hasSize(4000);
        assertThat(echantillons[0]).isEqualTo((short) 0);
        assertThat(echantillons[3999]).isEqualTo((short) 3999);
    }

    @Test
    void extraire_concatene_plusieurs_segments_dans_lordre() {
        byte[] wav = construireWavSynthetique(8000);
        SegmentLocuteur premier = new SegmentLocuteur(1, "a", 0, 250);   // echantillons 0..1999
        SegmentLocuteur second = new SegmentLocuteur(1, "b", 500, 250); // echantillons 4000..5999

        byte[] resultat = ExtracteurAudioLocuteur.extraire(wav, List.of(premier, second));

        short[] echantillons = lireEchantillons(resultat);
        assertThat(echantillons).hasSize(2000 + 2000);
        assertThat(echantillons[0]).isEqualTo((short) 0);
        assertThat(echantillons[1999]).isEqualTo((short) 1999);
        assertThat(echantillons[2000]).isEqualTo((short) 4000);
        assertThat(echantillons[3999]).isEqualTo((short) 5999);
    }

    @Test
    void extraire_clampe_un_segment_qui_depasse_la_fin_du_chunk() {
        byte[] wav = construireWavSynthetique(8000); // 1000ms
        SegmentLocuteur segment = new SegmentLocuteur(1, "texte", 900, 500); // deborde largement au-dela de 1000ms

        byte[] resultat = ExtracteurAudioLocuteur.extraire(wav, List.of(segment));

        short[] echantillons = lireEchantillons(resultat);
        assertThat(echantillons.length).isLessThanOrEqualTo(800); // ne depasse pas ce qui existe reellement (100ms restantes)
        assertThat(echantillons[echantillons.length - 1]).isEqualTo((short) 7999);
    }

    @Test
    void extraire_produit_un_entete_wav_coherent() {
        byte[] wav = construireWavSynthetique(8000);
        SegmentLocuteur segment = new SegmentLocuteur(1, "texte", 0, 100);

        byte[] resultat = ExtracteurAudioLocuteur.extraire(wav, List.of(segment));

        ByteBuffer entete = ByteBuffer.wrap(resultat, 0, 44).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(entete.getShort(22)).isEqualTo((short) 1); // mono, comme la source
        assertThat(entete.getInt(24)).isEqualTo(TAUX_ECHANTILLONNAGE);
        assertThat(entete.getInt(40)).isEqualTo(resultat.length - 44);
    }
}

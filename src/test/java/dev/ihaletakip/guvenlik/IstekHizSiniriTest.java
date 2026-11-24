package dev.ihaletakip.guvenlik;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class IstekHizSiniriTest {
    /** 100. dakikanın başı: pencere sınırında başlar, bekleme süreleri kesin hesaplanır. */
    private final AtomicLong saat = new AtomicLong(100 * 60_000L);

    private IstekHizSiniri sinirlayici(int giris, int yazma, int okuma) {
        return new IstekHizSiniri(true, false, giris, yazma, okuma, saat::get);
    }

    @Test
    void sinirAsilincaIstekReddedilirVeBeklemeSuresiPencereyiGecmez() {
        var s = sinirlayici(3, 100, 100);
        for (int i = 0; i < 3; i++) {
            assertThat(s.kontrolEt("10.0.0.1", IstekHizSiniri.Kova.GIRIS).izinli()).isTrue();
        }
        var red = s.kontrolEt("10.0.0.1", IstekHizSiniri.Kova.GIRIS);
        assertThat(red.izinli()).isFalse();
        assertThat(red.beklemeSaniye()).isEqualTo(60L);
    }

    @Test
    void yeniPenceredeSayacSifirlanir() {
        var s = sinirlayici(1, 100, 100);
        assertThat(s.kontrolEt("10.0.0.1", IstekHizSiniri.Kova.GIRIS).izinli()).isTrue();
        assertThat(s.kontrolEt("10.0.0.1", IstekHizSiniri.Kova.GIRIS).izinli()).isFalse();
        saat.addAndGet(60_000);
        assertThat(s.kontrolEt("10.0.0.1", IstekHizSiniri.Kova.GIRIS).izinli()).isTrue();
    }

    @Test
    void istemcilerVeKovalarBirbirindenBagimsizdir() {
        var s = sinirlayici(1, 1, 1);
        assertThat(s.kontrolEt("10.0.0.1", IstekHizSiniri.Kova.GIRIS).izinli()).isTrue();
        assertThat(s.kontrolEt("10.0.0.1", IstekHizSiniri.Kova.GIRIS).izinli()).isFalse();
        // aynı istemci, başka kova
        assertThat(s.kontrolEt("10.0.0.1", IstekHizSiniri.Kova.OKUMA).izinli()).isTrue();
        assertThat(s.kontrolEt("10.0.0.1", IstekHizSiniri.Kova.YAZMA).izinli()).isTrue();
        // başka istemci, aynı kova
        assertThat(s.kontrolEt("10.0.0.2", IstekHizSiniri.Kova.GIRIS).izinli()).isTrue();
    }

    @Test
    void beklemeSuresiPencereSonundaBirSaniyeyeIner() {
        var s = sinirlayici(1, 100, 100);
        saat.set(5 * 60_000L + 59_500L); // pencerenin bitmesine 0,5 sn
        s.kontrolEt("10.0.0.1", IstekHizSiniri.Kova.GIRIS);
        assertThat(s.kontrolEt("10.0.0.1", IstekHizSiniri.Kova.GIRIS).beklemeSaniye()).isEqualTo(1L);
    }
}

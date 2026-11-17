package dev.ihaletakip;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ihaletakip.domain.Teklif;
import dev.ihaletakip.kural.IhaleKurallari;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class IhaleKurallariTest {
    private static final BigDecimal S1 = new BigDecimal("500000");
    private static final BigDecimal S2 = new BigDecimal("5000000");
    private static final BigDecimal ORAN = new BigDecimal("0.70");
    private static final BigDecimal YM = new BigDecimal("1000000");

    private static BigDecimal tl(String v) {
        return new BigDecimal(v);
    }

    private static Teklif teklif(String tutar) {
        return new Teklif(null, "Firma", "1234567890", tl(tutar), LocalDateTime.now());
    }

    @Test
    void onayKademesiSinirlardaDogruHesaplanir() {
        assertThat(IhaleKurallari.onayKademesi(tl("500000"), S1, S2)).isEqualTo(1); // sınır dahil
        assertThat(IhaleKurallari.onayKademesi(tl("500000.01"), S1, S2)).isEqualTo(2);
        assertThat(IhaleKurallari.onayKademesi(tl("5000000"), S1, S2)).isEqualTo(2);
        assertThat(IhaleKurallari.onayKademesi(tl("5000000.01"), S1, S2)).isEqualTo(3);
    }

    @Test
    void asiriDusukSinirdaDegildirAltindadir() {
        assertThat(IhaleKurallari.asiriDusukMu(tl("700000"), YM, ORAN)).isFalse(); // tam sınır
        assertThat(IhaleKurallari.asiriDusukMu(tl("699999.99"), YM, ORAN)).isTrue();
    }

    @Test
    void asiriDusukTeklifAciklamasiKabulEdilmedenUygunDegildir() {
        Teklif t = teklif("600000");
        assertThat(IhaleKurallari.uygunMu(t, YM, ORAN)).isFalse();
        t.asiriDusukAciklamaKarari("KABUL");
        assertThat(IhaleKurallari.uygunMu(t, YM, ORAN)).isTrue();
    }

    @Test
    void reddedilenTeklifUygunDegildir() {
        Teklif t = teklif("900000");
        t.reddet("Şartname dışı");
        assertThat(IhaleKurallari.uygunMu(t, YM, ORAN)).isFalse();
    }

    @Test
    void enDusukUygunTeklifAsiriDusukVeReddedilenleriAtlar() {
        Teklif asiriDusuk = teklif("500000"); // aşırı düşük, açıklama yok
        Teklif reddedilen = teklif("750000");
        reddedilen.reddet("Eksik belge");
        Teklif uygun1 = teklif("800000");
        Teklif uygun2 = teklif("950000");

        var sonuc = IhaleKurallari.enDusukUygunTeklif(List.of(asiriDusuk, reddedilen, uygun2, uygun1), YM, ORAN);

        assertThat(sonuc).containsSame(uygun1);
    }

    @Test
    void uygunTeklifYoksaBosDoner() {
        assertThat(IhaleKurallari.enDusukUygunTeklif(List.of(teklif("100000")), YM, ORAN)).isEmpty();
    }
}

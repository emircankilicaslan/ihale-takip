package dev.ihaletakip;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ihaletakip.domain.IhaleDurumu;
import dev.ihaletakip.domain.Kullanici;
import dev.ihaletakip.domain.Rol;
import dev.ihaletakip.kural.IhaleKurallari;
import dev.ihaletakip.kural.IsGunuTakvimi;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Veritabanı gerektirmeyen alan kuralları: sınır değerleri, roller, durumlar, hesap kilidi, takvim. */
class AlanKurallariTest {

    // -------------------------------------------------------------- onay kademesi sınırları

    @ParameterizedTest(name = "{0} TL -> {1}. kademe")
    @CsvSource({
            "0.01,1", "499999.99,1", "500000,1", "500000.01,2", "4999999.99,2", "5000000,2", "5000000.01,3", "99999999,3"
    })
    void onayKademesiSinirlardaDogruHesaplanir(BigDecimal tutar, int beklenen) {
        assertThat(IhaleKurallari.onayKademesi(tutar, new BigDecimal("500000"), new BigDecimal("5000000")))
                .isEqualTo(beklenen);
    }

    @Test
    void onayKademesiEsikleriAyarlanabilir() {
        assertThat(IhaleKurallari.onayKademesi(new BigDecimal("1000"), new BigDecimal("500"), new BigDecimal("900")))
                .isEqualTo(3);
        assertThat(IhaleKurallari.onayKademesi(new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("2000")))
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------ aşırı düşük teklif

    @ParameterizedTest(name = "{0} / {1} x {2} -> aşırı düşük: {3}")
    @CsvSource({
            "700000,1000000,0.70,false", "699999.99,1000000,0.70,true", "1,1000000,0.70,true",
            "1000000,1000000,0.70,false", "850000,1000000,0.85,false", "849999.99,1000000,0.85,true"
    })
    void asiriDusukSinirDegerleri(BigDecimal tutar, BigDecimal yaklasikMaliyet, BigDecimal oran, boolean beklenen) {
        assertThat(IhaleKurallari.asiriDusukMu(tutar, yaklasikMaliyet, oran)).isEqualTo(beklenen);
    }

    // ------------------------------------------------------------------------------ roller

    @ParameterizedTest
    @CsvSource({
            "BIRIM_SORUMLUSU,0", "SATIN_ALMA_UZMANI,0", "KOMISYON_UYESI,0", "BIRIM_MUDURU,1",
            "DAIRE_BASKANI,2", "UST_YONETICI,3", "DENETCI,0", "ADMIN,0"
    })
    void onaySeviyesiRolleEslesir(Rol rol, int seviye) {
        assertThat(rol.onaySeviyesi()).isEqualTo(seviye);
    }

    @ParameterizedTest
    @EnumSource(Rol.class)
    void yalnizcaBirimSorumlusuVeMuduruBirimleSinirlidir(Rol rol) {
        boolean beklenen = rol == Rol.BIRIM_SORUMLUSU || rol == Rol.BIRIM_MUDURU;
        assertThat(rol.birimleSinirli()).isEqualTo(beklenen);
    }

    @Test
    void onayYetkisiOlanRollerYukseldikceYetkiArtar() {
        assertThat(Rol.BIRIM_MUDURU.onaySeviyesi()).isLessThan(Rol.DAIRE_BASKANI.onaySeviyesi());
        assertThat(Rol.DAIRE_BASKANI.onaySeviyesi()).isLessThan(Rol.UST_YONETICI.onaySeviyesi());
    }

    // ----------------------------------------------------------------------------- durumlar

    @ParameterizedTest
    @EnumSource(IhaleDurumu.class)
    void yalnizcaSozlesmeVeIptalSonDurumdur(IhaleDurumu durum) {
        boolean beklenen = durum == IhaleDurumu.SOZLESME_IMZALANDI || durum == IhaleDurumu.IPTAL;
        assertThat(durum.sonDurum()).isEqualTo(beklenen);
    }

    // ------------------------------------------------------------------------ hesap kilidi

    private static final LocalDateTime SIMDI = LocalDateTime.of(2026, 3, 2, 10, 0);

    private static Kullanici kullanici() {
        return new Kullanici("deneme", "Deneme", "hash", Rol.ADMIN, null);
    }

    @Test
    void ilkHataliGirisHesabiKilitlemez() {
        Kullanici k = kullanici();
        k.basarisizGirisKaydet(SIMDI, 5, 15);
        assertThat(k.getBasarisizGiris()).isEqualTo(1);
        assertThat(k.kilitliMi(SIMDI)).isFalse();
    }

    @Test
    void esigeUlasinCaKilitlenirVeSayacSifirlanir() {
        Kullanici k = kullanici();
        for (int i = 0; i < 5; i++) {
            k.basarisizGirisKaydet(SIMDI, 5, 15);
        }
        assertThat(k.kilitliMi(SIMDI)).isTrue();
        assertThat(k.getKilitBitis()).isEqualTo(SIMDI.plusMinutes(15));
        assertThat(k.getBasarisizGiris()).isZero();
    }

    @Test
    void kilitSuresiBitinceHesapAcilir() {
        Kullanici k = kullanici();
        for (int i = 0; i < 5; i++) {
            k.basarisizGirisKaydet(SIMDI, 5, 15);
        }
        assertThat(k.kilitliMi(SIMDI.plusMinutes(14))).isTrue();
        assertThat(k.kilitliMi(SIMDI.plusMinutes(15))).isFalse(); // bitiş anı dahil açık
        assertThat(k.kilitliMi(SIMDI.plusHours(2))).isFalse();
    }

    @Test
    void basariliGirisSayaciVeKilidiTemizler() {
        Kullanici k = kullanici();
        for (int i = 0; i < 5; i++) {
            k.basarisizGirisKaydet(SIMDI, 5, 15);
        }
        k.basariliGirisKaydet();
        assertThat(k.kilitliMi(SIMDI)).isFalse();
        assertThat(k.getBasarisizGiris()).isZero();
        assertThat(k.getKilitBitis()).isNull();
    }

    @Test
    void esikAyarlanabilir() {
        Kullanici k = kullanici();
        k.basarisizGirisKaydet(SIMDI, 2, 30);
        k.basarisizGirisKaydet(SIMDI, 2, 30);
        assertThat(k.getKilitBitis()).isEqualTo(SIMDI.plusMinutes(30));
    }

    // -------------------------------------------------------------------------- takvim

    private final IsGunuTakvimi takvim = new IsGunuTakvimi(List.of(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 5)));

    @ParameterizedTest(name = "{0} + {1} gün -> {2}")
    @CsvSource({
            "2026-03-02,0,2026-03-02", "2026-03-02,1,2026-03-03", "2026-03-02,2,2026-03-06", "2026-03-02,3,2026-03-06",
            "2026-03-02,5,2026-03-09", "2026-03-02,6,2026-03-09", "2026-03-02,7,2026-03-09", "2026-03-06,1,2026-03-09",
            "2026-03-02,15,2026-03-17"
    })
    void sureSonuIsGunuOlmayanGunlerdeIleriKayar(LocalDate baslangic, int gun, LocalDate beklenen) {
        assertThat(takvim.sureSonu(baslangic, gun)).isEqualTo(beklenen);
    }

    @Test
    void tatilListesindeTekrarEdenTarihHataVermez() {
        var t = new IsGunuTakvimi(List.of(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 4)));
        assertThat(t.isGunu(LocalDate.of(2026, 3, 4))).isFalse();
    }

    @Test
    void tatilYoksaYalnizcaHaftaSonlariAtlanir() {
        var bos = new IsGunuTakvimi(List.of());
        assertThat(bos.isGunu(LocalDate.of(2026, 3, 4))).isTrue();
        assertThat(bos.isGunu(LocalDate.of(2026, 3, 7))).isFalse();
        assertThat(bos.ilkIsGunu(LocalDate.of(2026, 3, 7))).isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    void enErkenIhaleTarihiAsgariIlanSuresiniVeTatilleriDikkateAlir() {
        // 2 Mart + 2 gün = 4 Mart (tatil) -> 5 Mart (tatil) -> 6 Mart Cuma
        assertThat(takvim.enErkenIhaleTarihi(LocalDate.of(2026, 3, 2), 2)).isEqualTo(LocalDate.of(2026, 3, 6));
    }

    @Test
    void yilSinirindaDogruCalisir() {
        var t = new IsGunuTakvimi(List.of(LocalDate.of(2027, 1, 1)));
        // 28 Aralık 2026 Pazartesi + 4 = 1 Ocak 2027 Cuma (tatil) -> 4 Ocak Pazartesi
        assertThat(t.sureSonu(LocalDate.of(2026, 12, 28), 4)).isEqualTo(LocalDate.of(2027, 1, 4));
    }
}

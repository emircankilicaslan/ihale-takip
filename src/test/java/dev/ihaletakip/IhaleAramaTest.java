package dev.ihaletakip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;

/** İhale listesinde arama, süzgeç, sıralama ve sayfalama; dışa aktarmanın aynı süzgeçleri kullanması. */
class IhaleAramaTest extends EntegrasyonTemeli {
    private static final String SUNUCU = "Sunucu ve Depolama Yenileme";
    private static final String GUVENLIK = "Kampüs Güvenlik Hizmeti";
    private static final String MOBILYA = "Ofis Mobilyası Alımı";
    private static final String CEPHE = "Bina Cephe Yenileme";

    private long teknikId;
    private long sunucuIhalesi;

    @BeforeEach
    void ihaleleriHazirla() throws Exception {
        teknikId = birimler.findAll().stream().filter(b -> b.getKod().equals("TEKNIK")).findFirst().orElseThrow().getId();
        sunucuIhalesi = ihaleOlustur(SUNUCU, "ACIK", "MAL", "2450000", bidb.getId());
        ihaleOlustur(GUVENLIK, "ACIK", "HIZMET", "8900000", idari.getId());
        ihaleOlustur(MOBILYA, "PAZARLIK", "MAL", "385000", idari.getId());
        ihaleOlustur(CEPHE, "BELLI_ISTEKLILER", "YAPIM", "14200000", teknikId);
    }

    /** Parametreler ad, değer, ad, değer biçiminde verilir. */
    private ResultActions liste(String kullanici, String... adDeger) throws Exception {
        return alSorgu(kullanici, "/api/ihaleler", adDeger).andExpect(status().isOk());
    }

    private String csv(String kullanici, String... adDeger) throws Exception {
        String[] hepsi = new String[adDeger.length + 2];
        hepsi[0] = "bicim";
        hepsi[1] = "csv";
        System.arraycopy(adDeger, 0, hepsi, 2, adDeger.length);
        byte[] bayt = alSorgu(kullanici, "/api/disa-aktar/ihaleler", hepsi)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return new String(bayt, StandardCharsets.UTF_8);
    }

    // ----------------------------------------------------------------------- arama

    @Test
    void aramaAdaGoreYapilirVeBuyukKucukHarfeDuyarsizdir() throws Exception {
        liste("t_uzman", "ara", "sunucu").andExpect(jsonPath("$.content[*].ad", contains(SUNUCU)));
        liste("t_uzman", "ara", "SUNUCU").andExpect(jsonPath("$.content[*].ad", contains(SUNUCU)));
        liste("t_uzman", "ara", "SuNuCu").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void aramaAdinIcindeBirKelimeyiBulur() throws Exception {
        liste("t_uzman", "ara", "yenileme").andExpect(jsonPath("$.content[*].ad", containsInAnyOrder(SUNUCU, CEPHE)));
    }

    @Test
    void aramaTurkceKarakterIcerenAdiBulur() throws Exception {
        liste("t_uzman", "ara", "mobilyası").andExpect(jsonPath("$.content[*].ad", contains(MOBILYA)));
        liste("t_uzman", "ara", "kampüs").andExpect(jsonPath("$.content[*].ad", contains(GUVENLIK)));
    }

    @Test
    void aramaIhaleNumarasinaGoreYapilir() throws Exception {
        String no = JsonPath.read(govde(al("t_uzman", "/api/ihaleler/" + sunucuIhalesi)), "$.ihaleNo");
        liste("t_uzman", "ara", no).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(sunucuIhalesi));
    }

    @Test
    void aramaMetniBoslukluOlsaBoslukKirpilir() throws Exception {
        liste("t_uzman", "ara", "  sunucu  ").andExpect(jsonPath("$.totalElements").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "%%", "s%a", "su_ucu", "\\", "\\%"})
    void likeJokerKarakterleriDuzMetinSayilir(String ara) throws Exception {
        // "%", "_" ve "\" kullanıcı girdisinde joker değil, düz karakterdir; hiçbir ihaleyi eşleştirmemeli
        liste("t_uzman", "ara", ara).andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void bosAramaTumKayitlariGetirir(String ara) throws Exception {
        liste("t_uzman", "ara", ara).andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void cokUzunAramaMetniHataVermezKesilir() throws Exception {
        liste("t_uzman", "ara", "a".repeat(500)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sqlEnjeksiyonDenemesiDuzMetinSayilir() throws Exception {
        liste("t_uzman", "ara", "' OR '1'='1").andExpect(jsonPath("$.totalElements").value(0));
        liste("t_uzman", "ara", "'; DROP TABLE ihale; --").andExpect(jsonPath("$.totalElements").value(0));
        liste("t_uzman").andExpect(jsonPath("$.totalElements").value(4)); // tablo yerinde
    }

    // ---------------------------------------------------------------------- süzgeçler

    @Test
    void usulSuzgeci() throws Exception {
        liste("t_uzman", "usul", "PAZARLIK").andExpect(jsonPath("$.content[*].ad", contains(MOBILYA)));
        liste("t_uzman", "usul", "ACIK").andExpect(jsonPath("$.totalElements").value(2));
        liste("t_uzman", "usul", "BELLI_ISTEKLILER").andExpect(jsonPath("$.content[*].ad", contains(CEPHE)));
    }

    @Test
    void turSuzgeci() throws Exception {
        liste("t_uzman", "tur", "YAPIM").andExpect(jsonPath("$.content[*].ad", contains(CEPHE)));
        liste("t_uzman", "tur", "MAL").andExpect(jsonPath("$.content[*].ad", containsInAnyOrder(SUNUCU, MOBILYA)));
        liste("t_uzman", "tur", "HIZMET").andExpect(jsonPath("$.content[*].ad", contains(GUVENLIK)));
    }

    @Test
    void birimSuzgeci() throws Exception {
        liste("t_uzman", "birimId", String.valueOf(idari.getId()))
                .andExpect(jsonPath("$.content[*].ad", containsInAnyOrder(GUVENLIK, MOBILYA)));
        liste("t_uzman", "birimId", String.valueOf(teknikId)).andExpect(jsonPath("$.content[*].ad", contains(CEPHE)));
        liste("t_uzman", "birimId", "999999").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void maliyetAraligiSinirlariDahildir() throws Exception {
        liste("t_uzman", "minMaliyet", "385000", "maxMaliyet", "2450000")
                .andExpect(jsonPath("$.content[*].ad", containsInAnyOrder(SUNUCU, MOBILYA)));
        liste("t_uzman", "minMaliyet", "9000000").andExpect(jsonPath("$.content[*].ad", contains(CEPHE)));
        liste("t_uzman", "maxMaliyet", "385000").andExpect(jsonPath("$.content[*].ad", contains(MOBILYA)));
        liste("t_uzman", "minMaliyet", "1000000", "maxMaliyet", "2000000").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void suzgeclerBirlikteUygulanir() throws Exception {
        liste("t_uzman", "tur", "MAL", "usul", "ACIK").andExpect(jsonPath("$.content[*].ad", contains(SUNUCU)));
        liste("t_uzman", "ara", "yenileme", "tur", "YAPIM").andExpect(jsonPath("$.content[*].ad", contains(CEPHE)));
        liste("t_uzman", "ara", "yenileme", "tur", "HIZMET").andExpect(jsonPath("$.totalElements").value(0));
        liste("t_uzman", "durum", "TASLAK", "birimId", String.valueOf(idari.getId()), "minMaliyet", "1000000")
                .andExpect(jsonPath("$.content[*].ad", contains(GUVENLIK)));
    }

    @Test
    void durumSuzgeciYeniSuzgeclerleBirlikteCalisir() throws Exception {
        gonder("t_uzman", "/api/ihaleler/" + sunucuIhalesi + "/onaya-gonder", null).andExpect(status().isOk());
        liste("t_uzman", "durum", "ONAY_BEKLIYOR", "tur", "MAL").andExpect(jsonPath("$.content[*].ad", contains(SUNUCU)));
        liste("t_uzman", "durum", "TASLAK", "tur", "MAL").andExpect(jsonPath("$.content[*].ad", contains(MOBILYA)));
        liste("t_uzman", "durum", "ONAY_BEKLIYOR", "tur", "YAPIM").andExpect(jsonPath("$.totalElements").value(0));
    }

    // -------------------------------------------------------------- yetki kapsamı

    @Test
    void birimSorumlusuAramaylaDaYalnizcaKendiBiriminiGorur() throws Exception {
        liste("t_sorumlu").andExpect(jsonPath("$.content[*].ad", contains(SUNUCU)));
        liste("t_sorumlu", "ara", "hizmeti").andExpect(jsonPath("$.totalElements").value(0));
        liste("t_sorumlu", "ara", "kampüs").andExpect(jsonPath("$.totalElements").value(0));
        liste("t_uzman", "ara", "hizmeti").andExpect(jsonPath("$.totalElements").value(1)); // uzman hepsini görür
    }

    @Test
    void birimSorumlusuBaskaBirimiFiltreleyipYetkisiniAsamaz() throws Exception {
        liste("t_sorumlu", "birimId", String.valueOf(idari.getId())).andExpect(jsonPath("$.totalElements").value(0));
        liste("t_sorumlu", "birimId", String.valueOf(teknikId), "tur", "YAPIM").andExpect(jsonPath("$.totalElements").value(0));
        liste("t_baska_sorumlu", "ara", "sunucu").andExpect(jsonPath("$.totalElements").value(0));
    }

    // ----------------------------------------------------------------- sıralama

    @Test
    void siralamaSecenekleri() throws Exception {
        liste("t_uzman", "sirala", "YENI").andExpect(jsonPath("$.content[0].ad").value(CEPHE));
        liste("t_uzman", "sirala", "ESKI").andExpect(jsonPath("$.content[0].ad").value(SUNUCU));
        liste("t_uzman", "sirala", "MALIYET_AZALAN")
                .andExpect(jsonPath("$.content[*].ad", contains(CEPHE, GUVENLIK, SUNUCU, MOBILYA)));
        liste("t_uzman", "sirala", "MALIYET_ARTAN")
                .andExpect(jsonPath("$.content[*].ad", contains(MOBILYA, SUNUCU, GUVENLIK, CEPHE)));
    }

    @Test
    void ihaleTarihineGoreSiralamaTarihiOlmayanlariSondaTutar() throws Exception {
        long id = ilandaIhale(); // ihale tarihi 17 Mart olan tek ihale
        liste("t_uzman", "sirala", "IHALE_TARIHI").andExpect(jsonPath("$.content[0].id").value(id));
    }

    // ------------------------------------------------------------- geçersiz girdi

    @ParameterizedTest(name = "{0}={1} -> 400")
    @CsvSource({"sirala,YOK", "usul,PAZAR", "tur,BILINMEYEN", "durum,YOK", "minMaliyet,abc", "birimId,x",
            "maxMaliyet,'1,5'", "page,abc", "size,abc"})
    void gecersizParametreler400Doner(String ad, String deger) throws Exception {
        alSorgu("t_uzman", "/api/ihaleler", ad, deger).andExpect(status().isBadRequest());
    }

    @Test
    void hamSiralamaAlaniKabulEdilmezBilinmeyenParametreYoksayilir() throws Exception {
        // Eski "sort" parametresiyle ham alan adı verilemez; izin verilen sıralamalar dışında etkisizdir
        liste("t_uzman", "sort", "olusturan.sifreHash,desc").andExpect(jsonPath("$.content[0].ad").value(CEPHE));
    }

    @Test
    void sayfaBoyutuUstSinirlidirVeNegatifSayfaSifirOlur() throws Exception {
        liste("t_uzman", "size", "1000").andExpect(jsonPath("$.size").value(100));
        liste("t_uzman", "size", "0").andExpect(jsonPath("$.size").value(1));
        liste("t_uzman", "size", "-7").andExpect(jsonPath("$.size").value(1));
        liste("t_uzman", "page", "-5").andExpect(jsonPath("$.number").value(0));
    }

    @Test
    void sayfalamaSuzgecleUyumludur() throws Exception {
        liste("t_uzman", "tur", "MAL", "size", "1", "page", "0").andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content.length()").value(1));
        liste("t_uzman", "tur", "MAL", "size", "1", "page", "1").andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));
        liste("t_uzman", "tur", "MAL", "size", "1", "page", "2").andExpect(jsonPath("$.content.length()").value(0));
    }

    // -------------------------------------------------------------- dışa aktarma

    @Test
    void disaAktarmaAyniSuzgecleriKullanir() throws Exception {
        String sonuc = csv("t_uzman", "tur", "YAPIM", "ara", "cephe");
        assertThat(sonuc).contains(CEPHE).doesNotContain(SUNUCU, GUVENLIK, MOBILYA);
    }

    @Test
    void disaAktarmaMaliyetAraligiVeUsuluUygular() throws Exception {
        String sonuc = csv("t_uzman", "usul", "ACIK", "minMaliyet", "3000000");
        assertThat(sonuc).contains(GUVENLIK).doesNotContain(SUNUCU, MOBILYA, CEPHE);
    }

    @Test
    void disaAktarmaSiralamayaUyar() throws Exception {
        String artan = csv("t_uzman", "sirala", "MALIYET_ARTAN");
        assertThat(artan.indexOf(MOBILYA)).isLessThan(artan.indexOf(CEPHE));
        String azalan = csv("t_uzman", "sirala", "MALIYET_AZALAN");
        assertThat(azalan.indexOf(CEPHE)).isLessThan(azalan.indexOf(MOBILYA));
    }

    @Test
    void disaAktarmaYetkiKapsaminiAsmaz() throws Exception {
        assertThat(csv("t_sorumlu", "ara", "ofis")).doesNotContain(MOBILYA, GUVENLIK, CEPHE);
        assertThat(csv("t_sorumlu")).contains(SUNUCU).doesNotContain(GUVENLIK);
    }

    @Test
    void disaAktarmaGecersizSuzgecte400Doner() throws Exception {
        alSorgu("t_uzman", "/api/disa-aktar/ihaleler", "bicim", "csv", "sirala", "YOK").andExpect(status().isBadRequest());
    }
}

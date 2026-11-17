package dev.ihaletakip;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.ihaletakip.config.ZamanConfig;
import dev.ihaletakip.domain.Birim;
import dev.ihaletakip.domain.Kullanici;
import dev.ihaletakip.domain.Rol;
import dev.ihaletakip.repo.BirimDeposu;
import dev.ihaletakip.repo.KullaniciDeposu;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entegrasyon testlerinin ortak tabanı: gerçek Oracle veritabanı, gerçek güvenlik zinciri.
 * Her test geri alınan bir işlemde çalışır; zaman {@link AyarlanabilirClock} ile ilerletilir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(EntegrasyonTemeli.SaatAyari.class)
abstract class EntegrasyonTemeli {
    protected static final String SIFRE = "Test-Sifre-1";
    protected static final LocalDate BASLANGIC = LocalDate.of(2026, 3, 2); // Pazartesi

    @TestConfiguration
    static class SaatAyari {
        @Bean
        @Primary
        AyarlanabilirClock testSaati() {
            return new AyarlanabilirClock(ZamanConfig.TR, BASLANGIC);
        }
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected AyarlanabilirClock saat;
    @Autowired protected KullaniciDeposu kullanicilar;
    @Autowired protected BirimDeposu birimler;
    @Autowired protected PasswordEncoder kodlayici;
    @Autowired protected JdbcTemplate jdbc;

    protected Birim bidb;
    protected Birim idari;

    @BeforeEach
    void hazirla() {
        saat.ayarla(BASLANGIC);
        bidb = birimler.findAll().stream().filter(b -> b.getKod().equals("BIDB")).findFirst().orElseThrow();
        idari = birimler.findAll().stream().filter(b -> b.getKod().equals("IDARI")).findFirst().orElseThrow();
        kullan("t_sorumlu", Rol.BIRIM_SORUMLUSU, bidb);
        kullan("t_baska_sorumlu", Rol.BIRIM_SORUMLUSU, idari);
        kullan("t_mudur", Rol.BIRIM_MUDURU, bidb);
        kullan("t_baskan", Rol.DAIRE_BASKANI, bidb);
        kullan("t_yonetici", Rol.UST_YONETICI, null);
        kullan("t_uzman", Rol.SATIN_ALMA_UZMANI, null);
        kullan("t_komisyon", Rol.KOMISYON_UYESI, null);
        kullan("t_denetci", Rol.DENETCI, null);
        kullan("t_admin", Rol.ADMIN, null);
    }

    protected void kullan(String ad, Rol rol, Birim birim) {
        kullanicilar.save(new Kullanici(ad, ad, kodlayici.encode(SIFRE), rol, birim));
    }

    // ------------------------------------------------------------ istek yardımcıları

    protected ResultActions girisYap(String kullaniciAdi, String sifre) throws Exception {
        return mvc.perform(post("/api/auth/giris").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kullaniciAdi\":\"%s\",\"sifre\":\"%s\"}".formatted(kullaniciAdi, sifre)));
    }

    protected String giris(String kullaniciAdi) throws Exception {
        String yanit = girisYap(kullaniciAdi, SIFRE)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(yanit, "$.token");
    }

    /** Token her çağrıda yeniden alınır: saat ileri alındığında eski token süresi dolmuş sayılır. */
    protected ResultActions gonder(String kullanici, String yol, String govde) throws Exception {
        var istek = post(yol).header("Authorization", "Bearer " + giris(kullanici));
        if (govde != null) {
            istek.contentType(MediaType.APPLICATION_JSON).content(govde);
        }
        return mvc.perform(istek);
    }

    protected ResultActions guncelle(String kullanici, String yol, String govde) throws Exception {
        return mvc.perform(put(yol).header("Authorization", "Bearer " + giris(kullanici))
                .contentType(MediaType.APPLICATION_JSON).content(govde));
    }

    protected ResultActions al(String kullanici, String yol) throws Exception {
        return mvc.perform(get(yol).header("Authorization", "Bearer " + giris(kullanici)));
    }

    /**
     * Sorgu parametrelerini ham değerle gönderir (URL'e elle kodlanmış metin yazılmaz; MockMvc kodlamayı kendisi yapar).
     * Parametreler ad, değer, ad, değer biçiminde verilir.
     */
    protected ResultActions alSorgu(String kullanici, String yol, String... adDeger) throws Exception {
        var istek = get(yol).header("Authorization", "Bearer " + giris(kullanici));
        for (int i = 0; i + 1 < adDeger.length; i += 2) {
            istek.param(adDeger[i], adDeger[i + 1]);
        }
        return mvc.perform(istek);
    }

    protected String govde(ResultActions sonuc) throws Exception {
        return sonuc.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ iş akışı yardımcıları

    protected long ihaleOlustur(String kullanici, String maliyet) throws Exception {
        String yanit = govde(gonder(kullanici, "/api/ihaleler", """
                {"ad":"Sunucu alımı","usul":"ACIK","tur":"MAL","yaklasikMaliyet":%s}""".formatted(maliyet))
                .andExpect(status().isCreated()));
        return ((Number) JsonPath.read(yanit, "$.id")).longValue();
    }

    /** Satın alma uzmanı olarak, verilen özelliklerle ihale oluşturur (arama ve süzgeç testleri için). */
    protected long ihaleOlustur(String ad, String usul, String tur, String maliyet, long birimId) throws Exception {
        String govde = """
                {"ad":"%s","usul":"%s","tur":"%s","yaklasikMaliyet":%s,"birimId":%d}""".formatted(ad, usul, tur, maliyet, birimId);
        String yanit = govde(gonder("t_uzman", "/api/ihaleler", govde).andExpect(status().isCreated()));
        return ((Number) JsonPath.read(yanit, "$.id")).longValue();
    }

    protected void onaylanmisIhaleyiIlanEt(long id) throws Exception {
        gonder("t_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null).andExpect(status().isOk());
        gonder("t_baskan", "/api/ihaleler/" + id + "/onayla", null).andExpect(status().isOk());
        gonder("t_uzman", "/api/ihaleler/" + id + "/ilan",
                "{\"ilanTarihi\":\"2026-03-02\",\"ihaleTarihi\":\"2026-03-17\"}").andExpect(status().isOk());
    }

    protected long teklifVer(long ihaleId, String vergiNo, String tutar) throws Exception {
        String yanit = govde(gonder("t_uzman", "/api/ihaleler/" + ihaleId + "/teklifler", """
                {"istekliAdi":"Firma %s","vergiNo":"%s","tutar":%s}""".formatted(vergiNo, vergiNo, tutar))
                .andExpect(status().isCreated()));
        return ((Number) JsonPath.read(yanit, "$.id")).longValue();
    }

    /** 2.000.000 TL'lik ihaleyi onaylatıp ilan eder (ilan 2 Mart, ihale 17 Mart). */
    protected long ilandaIhale() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "2000000");
        onaylanmisIhaleyiIlanEt(id);
        return id;
    }

    /** İhale gününe geçip komisyon değerlendirmesini başlatır. */
    protected void degerlendirmeyeAl(long id) throws Exception {
        saat.ayarla(LocalDate.of(2026, 3, 17));
        gonder("t_komisyon", "/api/ihaleler/" + id + "/degerlendirmeye-al", null).andExpect(status().isOk());
    }
}

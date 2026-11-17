package dev.ihaletakip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** Sağlık kontrolü, API belgesi, giriş doğrulaması ve hata yanıtlarının biçimi. */
class SozlesmeVeSaglikTest extends EntegrasyonTemeli {

    @Test
    void saglikUygulamaAyaktaIkenUpDoner() throws Exception {
        // Regresyon: LDAP kütüphanesi sınıf yolundayken sağlık kontrolü var olmayan dizine bağlanıp DOWN dönüyordu
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void diger_actuatorUcNoktalariAcikDegildir() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        al("t_admin", "/actuator/env").andExpect(status().isNotFound());
        al("t_admin", "/actuator/heapdump").andExpect(status().isNotFound());
    }

    @Test
    void openApiBelgesiUcNoktalariVeGuvenlikSemasiniIcerir() throws Exception {
        String belge = govde(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        assertThat(belge).contains("/api/ihaleler", "/api/kullanicilar", "/api/disa-aktar/ihaleler",
                "/api/auth/giris", "\"bearer\"", "İhale Takip API");
    }

    @Test
    void swaggerAdresiArayuzeYonlendirir() throws Exception {
        mvc.perform(get("/swagger")).andExpect(status().is3xxRedirection());
    }

    @Test
    void swaggerArayuzuHaricKendiSayfalarimizSikiCspIleSunulur() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(header().doesNotExist("Content-Security-Policy"));
        mvc.perform(get("/")).andExpect(header().exists("Content-Security-Policy"));
        mvc.perform(get("/app.js")).andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void oturumsuzApiIstekleriReddedilir() throws Exception {
        mvc.perform(get("/api/ihaleler")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ihaleler/ozet")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/kullanicilar")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/denetim")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ihaleler").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bozukVeSahteTokenReddedilir() throws Exception {
        mvc.perform(get("/api/ihaleler").header("Authorization", "Bearer bu-bir-token-degil"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ihaleler").header("Authorization", "Basic dXNlcjpwYXNz")).andExpect(status().isUnauthorized());
        String gecerli = giris("t_uzman");
        // İmzanın ilk karakteri değiştirilir (sona karakter eklemek Base64 çözümlemede imzayı bozmaz)
        int nokta = gecerli.lastIndexOf('.');
        char ilk = gecerli.charAt(nokta + 1);
        String bozukImza = gecerli.substring(0, nokta + 1) + (ilk == 'A' ? 'B' : 'A') + gecerli.substring(nokta + 2);
        mvc.perform(get("/api/ihaleler").header("Authorization", "Bearer " + bozukImza))
                .andExpect(status().isUnauthorized());
        // Yük (payload) değiştirilirse imza artık uymaz
        String[] parcalar = gecerli.split("\\.");
        String sahteYuk = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"t_admin\",\"exp\":9999999999}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get("/api/kullanicilar").header("Authorization", "Bearer " + parcalar[0] + "." + sahteYuk + "." + parcalar[2]))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suresiDolanTokenReddedilir() throws Exception {
        String token = giris("t_uzman");
        mvc.perform(get("/api/ihaleler").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        saat.ayarla(BASLANGIC.plusDays(1)); // 60 dakikalık geçerlilik aşıldı
        mvc.perform(get("/api/ihaleler").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void benUcNoktasiOturumAcanKullaniciyiDoner() throws Exception {
        al("t_komisyon", "/api/auth/ben").andExpect(status().isOk())
                .andExpect(jsonPath("$.kullaniciAdi").value("t_komisyon"))
                .andExpect(jsonPath("$.rol").value("KOMISYON_UYESI"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void birimlerListelenir() throws Exception {
        al("t_sorumlu", "/api/birimler").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.kod == 'TEKNIK')].ad").value("Teknik Hizmetler Daire Başkanlığı"));
    }

    // ---------------------------------------------------------------- girdi doğrulama

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"usul\":\"ACIK\",\"tur\":\"MAL\",\"yaklasikMaliyet\":1000,\"birimId\":1}",
            "{\"ad\":\"\",\"usul\":\"ACIK\",\"tur\":\"MAL\",\"yaklasikMaliyet\":1000,\"birimId\":1}",
            "{\"ad\":\"x\",\"tur\":\"MAL\",\"yaklasikMaliyet\":1000,\"birimId\":1}",
            "{\"ad\":\"x\",\"usul\":\"ACIK\",\"tur\":\"MAL\",\"yaklasikMaliyet\":-5,\"birimId\":1}",
            "{\"ad\":\"x\",\"usul\":\"ACIK\",\"tur\":\"MAL\",\"yaklasikMaliyet\":0,\"birimId\":1}",
            "{\"ad\":\"x\",\"usul\":\"ACIK\",\"tur\":\"MAL\",\"yaklasikMaliyet\":1.234,\"birimId\":1}",
            "{\"ad\":\"x\",\"usul\":\"PAZAR\",\"tur\":\"MAL\",\"yaklasikMaliyet\":1000,\"birimId\":1}",
            "{\"ad\":\"x\",\"usul\":\"ACIK\",\"tur\":\"MAL\",\"yaklasikMaliyet\":\"cok\",\"birimId\":1}",
            "bu json degil"
    })
    void gecersizIhaleIstekleri400DonerVeKayitOlusmaz(String govde) throws Exception {
        long once = jdbc.queryForObject("SELECT COUNT(*) FROM ihale", Long.class);
        gonder("t_uzman", "/api/ihaleler", govde).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ihale", Long.class)).isEqualTo(once);
    }

    @Test
    void dogrulamaHatasiAlanAdiniAciklamadaGosterir() throws Exception {
        gonder("t_uzman", "/api/ihaleler", "{\"usul\":\"ACIK\",\"tur\":\"MAL\",\"yaklasikMaliyet\":1000,\"birimId\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ad")));
    }

    @Test
    void olmayanIhale404Doner() throws Exception {
        al("t_uzman", "/api/ihaleler/999999").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("İhale bulunamadı: 999999"));
    }

    @Test
    void ihaleNumarasiYilVeSiraIcerir() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "100000");
        String yanit = govde(al("t_sorumlu", "/api/ihaleler/" + id).andExpect(status().isOk()));
        assertThat(yanit).containsPattern("\"ihaleNo\":\"2026/\\d{5}\"");
    }

    @Test
    void bosGirisIstegiReddedilir() throws Exception {
        mvc.perform(post("/api/auth/giris").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}

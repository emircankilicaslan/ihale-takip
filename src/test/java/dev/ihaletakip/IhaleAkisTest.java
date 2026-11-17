package dev.ihaletakip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** Uçtan uca akış testi: taslaktan sözleşmeye tüm adımlar, yetki ve kapsam kuralları. */
class IhaleAkisTest extends EntegrasyonTemeli {

    // ------------------------------------------------------------------ testler

    @Test
    void tamAkisTaslaktanSozlesmeyeKadar() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "2000000"); // 2. kademe

        gonder("t_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durum").value("ONAY_BEKLIYOR"))
                .andExpect(jsonPath("$.gerekliOnayKademesi").value(2));

        // Birim müdürü (seviye 1) 2. kademe ihaleyi onaylayamaz
        gonder("t_mudur", "/api/ihaleler/" + id + "/onayla", null).andExpect(status().isForbidden());
        gonder("t_baskan", "/api/ihaleler/" + id + "/onayla", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("ONAYLANDI"));

        // İlan süresi: ACIK usulde 15 gün -> 2026-03-17 en erken
        gonder("t_uzman", "/api/ihaleler/" + id + "/ilan",
                "{\"ilanTarihi\":\"2026-03-02\",\"ihaleTarihi\":\"2026-03-10\"}")
                .andExpect(status().isUnprocessableEntity());
        gonder("t_uzman", "/api/ihaleler/" + id + "/ilan",
                "{\"ilanTarihi\":\"2026-03-02\",\"ihaleTarihi\":\"2026-03-21\"}") // Cumartesi
                .andExpect(status().isUnprocessableEntity());
        gonder("t_uzman", "/api/ihaleler/" + id + "/ilan",
                "{\"ilanTarihi\":\"2026-03-02\",\"ihaleTarihi\":\"2026-03-17\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("ILANDA"));

        // Teklifler
        saat.ayarla(LocalDate.of(2026, 3, 10));
        long asiriDusuk = teklifVer(id, "1111111111", "1000000"); // 2.000.000 x 0,70 = 1.400.000 altı
        long enDusuk = teklifVer(id, "2222222222", "1600000");
        long baska = teklifVer(id, "3333333333", "1900000");
        long asan = teklifVer(id, "4444444444", "2100000"); // yaklaşık maliyeti aşar

        gonder("t_uzman", "/api/ihaleler/" + id + "/teklifler",
                "{\"istekliAdi\":\"Tekrar\",\"vergiNo\":\"2222222222\",\"tutar\":1500000}")
                .andExpect(status().isUnprocessableEntity());

        // İhale tarihi gelmeden değerlendirmeye geçilemez
        gonder("t_komisyon", "/api/ihaleler/" + id + "/degerlendirmeye-al", null)
                .andExpect(status().isUnprocessableEntity());

        saat.ayarla(LocalDate.of(2026, 3, 17));
        gonder("t_komisyon", "/api/ihaleler/" + id + "/degerlendirmeye-al", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("DEGERLENDIRMEDE"));

        // Süre dolduktan sonra teklif alınamaz (ihale artık ILANDA değil)
        gonder("t_uzman", "/api/ihaleler/" + id + "/teklifler",
                "{\"istekliAdi\":\"Geç\",\"vergiNo\":\"5555555555\",\"tutar\":1700000}")
                .andExpect(status().isUnprocessableEntity());

        // Aşırı düşük teklif işaretlenmiş olmalı
        mvc.perform(get("/api/ihaleler/" + id + "/teklifler").header("Authorization", "Bearer " + giris("t_komisyon")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].asiriDusuk").value(true))
                .andExpect(jsonPath("$[1].asiriDusuk").value(false));

        // Aşırı düşük teklifin açıklaması reddedilir
        gonder("t_komisyon", "/api/ihaleler/" + id + "/teklifler/" + asiriDusuk + "/asiri-dusuk-aciklama",
                "{\"karar\":\"RED\"}").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("REDDEDILDI"));

        // Kazanan en düşük uygun teklif olmalı; maliyeti aşan teklif seçilemez
        gonder("t_komisyon", "/api/ihaleler/" + id + "/karar", "{\"teklifId\":" + baska + "}")
                .andExpect(status().isUnprocessableEntity());
        gonder("t_komisyon", "/api/ihaleler/" + id + "/karar", "{\"teklifId\":" + asan + "}")
                .andExpect(status().isUnprocessableEntity());
        gonder("t_komisyon", "/api/ihaleler/" + id + "/karar", "{\"teklifId\":" + enDusuk + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durum").value("KARARLANDI"))
                .andExpect(jsonPath("$.kazananTeklifId").value(enDusuk))
                .andExpect(jsonPath("$.itirazSonTarihi").value("2026-03-27")); // 17 Mart + 10 gün, Cuma

        // İtiraz süresi bitmeden sözleşme imzalanamaz
        saat.ayarla(LocalDate.of(2026, 3, 27));
        gonder("t_uzman", "/api/ihaleler/" + id + "/sozlesme", null).andExpect(status().isUnprocessableEntity());
        saat.ayarla(LocalDate.of(2026, 3, 30));
        gonder("t_uzman", "/api/ihaleler/" + id + "/sozlesme", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("SOZLESME_IMZALANDI"));

        // Denetçi tüm adımları kayıttan görebilir
        String denetim = mvc.perform(get("/api/denetim")
                        .param("varlikTuru", "IHALE").param("varlikId", String.valueOf(id))
                        .header("Authorization", "Bearer " + giris("t_denetci")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(denetim).contains("OLUSTUR", "ONAYA_GONDER", "ONAYLA", "ILAN_ET", "KARAR", "SOZLESME_IMZALA");
    }

    @Test
    void yetkisizVeKimliksizIstekler() throws Exception {
        mvc.perform(get("/api/ihaleler")).andExpect(status().isUnauthorized());

        long id = ihaleOlustur("t_sorumlu", "100000");
        gonder("t_sorumlu", "/api/ihaleler/" + id + "/ilan",
                "{\"ilanTarihi\":\"2026-03-02\",\"ihaleTarihi\":\"2026-03-17\"}").andExpect(status().isForbidden());
        gonder("t_denetci", "/api/ihaleler",
                "{\"ad\":\"x\",\"usul\":\"ACIK\",\"tur\":\"MAL\",\"yaklasikMaliyet\":1000}")
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/denetim").header("Authorization", "Bearer " + giris("t_sorumlu")))
                .andExpect(status().isForbidden());
    }

    @Test
    void birimSorumlusuBaskaBirimInIhalesiniGoremez() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "100000");
        mvc.perform(get("/api/ihaleler/" + id).header("Authorization", "Bearer " + giris("t_baska_sorumlu")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ihaleler").header("Authorization", "Bearer " + giris("t_baska_sorumlu")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/ihaleler").header("Authorization", "Bearer " + giris("t_uzman")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id));
    }

    @Test
    void arayuzDosyalariAcikAmaVeriKorumalidir() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/style.css")).andExpect(status().isOk());
        mvc.perform(get("/api/birimler")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/birimler").header("Authorization", "Bearer " + giris("t_uzman")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.kod == 'BIDB')]").exists());
        mvc.perform(get("/")).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")));
    }

    @Test
    void ozetKullanicininGordugucuKayitlardanHesaplanir() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "100000");
        gonder("t_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null).andExpect(status().isOk());
        mvc.perform(get("/api/ihaleler/ozet").header("Authorization", "Bearer " + giris("t_uzman")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplam").value(1))
                .andExpect(jsonPath("$.durumlar.ONAY_BEKLIYOR").value(1))
                .andExpect(jsonPath("$.durumlar.TASLAK").value(0))
                .andExpect(jsonPath("$.devamEdenYaklasikMaliyet").value(100000.0));
        // Başka birimin sorumlusu bu ihaleyi özetinde görmez
        mvc.perform(get("/api/ihaleler/ozet").header("Authorization", "Bearer " + giris("t_baska_sorumlu")))
                .andExpect(jsonPath("$.toplam").value(0));
    }

    @Test
    void yanlisParolaDetaysizReddedilir() throws Exception {
        mvc.perform(post("/api/auth/giris").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kullaniciAdi\":\"t_sorumlu\",\"sifre\":\"yanlis\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/giris").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kullaniciAdi\":\"yok_boyle_biri\",\"sifre\":\"yanlis\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ilanVerilmisIhaleyeGecTeklifAlinmaz() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "2000000");
        onaylanmisIhaleyiIlanEt(id);
        saat.ayarla(LocalDate.of(2026, 3, 18)); // ihale tarihinden sonra
        gonder("t_uzman", "/api/ihaleler/" + id + "/teklifler",
                "{\"istekliAdi\":\"Geç\",\"vergiNo\":\"1234567890\",\"tutar\":1700000}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void denetimKaydiVeritabaniDuzeyindeSilinemezVeDegistirilemez() throws Exception {
        ihaleOlustur("t_sorumlu", "100000");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM denetim_kaydi")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE denetim_kaydi SET islem = 'X'"))
                .isInstanceOf(DataAccessException.class);
    }
}

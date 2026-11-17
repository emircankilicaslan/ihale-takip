package dev.ihaletakip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ihaletakip.domain.Rol;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Ayrıntılı iş kuralı senaryoları: onay kademeleri, teklif kararları, iptal, tatil takvimi, listeleme. */
class IhaleSenaryoTest extends EntegrasyonTemeli {

    private int kod(org.springframework.test.web.servlet.ResultActions sonuc) throws Exception {
        return sonuc.andReturn().getResponse().getStatus();
    }

    // ----------------------------------------------------------------- onay akışı

    @ParameterizedTest(name = "{0} TL ihaleyi {1} onaylarsa -> {2}")
    @CsvSource({
            "400000,t_mudur,200",
            "400000,t_baskan,200",
            "400000,t_yonetici,200",
            "500000,t_mudur,200",
            "500000.01,t_mudur,403",
            "2000000,t_mudur,403",
            "2000000,t_baskan,200",
            "5000000,t_baskan,200",
            "5000000.01,t_baskan,403",
            "8000000,t_baskan,403",
            "8000000,t_yonetici,200"
    })
    void onayKademesiYetkiyiBelirler(String maliyet, String onaylayan, int beklenen) throws Exception {
        long id = ihaleOlustur("t_sorumlu", maliyet);
        gonder("t_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null).andExpect(status().isOk());
        assertThat(kod(gonder(onaylayan, "/api/ihaleler/" + id + "/onayla", null))).isEqualTo(beklenen);
    }

    @Test
    void baskaBirimMuduruOnaylayamaz() throws Exception {
        kullan("t_baska_mudur", Rol.BIRIM_MUDURU, idari);
        long id = ihaleOlustur("t_sorumlu", "400000");
        gonder("t_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null).andExpect(status().isOk());
        gonder("t_baska_mudur", "/api/ihaleler/" + id + "/onayla", null).andExpect(status().isForbidden());
    }

    @Test
    void baskaBirimSorumlusuIhaleyiOnayaGonderemez() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "400000");
        gonder("t_baska_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null).andExpect(status().isForbidden());
    }

    @Test
    void onayBeklemeyenIhaleOnaylanamaz() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "400000"); // hâlâ taslak
        gonder("t_baskan", "/api/ihaleler/" + id + "/onayla", null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ikinciKezOnaylanamaz() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "400000");
        gonder("t_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null).andExpect(status().isOk());
        gonder("t_mudur", "/api/ihaleler/" + id + "/onayla", null).andExpect(status().isOk());
        gonder("t_mudur", "/api/ihaleler/" + id + "/onayla", null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void taslakIkinciKezOnayaGonderilemez() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "400000");
        gonder("t_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null).andExpect(status().isOk());
        gonder("t_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void geriCevrilenIhaleTaslagaDonerVeYenidenGonderilebilir() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "2000000");
        gonder("t_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null).andExpect(status().isOk());
        gonder("t_baskan", "/api/ihaleler/" + id + "/geri-cevir", "{\"gerekce\":\"Teknik şartname eksik\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durum").value("TASLAK"))
                .andExpect(jsonPath("$.gerekce").value("Teknik şartname eksik"));
        gonder("t_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("ONAY_BEKLIYOR"));
    }

    @Test
    void geriCevirmedeGerekceZorunludur() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "400000");
        gonder("t_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null).andExpect(status().isOk());
        gonder("t_mudur", "/api/ihaleler/" + id + "/geri-cevir", "{\"gerekce\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void kademesiYetmeyenGeriCeviremez() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "2000000");
        gonder("t_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null).andExpect(status().isOk());
        gonder("t_mudur", "/api/ihaleler/" + id + "/geri-cevir", "{\"gerekce\":\"x\"}").andExpect(status().isForbidden());
    }

    // --------------------------------------------------------------------- iptal

    @Test
    void daireBaskaniIhaleyiIptalEderVeIptalGeriAlinmaz() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "400000");
        gonder("t_baskan", "/api/ihaleler/" + id + "/iptal", "{\"gerekce\":\"İhtiyaç kalmadı\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durum").value("IPTAL"))
                .andExpect(jsonPath("$.gerekce").value("İhtiyaç kalmadı"));
        gonder("t_baskan", "/api/ihaleler/" + id + "/iptal", "{\"gerekce\":\"tekrar\"}")
                .andExpect(status().isUnprocessableEntity());
        gonder("t_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void iptalYetkisiYalnizcaDaireBaskaniVeUstYoneticidedir() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "400000");
        for (String yetkisiz : new String[] {"t_sorumlu", "t_mudur", "t_uzman", "t_komisyon", "t_denetci", "t_admin"}) {
            gonder(yetkisiz, "/api/ihaleler/" + id + "/iptal", "{\"gerekce\":\"x\"}").andExpect(status().isForbidden());
        }
        gonder("t_yonetici", "/api/ihaleler/" + id + "/iptal", "{\"gerekce\":\"x\"}").andExpect(status().isOk());
    }

    @Test
    void iptaldeGerekceZorunludur() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "400000");
        gonder("t_baskan", "/api/ihaleler/" + id + "/iptal", "{\"gerekce\":\"  \"}").andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- ilan kuralları

    @Test
    void taslakIhaleIlanEdilemez() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "400000");
        gonder("t_uzman", "/api/ihaleler/" + id + "/ilan", "{\"ilanTarihi\":\"2026-03-02\",\"ihaleTarihi\":\"2026-03-17\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ilanSuresiTamSinirdaKabulBirGunEksikteReddedilir() throws Exception {
        long a = ihaleOlustur("t_sorumlu", "400000");
        gonder("t_sorumlu", "/api/ihaleler/" + a + "/onaya-gonder", null).andExpect(status().isOk());
        gonder("t_mudur", "/api/ihaleler/" + a + "/onayla", null).andExpect(status().isOk());
        gonder("t_uzman", "/api/ihaleler/" + a + "/ilan", "{\"ilanTarihi\":\"2026-03-02\",\"ihaleTarihi\":\"2026-03-16\"}")
                .andExpect(status().isUnprocessableEntity()); // 14 gün: 15 gün gerekir
        gonder("t_uzman", "/api/ihaleler/" + a + "/ilan", "{\"ilanTarihi\":\"2026-03-02\",\"ihaleTarihi\":\"2026-03-17\"}")
                .andExpect(status().isOk());
    }

    @Test
    void ilanSuresiTakvimGunuOlarakHesaplanir() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "400000");
        gonder("t_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null).andExpect(status().isOk());
        gonder("t_mudur", "/api/ihaleler/" + id + "/onayla", null).andExpect(status().isOk());
        // 4 Mart + 15 gün = 19 Mart Perşembe: iş günü; 18 Mart reddedilir
        gonder("t_uzman", "/api/ihaleler/" + id + "/ilan", "{\"ilanTarihi\":\"2026-03-04\",\"ihaleTarihi\":\"2026-03-18\"}")
                .andExpect(status().isUnprocessableEntity());
        gonder("t_uzman", "/api/ihaleler/" + id + "/ilan", "{\"ilanTarihi\":\"2026-03-04\",\"ihaleTarihi\":\"2026-03-19\"}")
                .andExpect(status().isOk());
    }

    @Test
    void ilanYetkisiYalnizcaSatinAlmaUzmanindadir() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "400000");
        gonder("t_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null).andExpect(status().isOk());
        gonder("t_mudur", "/api/ihaleler/" + id + "/onayla", null).andExpect(status().isOk());
        String govde = "{\"ilanTarihi\":\"2026-03-02\",\"ihaleTarihi\":\"2026-03-17\"}";
        for (String yetkisiz : new String[] {"t_sorumlu", "t_mudur", "t_baskan", "t_komisyon", "t_denetci"}) {
            gonder(yetkisiz, "/api/ihaleler/" + id + "/ilan", govde).andExpect(status().isForbidden());
        }
    }

    // ----------------------------------------------------------------- resmi tatiller

    @Test
    void yoneticiResmiTatilEklerVeIhaleTarihiTatileDenkGelemez() throws Exception {
        gonder("t_admin", "/api/resmi-tatiller", "{\"tarih\":\"2026-03-17\",\"ad\":\"Deneme tatili\"}")
                .andExpect(status().isCreated());
        long id = ihaleOlustur("t_sorumlu", "400000");
        gonder("t_sorumlu", "/api/ihaleler/" + id + "/onaya-gonder", null).andExpect(status().isOk());
        gonder("t_mudur", "/api/ihaleler/" + id + "/onayla", null).andExpect(status().isOk());
        gonder("t_uzman", "/api/ihaleler/" + id + "/ilan", "{\"ilanTarihi\":\"2026-03-02\",\"ihaleTarihi\":\"2026-03-17\"}")
                .andExpect(status().isUnprocessableEntity());
        gonder("t_uzman", "/api/ihaleler/" + id + "/ilan", "{\"ilanTarihi\":\"2026-03-02\",\"ihaleTarihi\":\"2026-03-18\"}")
                .andExpect(status().isOk());
    }

    @Test
    void ayniTatilIkinciKezEklenemez() throws Exception {
        String govde = "{\"tarih\":\"2026-04-23\",\"ad\":\"Zaten kayıtlı\"}"; // migrasyonla gelen resmi tatil
        gonder("t_admin", "/api/resmi-tatiller", govde).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void resmiTatilEklemeYalnizcaYoneticiyeAcikTir() throws Exception {
        for (String yetkisiz : new String[] {"t_sorumlu", "t_baskan", "t_yonetici", "t_uzman", "t_denetci"}) {
            gonder(yetkisiz, "/api/resmi-tatiller", "{\"tarih\":\"2026-12-24\",\"ad\":\"x\"}")
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void resmiTatilIstegiEksikseReddedilir() throws Exception {
        gonder("t_admin", "/api/resmi-tatiller", "{\"tarih\":\"2026-12-24\"}").andExpect(status().isBadRequest());
        gonder("t_admin", "/api/resmi-tatiller", "{\"ad\":\"x\"}").andExpect(status().isBadRequest());
    }

    // ----------------------------------------------------------------------- teklifler

    @Test
    void taslakIhaleyeTeklifVerilemez() throws Exception {
        long id = ihaleOlustur("t_sorumlu", "400000");
        gonder("t_uzman", "/api/ihaleler/" + id + "/teklifler",
                "{\"istekliAdi\":\"Firma\",\"vergiNo\":\"1234567890\",\"tutar\":1000}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ilanTarihindenOnceTeklifAlinmaz() throws Exception {
        long id = ilandaIhale();
        saat.ayarla(LocalDate.of(2026, 3, 1));
        gonder("t_uzman", "/api/ihaleler/" + id + "/teklifler",
                "{\"istekliAdi\":\"Firma\",\"vergiNo\":\"1234567890\",\"tutar\":1500000}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ihaleGunuSonuncuGunOlarakTeklifAlinir() throws Exception {
        long id = ilandaIhale();
        saat.ayarla(LocalDate.of(2026, 3, 17)); // ihale günü
        teklifVer(id, "1234567890", "1500000");
        saat.ayarla(LocalDate.of(2026, 3, 18));
        gonder("t_uzman", "/api/ihaleler/" + id + "/teklifler",
                "{\"istekliAdi\":\"Geç\",\"vergiNo\":\"9876543210\",\"tutar\":1500000}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ayniIstekliAyniIhaleyeIkiTeklifVeremez() throws Exception {
        long id = ilandaIhale();
        teklifVer(id, "1234567890", "1500000");
        gonder("t_uzman", "/api/ihaleler/" + id + "/teklifler",
                "{\"istekliAdi\":\"Aynı firma\",\"vergiNo\":\"1234567890\",\"tutar\":1400000}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ayniIstekliFarkliIhalelereTeklifVerebilir() throws Exception {
        long a = ilandaIhale();
        long b = ilandaIhale();
        teklifVer(a, "1234567890", "1500000");
        teklifVer(b, "1234567890", "1500000");
    }

    @Test
    void gecersizTeklifIstekleriReddedilir() throws Exception {
        long id = ilandaIhale();
        String yol = "/api/ihaleler/" + id + "/teklifler";
        gonder("t_uzman", yol, "{\"istekliAdi\":\"F\",\"vergiNo\":\"123\",\"tutar\":1000}").andExpect(status().isBadRequest());
        gonder("t_uzman", yol, "{\"istekliAdi\":\"F\",\"vergiNo\":\"1234567890\",\"tutar\":0}").andExpect(status().isBadRequest());
        gonder("t_uzman", yol, "{\"istekliAdi\":\"F\",\"vergiNo\":\"1234567890\",\"tutar\":-5}").andExpect(status().isBadRequest());
        gonder("t_uzman", yol, "{\"istekliAdi\":\"\",\"vergiNo\":\"1234567890\",\"tutar\":1000}").andExpect(status().isBadRequest());
        gonder("t_uzman", yol, "{\"istekliAdi\":\"F\",\"vergiNo\":\"abcdefghij\",\"tutar\":1000}").andExpect(status().isBadRequest());
    }

    @Test
    void teklifleriYalnizcaYetkiliRollerGorur() throws Exception {
        long id = ilandaIhale();
        teklifVer(id, "1234567890", "1500000");
        for (String yetkili : new String[] {"t_uzman", "t_komisyon", "t_denetci", "t_admin", "t_baskan", "t_yonetici"}) {
            al(yetkili, "/api/ihaleler/" + id + "/teklifler").andExpect(status().isOk());
        }
        for (String yetkisiz : new String[] {"t_sorumlu", "t_mudur"}) {
            al(yetkisiz, "/api/ihaleler/" + id + "/teklifler").andExpect(status().isForbidden());
        }
    }

    @Test
    void teklifIstegiYalnizcaSatinAlmaUzmanindanKabulEdilir() throws Exception {
        long id = ilandaIhale();
        for (String yetkisiz : new String[] {"t_sorumlu", "t_komisyon", "t_baskan", "t_denetci"}) {
            gonder(yetkisiz, "/api/ihaleler/" + id + "/teklifler",
                    "{\"istekliAdi\":\"F\",\"vergiNo\":\"1234567890\",\"tutar\":1500000}").andExpect(status().isForbidden());
        }
    }

    // ----------------------------------------------------------- değerlendirme ve karar

    @Test
    void teklifYokkenDegerlendirmeyeGecilemez() throws Exception {
        long id = ilandaIhale();
        saat.ayarla(LocalDate.of(2026, 3, 17));
        gonder("t_komisyon", "/api/ihaleler/" + id + "/degerlendirmeye-al", null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reddedilenTeklifKazananSecilemez() throws Exception {
        long id = ilandaIhale();
        long a = teklifVer(id, "1111111111", "1500000");
        long b = teklifVer(id, "2222222222", "1700000");
        degerlendirmeyeAl(id);
        gonder("t_komisyon", "/api/ihaleler/" + id + "/teklifler/" + a + "/ret", "{\"gerekce\":\"Belgeleri eksik\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("REDDEDILDI"));
        gonder("t_komisyon", "/api/ihaleler/" + id + "/karar", "{\"teklifId\":" + a + "}")
                .andExpect(status().isUnprocessableEntity());
        gonder("t_komisyon", "/api/ihaleler/" + id + "/karar", "{\"teklifId\":" + b + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("KARARLANDI"));
    }

    @Test
    void retGerekcesiZorunludur() throws Exception {
        long id = ilandaIhale();
        long a = teklifVer(id, "1111111111", "1500000");
        degerlendirmeyeAl(id);
        gonder("t_komisyon", "/api/ihaleler/" + id + "/teklifler/" + a + "/ret", "{\"gerekce\":\"\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void baskaIhaleninTeklifiylaIslemYapilamaz() throws Exception {
        long birinci = ilandaIhale();
        long ikinci = ilandaIhale();
        teklifVer(birinci, "1111111111", "1500000");
        long baskaninTeklifi = teklifVer(ikinci, "2222222222", "1500000");
        degerlendirmeyeAl(birinci);
        gonder("t_komisyon", "/api/ihaleler/" + birinci + "/teklifler/" + baskaninTeklifi + "/ret", "{\"gerekce\":\"x\"}")
                .andExpect(status().isUnprocessableEntity());
        gonder("t_komisyon", "/api/ihaleler/" + birinci + "/karar", "{\"teklifId\":" + baskaninTeklifi + "}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void olmayanTeklifIcinKararVerilemez() throws Exception {
        long id = ilandaIhale();
        teklifVer(id, "1111111111", "1500000");
        degerlendirmeyeAl(id);
        gonder("t_komisyon", "/api/ihaleler/" + id + "/karar", "{\"teklifId\":999999}").andExpect(status().isNotFound());
    }

    @Test
    void asiriDusukTeklifAciklamasiKabulEdilinceKazanabilir() throws Exception {
        long id = ilandaIhale(); // yaklaşık maliyet 2.000.000; sınır 1.400.000
        long asiriDusuk = teklifVer(id, "1111111111", "1000000");
        teklifVer(id, "2222222222", "1600000");
        degerlendirmeyeAl(id);
        gonder("t_komisyon", "/api/ihaleler/" + id + "/karar", "{\"teklifId\":" + asiriDusuk + "}")
                .andExpect(status().isUnprocessableEntity()); // açıklama henüz yok
        gonder("t_komisyon", "/api/ihaleler/" + id + "/teklifler/" + asiriDusuk + "/asiri-dusuk-aciklama",
                "{\"karar\":\"KABUL\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.asiriDusukAciklama").value("KABUL"));
        gonder("t_komisyon", "/api/ihaleler/" + id + "/karar", "{\"teklifId\":" + asiriDusuk + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.kazananTeklifId").value(asiriDusuk));
    }

    @Test
    void asiriDusukAciklamasiReddedilenTeklifElenir() throws Exception {
        long id = ilandaIhale();
        long asiriDusuk = teklifVer(id, "1111111111", "1000000");
        long uygun = teklifVer(id, "2222222222", "1600000");
        degerlendirmeyeAl(id);
        gonder("t_komisyon", "/api/ihaleler/" + id + "/teklifler/" + asiriDusuk + "/asiri-dusuk-aciklama",
                "{\"karar\":\"RED\"}").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("REDDEDILDI"));
        gonder("t_komisyon", "/api/ihaleler/" + id + "/karar", "{\"teklifId\":" + uygun + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.kazananTeklifId").value(uygun));
    }

    @Test
    void asiriDusukOlmayanTeklifIcinAciklamaKarariVerilemez() throws Exception {
        long id = ilandaIhale();
        long normal = teklifVer(id, "1111111111", "1600000");
        degerlendirmeyeAl(id);
        gonder("t_komisyon", "/api/ihaleler/" + id + "/teklifler/" + normal + "/asiri-dusuk-aciklama",
                "{\"karar\":\"KABUL\"}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aciklamaKarariYalnizcaKabulVeyaRedOlabilir() throws Exception {
        long id = ilandaIhale();
        long asiriDusuk = teklifVer(id, "1111111111", "1000000");
        degerlendirmeyeAl(id);
        gonder("t_komisyon", "/api/ihaleler/" + id + "/teklifler/" + asiriDusuk + "/asiri-dusuk-aciklama",
                "{\"karar\":\"BELKI\"}").andExpect(status().isBadRequest());
    }

    @Test
    void yaklasikMaliyetiAsanTekliflerdenKazananSecilemez() throws Exception {
        long id = ilandaIhale();
        long asan = teklifVer(id, "1111111111", "2100000");
        degerlendirmeyeAl(id);
        gonder("t_komisyon", "/api/ihaleler/" + id + "/karar", "{\"teklifId\":" + asan + "}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void kararYetkisiYalnizcaKomisyonUyesindedir() throws Exception {
        long id = ilandaIhale();
        long a = teklifVer(id, "1111111111", "1600000");
        degerlendirmeyeAl(id);
        for (String yetkisiz : new String[] {"t_uzman", "t_baskan", "t_yonetici", "t_admin", "t_denetci"}) {
            gonder(yetkisiz, "/api/ihaleler/" + id + "/karar", "{\"teklifId\":" + a + "}").andExpect(status().isForbidden());
        }
    }

    @Test
    void kararaBaglanmisIhaleyeYenidenKararVerilemez() throws Exception {
        long id = ilandaIhale();
        long a = teklifVer(id, "1111111111", "1600000");
        degerlendirmeyeAl(id);
        gonder("t_komisyon", "/api/ihaleler/" + id + "/karar", "{\"teklifId\":" + a + "}").andExpect(status().isOk());
        gonder("t_komisyon", "/api/ihaleler/" + id + "/karar", "{\"teklifId\":" + a + "}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sozlesmeItirazSuresiBittiktenSonraImzalanirVeSonrasindaIptalEdilemez() throws Exception {
        long id = ilandaIhale();
        long a = teklifVer(id, "1111111111", "1600000");
        degerlendirmeyeAl(id);
        gonder("t_komisyon", "/api/ihaleler/" + id + "/karar", "{\"teklifId\":" + a + "}").andExpect(status().isOk());
        saat.ayarla(LocalDate.of(2026, 3, 30));
        gonder("t_uzman", "/api/ihaleler/" + id + "/sozlesme", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("SOZLESME_IMZALANDI"));
        gonder("t_baskan", "/api/ihaleler/" + id + "/iptal", "{\"gerekce\":\"x\"}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void itirazSonTarihiKararGunundenOnGunSonrasidir() throws Exception {
        long id = ilandaIhale();
        long a = teklifVer(id, "1111111111", "1600000");
        degerlendirmeyeAl(id); // 17 Mart
        gonder("t_komisyon", "/api/ihaleler/" + id + "/karar", "{\"teklifId\":" + a + "}")
                .andExpect(jsonPath("$.itirazSonTarihi").value("2026-03-27")); // 17 + 10 = Cuma
    }

    // ---------------------------------------------------------------------- listeleme

    @Test
    void listeDurumFiltresiVeSayfalamaCalisir() throws Exception {
        ihaleOlustur("t_sorumlu", "100000");
        ihaleOlustur("t_sorumlu", "200000");
        long ucuncu = ihaleOlustur("t_sorumlu", "300000");
        gonder("t_sorumlu", "/api/ihaleler/" + ucuncu + "/onaya-gonder", null).andExpect(status().isOk());

        al("t_uzman", "/api/ihaleler?durum=TASLAK&size=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
        al("t_uzman", "/api/ihaleler?size=2&page=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(1));
        al("t_uzman", "/api/ihaleler?durum=ONAY_BEKLIYOR").andExpect(jsonPath("$.content[0].id").value(ucuncu));
    }

    @Test
    void listeEnYeniIhaleyiUsteKoyar() throws Exception {
        long ilk = ihaleOlustur("t_sorumlu", "100000");
        long son = ihaleOlustur("t_sorumlu", "200000");
        assertThat(son).isGreaterThan(ilk);
        al("t_uzman", "/api/ihaleler").andExpect(jsonPath("$.content[0].id").value(son))
                .andExpect(jsonPath("$.content[1].id").value(ilk));
    }

    @Test
    void gecersizDurumFiltresiReddedilir() throws Exception {
        al("t_uzman", "/api/ihaleler?durum=YOK").andExpect(status().isBadRequest());
    }

    @Test
    void satinAlmaUzmaniBirimBelirtmedenIhaleOlusturamaz() throws Exception {
        gonder("t_uzman", "/api/ihaleler", "{\"ad\":\"x\",\"usul\":\"ACIK\",\"tur\":\"MAL\",\"yaklasikMaliyet\":1000}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void birimSorumlusuBirimiSecemezHepKendiBirimineOlusturur() throws Exception {
        String yanit = govde(gonder("t_sorumlu", "/api/ihaleler",
                "{\"ad\":\"x\",\"usul\":\"ACIK\",\"tur\":\"MAL\",\"yaklasikMaliyet\":1000,\"birimId\":" + idari.getId() + "}")
                .andExpect(status().isCreated()));
        assertThat(yanit).contains(bidb.getAd()).doesNotContain(idari.getAd());
    }

    @Test
    void ozetDurumSayilariniVeDevamEdenMaliyetiDoner() throws Exception {
        ihaleOlustur("t_sorumlu", "100000");
        long iptal = ihaleOlustur("t_sorumlu", "50000");
        gonder("t_baskan", "/api/ihaleler/" + iptal + "/iptal", "{\"gerekce\":\"x\"}").andExpect(status().isOk());
        al("t_uzman", "/api/ihaleler/ozet").andExpect(status().isOk())
                .andExpect(jsonPath("$.toplam").value(2))
                .andExpect(jsonPath("$.durumlar.TASLAK").value(1))
                .andExpect(jsonPath("$.durumlar.IPTAL").value(1))
                .andExpect(jsonPath("$.devamEdenYaklasikMaliyet").value(100000.0)); // iptal edilen sayılmaz
    }
}

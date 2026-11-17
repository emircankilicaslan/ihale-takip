package dev.ihaletakip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;

/** Kullanıcı yönetimi, giriş kilidi ve parola değiştirme. */
class KullaniciYonetimTest extends EntegrasyonTemeli {
    private static final String YENI = "Yeni-Parola-2026";

    private String yeniKullanici(String ad, String rol, Long birimId) {
        return """
                {"kullaniciAdi":"%s","adSoyad":"Deneme Kişi","sifre":"%s","rol":"%s","birimId":%s}"""
                .formatted(ad, YENI, rol, birimId);
    }

    private long kimlik(String kullaniciAdi) {
        return kullanicilar.findByKullaniciAdi(kullaniciAdi).orElseThrow().getId();
    }

    // -------------------------------------------------------------- listeleme ve yetki

    @Test
    void yoneticiKullanicilariListeler() throws Exception {
        al("t_admin", "/api/kullanicilar")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.kullaniciAdi == 't_sorumlu')].rol").value("BIRIM_SORUMLUSU"))
                .andExpect(jsonPath("$[?(@.kullaniciAdi == 't_sorumlu')].birim").value("Bilgi İşlem Daire Başkanlığı"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"t_sorumlu", "t_mudur", "t_baskan", "t_yonetici", "t_uzman", "t_komisyon", "t_denetci"})
    void yoneticiOlmayanKullaniciYonetemez(String kullanici) throws Exception {
        al(kullanici, "/api/kullanicilar").andExpect(status().isForbidden());
        gonder(kullanici, "/api/kullanicilar", yeniKullanici("izinsiz", "DENETCI", null))
                .andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------------------- oluşturma

    @Test
    void yoneticiKullaniciOlusturur() throws Exception {
        gonder("t_admin", "/api/kullanicilar", yeniKullanici("yeni.kisi", "SATIN_ALMA_UZMANI", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kullaniciAdi").value("yeni.kisi"))
                .andExpect(jsonPath("$.durum").value("AKTIF"))
                .andExpect(jsonPath("$.kilitli").value(false));
        girisYap("yeni.kisi", YENI).andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("SATIN_ALMA_UZMANI"));
    }

    @Test
    void ayniKullaniciAdiIkinciKezOlusturulamaz() throws Exception {
        gonder("t_admin", "/api/kullanicilar", yeniKullanici("t_uzman", "DENETCI", null))
                .andExpect(status().isUnprocessableEntity());
    }

    @ParameterizedTest
    @ValueSource(strings = {"kisa1", "sadece-harflerden-olusan-parola", "12345678901234"})
    void zayifParolaIleKullaniciOlusturulamaz(String sifre) throws Exception {
        String govde = """
                {"kullaniciAdi":"zayif.parola","adSoyad":"Deneme","sifre":"%s","rol":"DENETCI"}""".formatted(sifre);
        gonder("t_admin", "/api/kullanicilar", govde).andExpect(status().isUnprocessableEntity());
        assertThat(kullanicilar.existsByKullaniciAdi("zayif.parola")).isFalse();
    }

    @Test
    void parolasizKullaniciYerelKipteOlusturulamaz() throws Exception {
        gonder("t_admin", "/api/kullanicilar",
                "{\"kullaniciAdi\":\"parolasiz\",\"adSoyad\":\"Deneme\",\"rol\":\"DENETCI\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BIRIM_SORUMLUSU", "BIRIM_MUDURU"})
    void birimeBagliRoldeBirimZorunludur(String rol) throws Exception {
        gonder("t_admin", "/api/kullanicilar", yeniKullanici("birimsiz", rol, null))
                .andExpect(status().isUnprocessableEntity());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "iki kelime", "büyük-harf-türkçe", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void gecersizKullaniciAdiReddedilir(String ad) throws Exception {
        gonder("t_admin", "/api/kullanicilar", yeniKullanici(ad, "DENETCI", null)).andExpect(status().isBadRequest());
    }

    @Test
    void olmayanBirimIleKullaniciOlusturulamaz() throws Exception {
        gonder("t_admin", "/api/kullanicilar", yeniKullanici("birim.yok", "BIRIM_SORUMLUSU", 999999L))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------------- güncelleme

    @Test
    void rolVeBirimGuncellenir() throws Exception {
        long id = kimlik("t_uzman");
        guncelle("t_admin", "/api/kullanicilar/" + id,
                "{\"rol\":\"BIRIM_SORUMLUSU\",\"birimId\":" + idari.getId() + ",\"durum\":\"AKTIF\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("BIRIM_SORUMLUSU"))
                .andExpect(jsonPath("$.birim").value(idari.getAd()));
    }

    @Test
    void pasifeAlinanKullaniciGirisYapamazVeEskiAnahtariGecersizOlur() throws Exception {
        String eskiAnahtar = giris("t_komisyon");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/ihaleler")
                .header("Authorization", "Bearer " + eskiAnahtar)).andExpect(status().isOk());

        guncelle("t_admin", "/api/kullanicilar/" + kimlik("t_komisyon"),
                "{\"rol\":\"KOMISYON_UYESI\",\"durum\":\"PASIF\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("PASIF"));

        girisYap("t_komisyon", SIFRE).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/ihaleler")
                .header("Authorization", "Bearer " + eskiAnahtar)).andExpect(status().isUnauthorized());
    }

    @Test
    void pasifKullaniciTekrarAktifEdilebilir() throws Exception {
        long id = kimlik("t_denetci");
        guncelle("t_admin", "/api/kullanicilar/" + id, "{\"rol\":\"DENETCI\",\"durum\":\"PASIF\"}").andExpect(status().isOk());
        girisYap("t_denetci", SIFRE).andExpect(status().isUnauthorized());
        guncelle("t_admin", "/api/kullanicilar/" + id, "{\"rol\":\"DENETCI\",\"durum\":\"AKTIF\"}").andExpect(status().isOk());
        girisYap("t_denetci", SIFRE).andExpect(status().isOk());
    }

    @Test
    void yoneticiKendiniPasifeAlamaz() throws Exception {
        guncelle("t_admin", "/api/kullanicilar/" + kimlik("t_admin"), "{\"rol\":\"ADMIN\",\"durum\":\"PASIF\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void yoneticiKendiRolunuDusuremez() throws Exception {
        guncelle("t_admin", "/api/kullanicilar/" + kimlik("t_admin"), "{\"rol\":\"DENETCI\",\"durum\":\"AKTIF\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void olmayanKullaniciGuncellenemez() throws Exception {
        guncelle("t_admin", "/api/kullanicilar/999999", "{\"rol\":\"DENETCI\",\"durum\":\"AKTIF\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void gecersizRolDegeriReddedilir() throws Exception {
        guncelle("t_admin", "/api/kullanicilar/" + kimlik("t_uzman"), "{\"rol\":\"PATRON\",\"durum\":\"AKTIF\"}")
                .andExpect(status().isBadRequest());
    }

    // ----------------------------------------------------------------- giriş kilidi

    private void hataliGirisler(String kullanici, int adet) throws Exception {
        for (int i = 0; i < adet; i++) {
            girisYap(kullanici, "yanlis-parola").andExpect(status().isUnauthorized());
        }
    }

    @Test
    void besHataliGirisHesabiKilitler() throws Exception {
        hataliGirisler("t_uzman", 5);
        // Doğru parola bile artık kabul edilmez
        girisYap("t_uzman", SIFRE).andExpect(status().isLocked());
    }

    @Test
    void dortHataliGirisKilitlemezVeBasariliGirisSayaciSifirlar() throws Exception {
        hataliGirisler("t_uzman", 4);
        girisYap("t_uzman", SIFRE).andExpect(status().isOk());
        hataliGirisler("t_uzman", 4); // sayaç sıfırlandığı için yine kilitlenmez
        girisYap("t_uzman", SIFRE).andExpect(status().isOk());
    }

    @Test
    void kilitSuresiDolunca_girisYenidenCalisir() throws Exception {
        hataliGirisler("t_uzman", 5);
        girisYap("t_uzman", SIFRE).andExpect(status().isLocked());
        saat.ayarla(LocalDate.of(2026, 3, 3)); // 15 dakikadan uzun süre sonra
        girisYap("t_uzman", SIFRE).andExpect(status().isOk());
    }

    @Test
    void yoneticiKilidiAcabilir() throws Exception {
        hataliGirisler("t_uzman", 5);
        gonder("t_admin", "/api/kullanicilar/" + kimlik("t_uzman") + "/kilit-ac", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.kilitli").value(false));
        girisYap("t_uzman", SIFRE).andExpect(status().isOk());
    }

    @Test
    void kilitliHesapListedeGorunur() throws Exception {
        hataliGirisler("t_uzman", 5);
        al("t_admin", "/api/kullanicilar")
                .andExpect(jsonPath("$[?(@.kullaniciAdi == 't_uzman')].kilitli").value(true));
    }

    @Test
    void kilitYalnizcaYoneticiAcar() throws Exception {
        hataliGirisler("t_uzman", 5);
        gonder("t_komisyon", "/api/kullanicilar/" + kimlik("t_uzman") + "/kilit-ac", null)
                .andExpect(status().isForbidden());
    }

    @Test
    void olmayanKullaniciIcinHataliGirisAyniYanitiVerir() throws Exception {
        girisYap("hic.yok", "yanlis-parola").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Kullanıcı adı veya parola hatalı"));
        girisYap("t_uzman", "yanlis-parola").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Kullanıcı adı veya parola hatalı"));
    }

    // ------------------------------------------------------------- parola değiştirme

    private ResultActions parolaDegistir(String kullanici, String mevcut, String yeni) throws Exception {
        return gonder(kullanici, "/api/auth/sifre-degistir",
                "{\"mevcutSifre\":\"%s\",\"yeniSifre\":\"%s\"}".formatted(mevcut, yeni));
    }

    @Test
    void kullaniciParolasiniDegistirir() throws Exception {
        parolaDegistir("t_sorumlu", SIFRE, YENI).andExpect(status().isNoContent());
        girisYap("t_sorumlu", SIFRE).andExpect(status().isUnauthorized());
        girisYap("t_sorumlu", YENI).andExpect(status().isOk());
    }

    @Test
    void mevcutParolaYanlisIseDegistirilemez() throws Exception {
        parolaDegistir("t_sorumlu", "yanlis-parola", YENI).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Mevcut parola hatalı"));
        girisYap("t_sorumlu", SIFRE).andExpect(status().isOk());
    }

    @Test
    void yeniParolaEskisiyleAyniOlamaz() throws Exception {
        parolaDegistir("t_sorumlu", SIFRE, SIFRE).andExpect(status().isUnprocessableEntity());
    }

    @ParameterizedTest
    @ValueSource(strings = {"kisa1", "sadece-harflerden-olusan-parola", "12345678901234"})
    void zayifYeniParolaReddedilir(String yeni) throws Exception {
        parolaDegistir("t_sorumlu", SIFRE, yeni).andExpect(status().isUnprocessableEntity());
        girisYap("t_sorumlu", SIFRE).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ denetim izi

    @Test
    void kullaniciOlaylariDenetimKaydinaYazilir() throws Exception {
        gonder("t_admin", "/api/kullanicilar", yeniKullanici("izlenen", "DENETCI", null)).andExpect(status().isCreated());
        hataliGirisler("t_uzman", 5);
        guncelle("t_admin", "/api/kullanicilar/" + kimlik("t_denetci"), "{\"rol\":\"DENETCI\",\"durum\":\"PASIF\"}");
        parolaDegistir("t_sorumlu", SIFRE, YENI).andExpect(status().isNoContent());

        String kayit = govde(al("t_admin", "/api/denetim?varlikTuru=KULLANICI&size=100").andExpect(status().isOk()));
        assertThat(kayit).contains("KULLANICI_OLUSTUR", "GIRIS_BASARISIZ", "Hesap 15 dakika kilitlendi",
                "KULLANICI_GUNCELLE", "SIFRE_DEGISTIR");
        assertThat(JsonPath.<java.util.List<?>>read(kayit, "$.content[?(@.islem == 'GIRIS_BASARISIZ')]")).hasSize(5);
    }
}

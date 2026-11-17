package dev.ihaletakip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** Excel/CSV dışa aktarma: içerik, yetki kapsamı ve denetim izi. */
class DisaAktarimTest extends EntegrasyonTemeli {

    private byte[] indir(String kullanici, String yol) throws Exception {
        return al(kullanici, yol).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    @Test
    void ihaleListesiCsvOlarakIndirilir() throws Exception {
        ihaleOlustur("t_sorumlu", "2000000");
        var yanit = al("t_uzman", "/api/disa-aktar/ihaleler?bicim=csv")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("ihaleler-2026-03-02.csv")))
                .andReturn().getResponse();
        String icerik = new String(yanit.getContentAsByteArray(), StandardCharsets.UTF_8);

        assertThat(icerik).startsWith("﻿İhale no;Ad;Usul;Tür;Birim;Yaklaşık maliyet (TL);Durum");
        assertThat(icerik).contains("Sunucu alımı", "Açık ihale", "Mal", "2000000.00".replace('.', ','), "Taslak");
    }

    @Test
    void ihaleListesiExcelOlarakIndirilirVeAcilabilir() throws Exception {
        ihaleOlustur("t_sorumlu", "2000000");
        byte[] bayt = indir("t_uzman", "/api/disa-aktar/ihaleler?bicim=xlsx");

        try (var defter = new XSSFWorkbook(new ByteArrayInputStream(bayt))) {
            var sayfa = defter.getSheet("İhaleler");
            assertThat(sayfa).isNotNull();
            assertThat(sayfa.getRow(0).getCell(0).getStringCellValue()).isEqualTo("İhale no");
            assertThat(sayfa.getRow(1).getCell(0).getStringCellValue()).matches("2026/\\d{5}");
            assertThat(sayfa.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Sunucu alımı");
            assertThat(sayfa.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(2_000_000.0);
            assertThat(sayfa.getRow(1).getCell(6).getStringCellValue()).isEqualTo("Taslak");
        }
    }

    @Test
    void excelBicimiVarsayilandir() throws Exception {
        al("t_uzman", "/api/disa-aktar/ihaleler")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("spreadsheetml")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".xlsx")));
    }

    @Test
    void gecersizBicimReddedilir() throws Exception {
        al("t_uzman", "/api/disa-aktar/ihaleler?bicim=pdf").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void durumFiltresiDisaAktarmayaUygulanir() throws Exception {
        ihaleOlustur("t_sorumlu", "100000"); // TASLAK
        String ilandaYok = new String(indir("t_uzman", "/api/disa-aktar/ihaleler?bicim=csv&durum=ILANDA"), StandardCharsets.UTF_8);
        assertThat(ilandaYok).doesNotContain("Sunucu alımı");
        String taslak = new String(indir("t_uzman", "/api/disa-aktar/ihaleler?bicim=csv&durum=TASLAK"), StandardCharsets.UTF_8);
        assertThat(taslak).contains("Sunucu alımı");
    }

    @Test
    void birimSorumlusuYalnizcaKendiBirimininIhalelerinAlir() throws Exception {
        ihaleOlustur("t_sorumlu", "100000");
        String baskaBirim = new String(indir("t_baska_sorumlu", "/api/disa-aktar/ihaleler?bicim=csv"), StandardCharsets.UTF_8);
        assertThat(baskaBirim).doesNotContain("Sunucu alımı");
        String kendi = new String(indir("t_sorumlu", "/api/disa-aktar/ihaleler?bicim=csv"), StandardCharsets.UTF_8);
        assertThat(kendi).contains("Sunucu alımı");
    }

    @Test
    void denetimKaydiYalnizcaYetkiliRollerDisaAktarir() throws Exception {
        for (String yetkisiz : new String[] {"t_sorumlu", "t_mudur", "t_baskan", "t_uzman", "t_komisyon"}) {
            al(yetkisiz, "/api/disa-aktar/denetim?bicim=csv").andExpect(status().isForbidden());
        }
        for (String yetkili : new String[] {"t_denetci", "t_admin", "t_yonetici"}) {
            al(yetkili, "/api/disa-aktar/denetim?bicim=csv").andExpect(status().isOk());
        }
    }

    @Test
    void denetimKaydiIcerigiDisaAktarilir() throws Exception {
        ihaleOlustur("t_sorumlu", "100000");
        String csv = new String(indir("t_denetci", "/api/disa-aktar/denetim?bicim=csv"), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Zaman;Kullanıcı;İşlem;Kayıt türü;Kayıt no;Ayrıntı");
        assertThat(csv).contains("OLUSTUR", "t_sorumlu", "IHALE");
    }

    @Test
    void disaAktarmaDenetimKaydinaYazilir() throws Exception {
        indir("t_uzman", "/api/disa-aktar/ihaleler?bicim=csv");
        String denetim = govde(al("t_denetci", "/api/denetim?size=100").andExpect(status().isOk()));
        assertThat(denetim).contains("DISA_AKTAR", "csv, 0 kayıt");
    }

    @Test
    void oturumsuzDisaAktarmaYapilamaz() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/disa-aktar/ihaleler"))
                .andExpect(status().isUnauthorized());
    }
}

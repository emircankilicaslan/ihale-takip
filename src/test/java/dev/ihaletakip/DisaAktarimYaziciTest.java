package dev.ihaletakip;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ihaletakip.disaaktar.CsvYazici;
import dev.ihaletakip.disaaktar.ExcelYazici;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DisaAktarimYaziciTest {

    private static String csv(List<String> baslik, List<List<String>> satirlar) {
        return new String(CsvYazici.uret(baslik, satirlar), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------------- CSV

    @Test
    void csvBomIleBaslarVeNoktaliVirgulKullanir() {
        String sonuc = csv(List.of("A", "B"), List.of(List.of("1", "2")));
        assertThat(sonuc).isEqualTo("﻿A;B\r\n1;2\r\n");
    }

    @Test
    void csvTurkceKarakterleriKorur() {
        String sonuc = csv(List.of("İhale", "Şehir"), List.of(List.of("Güvenlik hizmeti", "Çankırı")));
        assertThat(sonuc).contains("İhale;Şehir", "Güvenlik hizmeti;Çankırı");
    }

    @Test
    void csvAyiriciIcerenHucreyiTirnaklar() {
        String sonuc = csv(List.of("A"), List.of(List.of("x;y")));
        assertThat(sonuc).contains("\"x;y\"");
    }

    @Test
    void csvTirnakIsaretiniKacirir() {
        String sonuc = csv(List.of("A"), List.of(List.of("o \"harika\" bir ihale")));
        assertThat(sonuc).contains("\"o \"\"harika\"\" bir ihale\"");
    }

    @Test
    void csvSatirSonuIcerenHucreyiTirnaklar() {
        String sonuc = csv(List.of("A"), List.of(List.of("ilk\nikinci")));
        assertThat(sonuc).contains("\"ilk\nikinci\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=SUM(A1:A9)", "+90555", "-1+1", "@KOMUT", "\tsekme"})
    void csvFormulEnjeksiyonunaKarsiBasaKesmeKoyar(String tehlikeli) {
        String sonuc = csv(List.of("A"), List.of(List.of(tehlikeli)));
        assertThat(sonuc).contains("'" + tehlikeli);
    }

    @Test
    void csvNullHucreBosYazilir() {
        String sonuc = csv(List.of("A", "B", "C"), List.of(Arrays.asList("1", null, "3")));
        assertThat(sonuc).contains("1;;3");
    }

    @Test
    void csvBosListedeYalnizcaBaslikYazar() {
        assertThat(csv(List.of("A", "B"), List.of())).isEqualTo("﻿A;B\r\n");
    }

    // ----------------------------------------------------------------------- Excel

    @Test
    void excelDegerTurleriniKorur() throws Exception {
        List<List<Object>> satirlar = List.of(Arrays.asList(
                "Metin", new BigDecimal("1234.50"), 7, LocalDate.of(2026, 3, 17), LocalDateTime.of(2026, 3, 17, 14, 30), null));
        byte[] bayt = ExcelYazici.uret("Deneme", List.of("a", "b", "c", "d", "e", "f"), satirlar);

        try (var defter = new XSSFWorkbook(new ByteArrayInputStream(bayt))) {
            var satir = defter.getSheet("Deneme").getRow(1);
            assertThat(satir.getCell(0).getStringCellValue()).isEqualTo("Metin");
            assertThat(satir.getCell(1).getNumericCellValue()).isEqualTo(1234.5);
            assertThat(satir.getCell(2).getNumericCellValue()).isEqualTo(7.0);
            assertThat(DateUtil.isCellDateFormatted(satir.getCell(3))).isTrue();
            assertThat(satir.getCell(3).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 17));
            assertThat(satir.getCell(4).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 3, 17, 14, 30));
            assertThat(satir.getCell(5).getCellType()).isEqualTo(CellType.BLANK); // boş değer boş hücre olarak yazılır
        }
    }

    @Test
    void excelBaslikSatiriVeSatirSayisiDogrudur() throws Exception {
        List<List<Object>> satirlar = List.of(List.of("x"), List.of("y"), List.of("z"));
        byte[] bayt = ExcelYazici.uret("Sayfa", List.of("Başlık"), satirlar);
        try (var defter = new XSSFWorkbook(new ByteArrayInputStream(bayt))) {
            var sayfa = defter.getSheet("Sayfa");
            assertThat(sayfa.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Başlık");
            assertThat(sayfa.getLastRowNum()).isEqualTo(3);
        }
    }

    @Test
    void excelFormulGibiGorunenMetniFormulYapmaz() throws Exception {
        byte[] bayt = ExcelYazici.uret("Sayfa", List.of("A"), List.of(List.of("=1+1")));
        try (var defter = new XSSFWorkbook(new ByteArrayInputStream(bayt))) {
            var hucre = defter.getSheet("Sayfa").getRow(1).getCell(0);
            assertThat(hucre.getCellType()).isEqualTo(CellType.STRING);
            assertThat(hucre.getStringCellValue()).isEqualTo("=1+1");
        }
    }
}

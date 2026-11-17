package dev.ihaletakip.disaaktar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Tek sayfalı .xlsx üretir. Sütun genişlikleri sabittir (AWT'ye bağımlı otomatik genişlik kullanılmaz). */
public final class ExcelYazici {
    private ExcelYazici() {}

    public static byte[] uret(String sayfaAdi, List<String> basliklar, List<List<Object>> satirlar) {
        try (XSSFWorkbook defter = new XSSFWorkbook(); ByteArrayOutputStream cikti = new ByteArrayOutputStream()) {
            Sheet sayfa = defter.createSheet(sayfaAdi);
            CreationHelper yardimci = defter.getCreationHelper();

            Font kalin = defter.createFont();
            kalin.setBold(true);
            CellStyle baslikStili = defter.createCellStyle();
            baslikStili.setFont(kalin);
            CellStyle paraStili = defter.createCellStyle();
            paraStili.setDataFormat(yardimci.createDataFormat().getFormat("#,##0.00"));
            CellStyle tarihStili = defter.createCellStyle();
            tarihStili.setDataFormat(yardimci.createDataFormat().getFormat("dd.mm.yyyy"));
            CellStyle zamanStili = defter.createCellStyle();
            zamanStili.setDataFormat(yardimci.createDataFormat().getFormat("dd.mm.yyyy hh:mm"));

            Row ust = sayfa.createRow(0);
            for (int i = 0; i < basliklar.size(); i++) {
                var h = ust.createCell(i);
                h.setCellValue(basliklar.get(i));
                h.setCellStyle(baslikStili);
                sayfa.setColumnWidth(i, 256 * 22);
            }
            sayfa.createFreezePane(0, 1);

            int sira = 1;
            for (List<Object> satir : satirlar) {
                Row r = sayfa.createRow(sira++);
                for (int i = 0; i < satir.size(); i++) {
                    Object v = satir.get(i);
                    var h = r.createCell(i);
                    if (v == null) {
                        continue;
                    } else if (v instanceof BigDecimal b) {
                        h.setCellValue(b.doubleValue());
                        h.setCellStyle(paraStili);
                    } else if (v instanceof Number n) {
                        h.setCellValue(n.doubleValue());
                    } else if (v instanceof LocalDate d) {
                        h.setCellValue(d);
                        h.setCellStyle(tarihStili);
                    } else if (v instanceof LocalDateTime z) {
                        h.setCellValue(z);
                        h.setCellStyle(zamanStili);
                    } else {
                        h.setCellValue(v.toString()); // metin olarak yazılır; Excel formül saymaz
                    }
                }
            }
            defter.write(cikti);
            return cikti.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

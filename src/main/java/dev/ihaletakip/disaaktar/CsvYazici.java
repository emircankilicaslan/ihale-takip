package dev.ihaletakip.disaaktar;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Türkçe Excel ile uyumlu CSV: noktalı virgül ayırıcı, UTF-8 BOM. Formül enjeksiyonuna karşı
 * "=", "+", "-", "@" ile başlayan metin hücrelerinin başına kesme işareti konur.
 */
public final class CsvYazici {
    private static final char AYIRICI = ';';
    private static final String BOM = "﻿";

    private CsvYazici() {}

    public static byte[] uret(List<String> basliklar, List<List<String>> satirlar) {
        StringBuilder sb = new StringBuilder(BOM);
        satir(sb, basliklar);
        for (List<String> s : satirlar) {
            satir(sb, s);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String hucre(String deger) {
        if (deger == null) {
            return "";
        }
        String d = deger;
        if (!d.isEmpty() && "=+-@\t\r".indexOf(d.charAt(0)) >= 0) {
            d = "'" + d;
        }
        boolean tirnak = d.indexOf(AYIRICI) >= 0 || d.indexOf('"') >= 0 || d.indexOf('\n') >= 0 || d.indexOf('\r') >= 0;
        if (tirnak) {
            d = "\"" + d.replace("\"", "\"\"") + "\"";
        }
        return d;
    }

    private static void satir(StringBuilder sb, List<String> hucreler) {
        for (int i = 0; i < hucreler.size(); i++) {
            if (i > 0) {
                sb.append(AYIRICI);
            }
            sb.append(hucre(hucreler.get(i)));
        }
        sb.append("\r\n");
    }
}

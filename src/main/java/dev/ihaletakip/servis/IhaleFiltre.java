package dev.ihaletakip.servis;

import dev.ihaletakip.domain.IhaleDurumu;
import dev.ihaletakip.domain.IhaleTuru;
import dev.ihaletakip.domain.IhaleUsulu;
import java.math.BigDecimal;

/**
 * İhale listesi süzgeci. Boş alanlar süzgece katılmaz. {@code ara}, ihale numarasında ve adında
 * büyük/küçük harf duyarsız arar (Türkçe İ/ı eşlemesi veritabanının karşılaştırma kurallarına bağlıdır).
 */
public record IhaleFiltre(String ara, IhaleDurumu durum, IhaleUsulu usul, IhaleTuru tur, Long birimId,
                          BigDecimal minMaliyet, BigDecimal maxMaliyet) {

    public static final IhaleFiltre BOS = new IhaleFiltre(null, null, null, null, null, null, null);

    /** Arama metni en çok bu kadar karakter dikkate alınır. */
    public static final int EN_COK_ARAMA_UZUNLUGU = 100;

    public static IhaleFiltre yalnizDurum(IhaleDurumu durum) {
        return new IhaleFiltre(null, durum, null, null, null, null, null);
    }
}

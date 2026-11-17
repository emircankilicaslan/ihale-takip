package dev.ihaletakip.kural;

import dev.ihaletakip.domain.Teklif;
import dev.ihaletakip.domain.TeklifDurumu;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Tutar ve teklif kuralları. Saf fonksiyonlar: eşikler dışarıdan verilir. */
public final class IhaleKurallari {
    private IhaleKurallari() {}

    /**
     * Yaklaşık maliyete göre gereken onay kademesi: sınır1'e kadar (dahil) 1, sınır2'ye kadar 2,
     * üstü 3. Kademe N, N veya daha yüksek onay seviyesindeki kullanıcı tarafından onaylanır.
     */
    public static int onayKademesi(BigDecimal yaklasikMaliyet, BigDecimal sinir1, BigDecimal sinir2) {
        if (yaklasikMaliyet.compareTo(sinir1) <= 0) {
            return 1;
        }
        if (yaklasikMaliyet.compareTo(sinir2) <= 0) {
            return 2;
        }
        return 3;
    }

    /** Teklif, yaklaşık maliyetin oran katından düşükse aşırı düşük sayılır (açıklama istenir). */
    public static boolean asiriDusukMu(BigDecimal tutar, BigDecimal yaklasikMaliyet, BigDecimal oran) {
        return tutar.compareTo(yaklasikMaliyet.multiply(oran)) < 0;
    }

    /** Karara esas olabilecek teklif: geçerli ve (aşırı düşük değil ya da açıklaması kabul edilmiş). */
    public static boolean uygunMu(Teklif teklif, BigDecimal yaklasikMaliyet, BigDecimal oran) {
        if (teklif.getDurum() != TeklifDurumu.GECERLI) {
            return false;
        }
        if (asiriDusukMu(teklif.getTutar(), yaklasikMaliyet, oran)) {
            return "KABUL".equals(teklif.getAsiriDusukAciklama());
        }
        return true;
    }

    /** Uygun teklifler arasındaki en düşük tutarlı teklif (en düşük fiyat esaslı sadeleştirme). */
    public static Optional<Teklif> enDusukUygunTeklif(List<Teklif> teklifler, BigDecimal yaklasikMaliyet,
                                                      BigDecimal oran) {
        return teklifler.stream()
                .filter(t -> uygunMu(t, yaklasikMaliyet, oran))
                .min(Comparator.comparing(Teklif::getTutar));
    }
}

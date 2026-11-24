package dev.ihaletakip.guvenlik;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * İstemci bazlı, sabit pencereli (dakikalık) istek sayacı. Hesap kilidi tek bir hesabı korur; bu sınır ise
 * hangi hesaba yönelirse yönelsin tek bir istemcinin giriş denemelerini ve genel istek yoğunluğunu sınırlar.
 *
 * <p>Sayaçlar bellektedir: yalnızca tek uygulama örneği için yeterlidir, birden çok örnekte her örnek kendi
 * sayacını tutar. Zaman olarak duvar saati kullanılır; uygulamanın iş saati ({@code Clock}) değil.
 */
@Component
public class IstekHizSiniri {

    public enum Kova { GIRIS, YAZMA, OKUMA }

    /** izinli: istek geçebilir. beklemeSaniye: reddedilirse pencerenin bitmesine kalan süre. */
    public record Karar(boolean izinli, long beklemeSaniye) {}

    private record Pencere(long dakika, int sayi) {}

    private static final long PENCERE_MS = 60_000L;
    private static final int TEMIZLIK_ESIGI = 10_000;

    private final boolean etkin;
    private final boolean guvenilirVekil;
    private final int girisSiniri;
    private final int yazmaSiniri;
    private final int okumaSiniri;
    private final LongSupplier saat;
    private final ConcurrentMap<String, Pencere> sayaclar = new ConcurrentHashMap<>();

    @Autowired
    public IstekHizSiniri(
            @Value("${ihale.hiz-siniri.etkin:true}") boolean etkin,
            @Value("${ihale.hiz-siniri.guvenilir-vekil:false}") boolean guvenilirVekil,
            @Value("${ihale.hiz-siniri.giris-dakika:10}") int girisSiniri,
            @Value("${ihale.hiz-siniri.yazma-dakika:120}") int yazmaSiniri,
            @Value("${ihale.hiz-siniri.okuma-dakika:600}") int okumaSiniri) {
        this(etkin, guvenilirVekil, girisSiniri, yazmaSiniri, okumaSiniri, System::currentTimeMillis);
    }

    IstekHizSiniri(boolean etkin, boolean guvenilirVekil, int girisSiniri, int yazmaSiniri, int okumaSiniri,
            LongSupplier saat) {
        this.etkin = etkin;
        this.guvenilirVekil = guvenilirVekil;
        this.girisSiniri = girisSiniri;
        this.yazmaSiniri = yazmaSiniri;
        this.okumaSiniri = okumaSiniri;
        this.saat = saat;
    }

    public boolean etkin() {
        return etkin;
    }

    /** Arkada güvenilir bir ters vekil varsa istemci adresi X-Forwarded-For başlığındaki son girdidir. */
    public boolean guvenilirVekil() {
        return guvenilirVekil;
    }

    public Karar kontrolEt(String istemci, Kova kova) {
        long simdi = saat.getAsLong();
        long dakika = simdi / PENCERE_MS;
        Pencere guncel = sayaclar.compute(istemci + "|" + kova,
                (anahtar, onceki) -> onceki == null || onceki.dakika() != dakika
                        ? new Pencere(dakika, 1)
                        : new Pencere(dakika, onceki.sayi() + 1));
        if (sayaclar.size() > TEMIZLIK_ESIGI) {
            sayaclar.values().removeIf(p -> p.dakika() < dakika);
        }
        if (guncel.sayi() <= sinir(kova)) {
            return new Karar(true, 0);
        }
        long bekleme = ((dakika + 1) * PENCERE_MS - simdi + 999) / 1000;
        return new Karar(false, Math.max(1, bekleme));
    }

    private int sinir(Kova kova) {
        return switch (kova) {
            case GIRIS -> girisSiniri;
            case YAZMA -> yazmaSiniri;
            case OKUMA -> okumaSiniri;
        };
    }
}

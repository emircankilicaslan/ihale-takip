package dev.ihaletakip.servis;

import dev.ihaletakip.config.IhaleProperties;
import dev.ihaletakip.repo.KullaniciDeposu;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Giriş denemelerinin kaydı ve hesap kilidi. Her yöntem kendi işleminde (transaction) çalışır:
 * hatalı giriş sayısı, ardından istek hata ile bitse bile kalıcı olmalıdır.
 */
@Service
public class GirisServisi {
    private final KullaniciDeposu kullanicilar;
    private final DenetimServisi denetim;
    private final IhaleProperties.Guvenlik guvenlik;
    private final Clock clock;

    public GirisServisi(KullaniciDeposu kullanicilar, DenetimServisi denetim, IhaleProperties props, Clock clock) {
        this.kullanicilar = kullanicilar;
        this.denetim = denetim;
        this.guvenlik = props.guvenlik();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean kilitliMi(String kullaniciAdi) {
        return kullanicilar.findByKullaniciAdi(kullaniciAdi)
                .map(k -> k.kilitliMi(LocalDateTime.now(clock)))
                .orElse(false);
    }

    @Transactional
    public void basarisiz(String kullaniciAdi) {
        kullanicilar.findByKullaniciAdi(kullaniciAdi).ifPresent(k -> {
            LocalDateTime simdi = LocalDateTime.now(clock);
            k.basarisizGirisKaydet(simdi, guvenlik.kilitEsigi(), guvenlik.kilitDakika());
            String not = k.kilitliMi(simdi) ? "Hesap %d dakika kilitlendi".formatted(guvenlik.kilitDakika()) : null;
            denetim.kaydet(kullaniciAdi, "GIRIS_BASARISIZ", "KULLANICI", k.getId(), not);
        });
    }

    @Transactional
    public void basarili(String kullaniciAdi) {
        kullanicilar.findByKullaniciAdi(kullaniciAdi).ifPresent(k -> {
            k.basariliGirisKaydet();
            denetim.kaydet(kullaniciAdi, "GIRIS", "KULLANICI", k.getId(), null);
        });
    }
}

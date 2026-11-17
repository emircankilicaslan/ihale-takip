package dev.ihaletakip;

import dev.ihaletakip.domain.Birim;
import dev.ihaletakip.domain.Kullanici;
import dev.ihaletakip.domain.Rol;
import dev.ihaletakip.repo.BirimDeposu;
import dev.ihaletakip.repo.KullaniciDeposu;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Yalnızca "dev" profilinde çalışır: her rol için bir demo kullanıcı oluşturur.
 * Parola koda gömülü değildir; DEMO_SIFRE ortam değişkeninden okunur.
 */
@Component
@Profile("dev")
public class DemoVeriYukleyici implements CommandLineRunner {
    private final KullaniciDeposu kullanicilar;
    private final BirimDeposu birimler;
    private final PasswordEncoder kodlayici;
    private final String demoSifre;

    public DemoVeriYukleyici(KullaniciDeposu kullanicilar, BirimDeposu birimler, PasswordEncoder kodlayici,
                             @Value("${DEMO_SIFRE:}") String demoSifre) {
        this.kullanicilar = kullanicilar;
        this.birimler = birimler;
        this.kodlayici = kodlayici;
        this.demoSifre = demoSifre;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (demoSifre.isBlank()) {
            throw new IllegalStateException("dev profilinde DEMO_SIFRE ortam değişkeni tanımlanmalıdır");
        }
        if (kullanicilar.count() > 0) {
            return;
        }
        Birim bidb = birimler.findAll().stream().filter(b -> b.getKod().equals("BIDB")).findFirst().orElseThrow();
        String hash = kodlayici.encode(demoSifre);
        kullanicilar.save(new Kullanici("sorumlu", "Ayşe Sorumlu", hash, Rol.BIRIM_SORUMLUSU, bidb));
        kullanicilar.save(new Kullanici("mudur", "Mehmet Müdür", hash, Rol.BIRIM_MUDURU, bidb));
        kullanicilar.save(new Kullanici("baskan", "Zeynep Başkan", hash, Rol.DAIRE_BASKANI, bidb));
        kullanicilar.save(new Kullanici("yonetici", "Ali Yönetici", hash, Rol.UST_YONETICI, null));
        kullanicilar.save(new Kullanici("uzman", "Can Uzman", hash, Rol.SATIN_ALMA_UZMANI, null));
        kullanicilar.save(new Kullanici("komisyon", "Elif Komisyon", hash, Rol.KOMISYON_UYESI, null));
        kullanicilar.save(new Kullanici("denetci", "Deniz Denetçi", hash, Rol.DENETCI, null));
        kullanicilar.save(new Kullanici("admin", "Yönetici Admin", hash, Rol.ADMIN, null));
    }
}

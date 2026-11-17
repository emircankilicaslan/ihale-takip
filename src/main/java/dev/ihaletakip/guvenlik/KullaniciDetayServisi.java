package dev.ihaletakip.guvenlik;

import dev.ihaletakip.repo.KullaniciDeposu;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KullaniciDetayServisi implements UserDetailsService {
    private final KullaniciDeposu kullanicilar;

    public KullaniciDetayServisi(KullaniciDeposu kullanicilar) {
        this.kullanicilar = kullanicilar;
    }

    @Override
    @Transactional(readOnly = true)
    public KullaniciDetay loadUserByUsername(String kullaniciAdi) {
        return kullanicilar.findByKullaniciAdi(kullaniciAdi)
                .map(KullaniciDetay::new)
                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı"));
    }
}

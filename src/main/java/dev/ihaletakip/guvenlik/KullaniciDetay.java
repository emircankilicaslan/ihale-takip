package dev.ihaletakip.guvenlik;

import dev.ihaletakip.domain.Kullanici;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class KullaniciDetay implements UserDetails {
    private final Kullanici kullanici;

    public KullaniciDetay(Kullanici kullanici) {
        this.kullanici = kullanici;
    }

    public Kullanici kullanici() {
        return kullanici;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + kullanici.getRol().name()));
    }

    @Override
    public String getPassword() {
        return kullanici.getSifreHash();
    }

    @Override
    public String getUsername() {
        return kullanici.getKullaniciAdi();
    }

    /** Pasife alınan kullanıcı giriş yapamaz; mevcut oturum anahtarı da geçersiz sayılır. */
    @Override
    public boolean isEnabled() {
        return kullanici.getDurum() == dev.ihaletakip.domain.KullaniciDurumu.AKTIF;
    }
}

package dev.ihaletakip.guvenlik;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/**
 * Kimliği kurumun Active Directory'sine doğrulatır; rol ve birimi uygulamanın kendi
 * kullanıcı tablosundan alır. Dizinde olup uygulamada kaydı olmayan kullanıcı giremez.
 */
public class AdSonrasiYerelRolSaglayici implements AuthenticationProvider {
    private final AuthenticationProvider dizin;
    private final KullaniciDetayServisi kullanicilar;

    public AdSonrasiYerelRolSaglayici(AuthenticationProvider dizin, KullaniciDetayServisi kullanicilar) {
        this.dizin = dizin;
        this.kullanicilar = kullanicilar;
    }

    @Override
    public Authentication authenticate(Authentication girdi) throws AuthenticationException {
        dizin.authenticate(girdi);
        KullaniciDetay detay = kullanicilar.loadUserByUsername(girdi.getName());
        return UsernamePasswordAuthenticationToken.authenticated(detay, null, detay.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> kimlikTuru) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(kimlikTuru);
    }
}

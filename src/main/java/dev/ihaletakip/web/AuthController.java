package dev.ihaletakip.web;

import dev.ihaletakip.guvenlik.JwtServisi;
import dev.ihaletakip.guvenlik.KullaniciDetay;
import dev.ihaletakip.servis.DenetimServisi;
import dev.ihaletakip.web.Dto.GirisIstegi;
import dev.ihaletakip.web.Dto.GirisYaniti;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager kimlikYoneticisi;
    private final JwtServisi jwt;
    private final DenetimServisi denetim;

    public AuthController(AuthenticationManager kimlikYoneticisi, JwtServisi jwt, DenetimServisi denetim) {
        this.kimlikYoneticisi = kimlikYoneticisi;
        this.jwt = jwt;
        this.denetim = denetim;
    }

    @PostMapping("/giris")
    @Transactional
    public GirisYaniti giris(@Valid @RequestBody GirisIstegi istek) {
        try {
            var sonuc = kimlikYoneticisi.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(istek.kullaniciAdi(), istek.sifre()));
            var detay = (KullaniciDetay) sonuc.getPrincipal();
            denetim.kaydet(detay.getUsername(), "GIRIS", "KULLANICI", detay.kullanici().getId(), null);
            return new GirisYaniti(jwt.uret(detay.getUsername()), detay.getUsername(),
                    detay.kullanici().getAdSoyad(), detay.kullanici().getRol());
        } catch (AuthenticationException e) {
            // Neden başarısız olduğu (kullanıcı yok / parola yanlış) dışarı sızdırılmaz.
            throw new BadCredentialsException("Kullanıcı adı veya parola hatalı");
        }
    }

    @GetMapping("/ben")
    public GirisYaniti ben(@AuthenticationPrincipal KullaniciDetay detay) {
        return new GirisYaniti(null, detay.getUsername(), detay.kullanici().getAdSoyad(), detay.kullanici().getRol());
    }
}

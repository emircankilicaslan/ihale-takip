package dev.ihaletakip.web;

import dev.ihaletakip.guvenlik.JwtServisi;
import dev.ihaletakip.guvenlik.KullaniciDetay;
import dev.ihaletakip.servis.Hatalar.HesapKilitli;
import dev.ihaletakip.servis.GirisServisi;
import dev.ihaletakip.servis.KullaniciYonetimServisi;
import dev.ihaletakip.web.Dto.GirisIstegi;
import dev.ihaletakip.web.Dto.GirisYaniti;
import dev.ihaletakip.web.Dto.SifreDegistirIstegi;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Kimlik")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager kimlikYoneticisi;
    private final JwtServisi jwt;
    private final GirisServisi giris;
    private final KullaniciYonetimServisi kullaniciServisi;

    public AuthController(AuthenticationManager kimlikYoneticisi, JwtServisi jwt, GirisServisi giris,
                          KullaniciYonetimServisi kullaniciServisi) {
        this.kimlikYoneticisi = kimlikYoneticisi;
        this.jwt = jwt;
        this.giris = giris;
        this.kullaniciServisi = kullaniciServisi;
    }

    @PostMapping("/giris")
    public GirisYaniti giris(@Valid @RequestBody GirisIstegi istek) {
        String ad = istek.kullaniciAdi();
        if (giris.kilitliMi(ad)) {
            throw new HesapKilitli("Hesap art arda hatalı giriş nedeniyle geçici olarak kilitlendi; "
                    + "bir süre sonra tekrar deneyin veya yöneticiyle iletişime geçin");
        }
        try {
            var sonuc = kimlikYoneticisi.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(ad, istek.sifre()));
            var detay = (KullaniciDetay) sonuc.getPrincipal();
            giris.basarili(detay.getUsername());
            return new GirisYaniti(jwt.uret(detay.getUsername()), detay.getUsername(),
                    detay.kullanici().getAdSoyad(), detay.kullanici().getRol());
        } catch (AuthenticationException e) {
            giris.basarisiz(ad);
            // Neden başarısız olduğu (kullanıcı yok / parola yanlış / pasif) dışarı sızdırılmaz.
            throw new BadCredentialsException("Kullanıcı adı veya parola hatalı");
        }
    }

    @GetMapping("/ben")
    public GirisYaniti ben(@AuthenticationPrincipal KullaniciDetay detay) {
        return new GirisYaniti(null, detay.getUsername(), detay.kullanici().getAdSoyad(), detay.kullanici().getRol());
    }

    @PostMapping("/sifre-degistir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sifreDegistir(@AuthenticationPrincipal KullaniciDetay detay,
                              @Valid @RequestBody SifreDegistirIstegi istek) {
        kullaniciServisi.sifreDegistir(detay.kullanici(), istek.mevcutSifre(), istek.yeniSifre());
    }
}

package dev.ihaletakip.guvenlik;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtFiltresi extends OncePerRequestFilter {
    private final JwtServisi jwt;
    private final KullaniciDetayServisi kullanicilar;

    public JwtFiltresi(JwtServisi jwt, KullaniciDetayServisi kullanicilar) {
        this.jwt = jwt;
        this.kullanicilar = kullanicilar;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest istek, HttpServletResponse yanit, FilterChain zincir)
            throws ServletException, IOException {
        String baslik = istek.getHeader(HttpHeaders.AUTHORIZATION);
        if (baslik != null && baslik.startsWith("Bearer ")) {
            jwt.kullaniciAdi(baslik.substring(7)).ifPresent(ad -> {
                try {
                    // Rol ve birim her istekte veritabanından okunur: yetki değişikliği hemen etkili olur.
                    KullaniciDetay detay = kullanicilar.loadUserByUsername(ad);
                    var kimlik = UsernamePasswordAuthenticationToken.authenticated(
                            detay, null, detay.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(kimlik);
                } catch (UsernameNotFoundException ignored) {
                    // Kullanıcı silinmiş: kimlik kurulmaz, istek 401 alır.
                }
            });
        }
        zincir.doFilter(istek, yanit);
    }
}

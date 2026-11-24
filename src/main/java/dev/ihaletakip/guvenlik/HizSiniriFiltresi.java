package dev.ihaletakip.guvenlik;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /api altındaki isteklere istemci bazlı hız sınırı uygular; aşımda RFC 7807 biçiminde 429 ve
 * {@code Retry-After} döner. Arayüz dosyaları ve sağlık denetimi sınırın dışındadır.
 */
public class HizSiniriFiltresi extends OncePerRequestFilter {
    private static final Set<String> OKUMA_YONTEMLERI = Set.of("GET", "HEAD", "OPTIONS");

    private final IstekHizSiniri sinirlayici;

    public HizSiniriFiltresi(IstekHizSiniri sinirlayici) {
        this.sinirlayici = sinirlayici;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest istek) {
        return !sinirlayici.etkin() || !istek.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest istek, HttpServletResponse yanit, FilterChain zincir)
            throws ServletException, IOException {
        var karar = sinirlayici.kontrolEt(istemciAdresi(istek), kova(istek));
        if (karar.izinli()) {
            zincir.doFilter(istek, yanit);
            return;
        }
        yanit.setStatus(429);
        yanit.setHeader("Retry-After", Long.toString(karar.beklemeSaniye()));
        yanit.setContentType("application/problem+json");
        yanit.setCharacterEncoding(StandardCharsets.UTF_8.name());
        yanit.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"Çok fazla istek gönderildi; " + karar.beklemeSaniye() + " saniye sonra tekrar deneyin\"}");
    }

    static IstekHizSiniri.Kova kova(HttpServletRequest istek) {
        if ("/api/auth/giris".equals(istek.getRequestURI())) {
            return IstekHizSiniri.Kova.GIRIS;
        }
        return OKUMA_YONTEMLERI.contains(istek.getMethod())
                ? IstekHizSiniri.Kova.OKUMA
                : IstekHizSiniri.Kova.YAZMA;
    }

    private String istemciAdresi(HttpServletRequest istek) {
        if (sinirlayici.guvenilirVekil()) {
            String baslik = istek.getHeader("X-Forwarded-For");
            if (baslik != null && !baslik.isBlank()) {
                String[] parcalar = baslik.split(",");
                String son = parcalar[parcalar.length - 1].trim();
                if (!son.isEmpty()) {
                    return son;
                }
            }
        }
        return istek.getRemoteAddr();
    }
}

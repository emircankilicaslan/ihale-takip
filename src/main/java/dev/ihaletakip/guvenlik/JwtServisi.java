package dev.ihaletakip.guvenlik;

import dev.ihaletakip.config.IhaleProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtServisi {
    private final SecretKey anahtar;
    private final Duration gecerlilik;
    private final Clock clock;

    public JwtServisi(IhaleProperties props, Clock clock) {
        String gizli = props.jwt().gizliAnahtar();
        if (gizli == null || gizli.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("ihale.jwt.gizli-anahtar en az 32 bayt olmalıdır (JWT_SECRET)");
        }
        this.anahtar = Keys.hmacShaKeyFor(gizli.getBytes(StandardCharsets.UTF_8));
        this.gecerlilik = Duration.ofMinutes(props.jwt().gecerlilikDakika());
        this.clock = clock;
    }

    public String uret(String kullaniciAdi) {
        Date simdi = Date.from(clock.instant());
        return Jwts.builder()
                .subject(kullaniciAdi)
                .issuedAt(simdi)
                .expiration(Date.from(clock.instant().plus(gecerlilik)))
                .signWith(anahtar)
                .compact();
    }

    /** Geçerli token'ın kullanıcı adını döner; imza veya süre hatalıysa boş. */
    public Optional<String> kullaniciAdi(String token) {
        try {
            var talepler = Jwts.parser()
                    .verifyWith(anahtar)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.ofNullable(talepler.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

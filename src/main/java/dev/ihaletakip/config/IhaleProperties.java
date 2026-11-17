package dev.ihaletakip.config;

import dev.ihaletakip.domain.IhaleUsulu;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Uygulama ayarları. Mevzuata bağlı eşik ve süre değerleri koda gömülü değildir; kurum
 * kendi mevzuat yorumuna göre application.yml üzerinden değiştirir. Varsayılanlar ÖRNEKTİR.
 */
@ConfigurationProperties("ihale")
public record IhaleProperties(Kimlik kimlik, Jwt jwt, Kurallar kurallar, Guvenlik guvenlik) {

    /** Art arda hatalı girişte kilit ve parola politikası. */
    public record Guvenlik(int kilitEsigi, int kilitDakika, int sifreMinUzunluk) {}

    /** mod: "yerel" (kullanıcı adı + parola) veya "ldap" (Active Directory ile kimlik doğrulama). */
    public record Kimlik(String mod, String adDomain, String ldapUrl) {}

    public record Jwt(String gizliAnahtar, long gecerlilikDakika) {}

    public record Kurallar(
            BigDecimal onaySiniri1,
            BigDecimal onaySiniri2,
            BigDecimal asiriDusukOran,
            int itirazSuresiGun,
            Map<IhaleUsulu, Integer> ilanSuresiGun) {}
}

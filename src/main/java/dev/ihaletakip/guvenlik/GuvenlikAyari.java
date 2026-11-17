package dev.ihaletakip.guvenlik;

import dev.ihaletakip.config.IhaleProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class GuvenlikAyari {

    @Bean
    public SecurityFilterChain filtreZinciri(HttpSecurity http, JwtFiltresi jwtFiltresi) throws Exception {
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/giris", "/actuator/health").permitAll()
                        // Arayüz dosyaları herkese açıktır; veri yalnızca /api üzerinden ve oturumla gelir.
                        .requestMatchers("/", "/index.html", "/app.js", "/style.css", "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                .headers(h -> h.contentSecurityPolicy(
                        c -> c.policyDirectives("default-src 'self'; frame-ancestors 'none'; base-uri 'self'")))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtFiltresi, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder sifreKodlayici() {
        return new BCryptPasswordEncoder();
    }

    /** Varsayılan kip: uygulamanın kendi kullanıcı tablosu ve BCrypt parolası. */
    @Bean
    @ConditionalOnProperty(name = "ihale.kimlik.mod", havingValue = "yerel", matchIfMissing = true)
    public AuthenticationManager yerelKimlikYoneticisi(KullaniciDetayServisi kullanicilar, PasswordEncoder kodlayici) {
        DaoAuthenticationProvider saglayici = new DaoAuthenticationProvider();
        saglayici.setUserDetailsService(kullanicilar);
        saglayici.setPasswordEncoder(kodlayici);
        return new ProviderManager(saglayici);
    }

    /** Kurumsal kip: kimlik Active Directory'de doğrulanır, yetki uygulama tablosundan gelir. */
    @Bean
    @ConditionalOnProperty(name = "ihale.kimlik.mod", havingValue = "ldap")
    public AuthenticationManager ldapKimlikYoneticisi(IhaleProperties props, KullaniciDetayServisi kullanicilar) {
        var ad = new ActiveDirectoryLdapAuthenticationProvider(
                props.kimlik().adDomain(), props.kimlik().ldapUrl());
        ad.setConvertSubErrorCodesToExceptions(true);
        return new ProviderManager(new AdSonrasiYerelRolSaglayici(ad, kullanicilar));
    }
}

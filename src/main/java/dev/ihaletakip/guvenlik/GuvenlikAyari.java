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
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Configuration
@EnableMethodSecurity
public class GuvenlikAyari {

    @Bean
    public SecurityFilterChain filtreZinciri(HttpSecurity http, JwtFiltresi jwtFiltresi, IstekHizSiniri hizSiniri) throws Exception {
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/giris", "/actuator/health").permitAll()
                        // Arayüz dosyaları herkese açıktır; veri yalnızca /api üzerinden ve oturumla gelir.
                        .requestMatchers("/", "/index.html", "/app.js", "/style.css", "/favicon.ico").permitAll()
                        // API sözleşmesi (Swagger arayüzü ve OpenAPI belgesi) açıktır; uç noktalar yine oturum ister.
                        .requestMatchers("/swagger", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                // Kendi arayüzümüz sıkı CSP ile sunulur; Swagger arayüzü satır içi stil kullandığı için hariçtir.
                .headers(h -> h.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                        new NegatedRequestMatcher(new OrRequestMatcher(
                                new AntPathRequestMatcher("/swagger"),
                                new AntPathRequestMatcher("/swagger-ui/**"),
                                new AntPathRequestMatcher("/v3/api-docs/**"))),
                        new StaticHeadersWriter("Content-Security-Policy",
                                "default-src 'self'; frame-ancestors 'none'; base-uri 'self'"))))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new HizSiniriFiltresi(hizSiniri), UsernamePasswordAuthenticationFilter.class)
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

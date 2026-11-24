package dev.ihaletakip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Hız sınırı gerçek güvenlik zincirinde uygulanır. Sayaç duvar saatine bağlı olduğundan test, dakika
 * sınırına denk gelme ihtimaline karşı sınırın iki katı kadar deneme yapar; 429 mutlaka görülür.
 */
@TestPropertySource(properties = {
    "ihale.hiz-siniri.giris-dakika=3",
    "ihale.hiz-siniri.okuma-dakika=5"
})
class HizSiniriTest extends EntegrasyonTemeli {

    @Test
    void girisDenemeleriSinirAsilincaTooManyRequestsDoner() throws Exception {
        int durum = 0;
        String retryAfter = null;
        String icerikTuru = null;
        for (int i = 0; i < 6 && durum != 429; i++) {
            var yanit = girisYap("t_kayit", "yanlis-parola").andReturn().getResponse();
            durum = yanit.getStatus();
            retryAfter = yanit.getHeader("Retry-After");
            icerikTuru = yanit.getContentType();
        }
        assertThat(durum).as("sınır aşıldığında 429 dönmeli").isEqualTo(429);
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);
        assertThat(icerikTuru).startsWith("application/problem+json");
    }

    @Test
    void okumaIstekleriSinirAsilincaTooManyRequestsDoner() throws Exception {
        int durum = 0;
        for (int i = 0; i < 10 && durum != 429; i++) {
            durum = mvc.perform(get("/api/ornek")).andReturn().getResponse().getStatus();
        }
        assertThat(durum).as("sınır aşıldığında 429 dönmeli").isEqualTo(429);
    }

    @Test
    void arayuzDosyalariVeSaglikDenetimiSinirdanMuaftir() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus()).isNotEqualTo(429);
            assertThat(mvc.perform(get("/")).andReturn().getResponse().getStatus()).isNotEqualTo(429);
        }
    }
}

package dev.ihaletakip;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ihaletakip.kural.SifreKurali;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SifreKuraliTest {

    @ParameterizedTest
    @ValueSource(strings = {"Yeni-Parola-2026", "abcdefgh12", "12abcdefgh", "Çok-Güçlü-Parola9", "a1b2c3d4e5f6g7"})
    void uygunParolalarKabulEdilir(String sifre) {
        assertThat(SifreKurali.hata(sifre, 10)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"kisa1", "abc123", "ancak9kar"})
    void kisaParolaReddedilir(String sifre) {
        assertThat(SifreKurali.hata(sifre, 10)).hasValueSatisfying(m -> assertThat(m).contains("en az 10"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sadece-harflerden-olusan", "SADECEBUYUKHARFLER", "çğüşöıİ-türkçe-harf"})
    void rakamsizParolaReddedilir(String sifre) {
        assertThat(SifreKurali.hata(sifre, 10)).hasValueSatisfying(m -> assertThat(m).contains("harf ve bir rakam"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678901234", "00000000000", "1234-5678-9012"})
    void harfsizParolaReddedilir(String sifre) {
        assertThat(SifreKurali.hata(sifre, 10)).hasValueSatisfying(m -> assertThat(m).contains("harf ve bir rakam"));
    }

    @ParameterizedTest
    @ValueSource(strings = {" bosluk-basta-1234", "bosluk-sonda-1234 ", " ikisi-de-1234 "})
    void basVeSonBoslukluParolaReddedilir(String sifre) {
        assertThat(SifreKurali.hata(sifre, 10)).hasValueSatisfying(m -> assertThat(m).contains("boşluk"));
    }

    @Test
    void asgariUzunlukAyarlanabilir() {
        assertThat(SifreKurali.hata("abc12", 5)).isEmpty();
        assertThat(SifreKurali.hata("abc1", 5)).isPresent();
        assertThat(SifreKurali.hata("abc12", 6)).hasValueSatisfying(m -> assertThat(m).contains("en az 6"));
    }

    @Test
    void ortadakiBoslukIzinlidir() {
        assertThat(SifreKurali.hata("uzun bir parola 2026", 10)).isEmpty();
    }
}

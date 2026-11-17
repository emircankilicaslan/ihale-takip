package dev.ihaletakip.kural;

import java.util.Optional;

/** Parola politikası: asgari uzunluk, en az bir harf ve bir rakam, baş/son boşluk yok. */
public final class SifreKurali {
    private SifreKurali() {}

    /** Kural ihlali varsa açıklamasını, parola uygunsa boş döner. */
    public static Optional<String> hata(String sifre, int asgariUzunluk) {
        if (sifre == null || sifre.length() < asgariUzunluk) {
            return Optional.of("Parola en az %d karakter olmalıdır".formatted(asgariUzunluk));
        }
        if (!sifre.equals(sifre.strip())) {
            return Optional.of("Parola boşlukla başlayıp bitemez");
        }
        boolean harf = sifre.chars().anyMatch(Character::isLetter);
        boolean rakam = sifre.chars().anyMatch(Character::isDigit);
        if (!harf || !rakam) {
            return Optional.of("Parola en az bir harf ve bir rakam içermelidir");
        }
        return Optional.empty();
    }
}

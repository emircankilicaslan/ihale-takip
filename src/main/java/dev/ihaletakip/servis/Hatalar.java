package dev.ihaletakip.servis;

/** Servis katmanının fırlattığı iş hataları; web katmanı HTTP durum koduna çevirir. */
public final class Hatalar {
    private Hatalar() {}

    /** İş kuralı ihlali: 422. */
    public static class IsKuraliIhlali extends RuntimeException {
        public IsKuraliIhlali(String mesaj) {
            super(mesaj);
        }
    }

    /** Kayıt yok: 404. */
    public static class KayitBulunamadi extends RuntimeException {
        public KayitBulunamadi(String mesaj) {
            super(mesaj);
        }
    }
}

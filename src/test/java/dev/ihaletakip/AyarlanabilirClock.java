package dev.ihaletakip;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Testlerde zamanı ileri almak için değiştirilebilir saat. */
class AyarlanabilirClock extends Clock {
    private final ZoneId bolge;
    private Instant an;

    AyarlanabilirClock(ZoneId bolge, LocalDate gun) {
        this.bolge = bolge;
        ayarla(gun);
    }

    void ayarla(LocalDate gun) {
        this.an = gun.atTime(10, 0).atZone(bolge).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return bolge;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        AyarlanabilirClock kopya = new AyarlanabilirClock(zone, LocalDate.now());
        kopya.an = this.an;
        return kopya;
    }

    @Override
    public Instant instant() {
        return an;
    }
}

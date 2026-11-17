package dev.ihaletakip.kural;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

/** Hafta sonu ve resmi tatil bilgisine göre iş günü hesabı. Saf sınıf: veritabanı bilmez. */
public class IsGunuTakvimi {
    private final Set<LocalDate> tatiller;

    public IsGunuTakvimi(Collection<LocalDate> tatiller) {
        this.tatiller = Set.copyOf(tatiller);
    }

    public boolean isGunu(LocalDate gun) {
        DayOfWeek hafta = gun.getDayOfWeek();
        return hafta != DayOfWeek.SATURDAY && hafta != DayOfWeek.SUNDAY && !tatiller.contains(gun);
    }

    /** Verilen gün iş günüyse kendisi, değilse sonraki ilk iş günü. */
    public LocalDate ilkIsGunu(LocalDate gun) {
        LocalDate aday = gun;
        while (!isGunu(aday)) {
            aday = aday.plusDays(1);
        }
        return aday;
    }

    /** Takvim günü cinsinden süre eklenir; bitiş iş günü değilse ilk iş gününe uzar. */
    public LocalDate sureSonu(LocalDate baslangic, int takvimGunu) {
        return ilkIsGunu(baslangic.plusDays(takvimGunu));
    }

    /** İlan tarihinden asgari ilan süresi geçtikten sonraki en erken ihale günü. */
    public LocalDate enErkenIhaleTarihi(LocalDate ilanTarihi, int asgariIlanGunu) {
        return sureSonu(ilanTarihi, asgariIlanGunu);
    }
}

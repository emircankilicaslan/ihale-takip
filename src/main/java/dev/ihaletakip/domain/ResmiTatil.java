package dev.ihaletakip.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "resmi_tatil")
public class ResmiTatil {
    @Id
    private LocalDate tarih;

    @Column(nullable = false)
    private String ad;

    protected ResmiTatil() {}

    public ResmiTatil(LocalDate tarih, String ad) {
        this.tarih = tarih;
        this.ad = ad;
    }

    public LocalDate getTarih() { return tarih; }
    public String getAd() { return ad; }
}

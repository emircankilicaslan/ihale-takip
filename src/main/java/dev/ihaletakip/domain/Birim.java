package dev.ihaletakip.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "birim")
public class Birim {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String kod;

    @Column(nullable = false)
    private String ad;

    protected Birim() {}

    public Birim(String kod, String ad) {
        this.kod = kod;
        this.ad = ad;
    }

    public Long getId() { return id; }
    public String getKod() { return kod; }
    public String getAd() { return ad; }
}

package dev.ihaletakip.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Yalnızca eklenir; veritabanı tetikleyicisi güncelleme ve silmeyi engeller. */
@Entity
@Table(name = "denetim_kaydi")
public class DenetimKaydi {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime zaman;

    @Column(name = "kullanici_adi", nullable = false, updatable = false)
    private String kullaniciAdi;

    @Column(nullable = false, updatable = false)
    private String islem;

    @Column(name = "varlik_turu", nullable = false, updatable = false)
    private String varlikTuru;

    @Column(name = "varlik_id", updatable = false)
    private Long varlikId;

    @Column(updatable = false, length = 2000)
    private String ayrinti;

    protected DenetimKaydi() {}

    public DenetimKaydi(LocalDateTime zaman, String kullaniciAdi, String islem,
                        String varlikTuru, Long varlikId, String ayrinti) {
        this.zaman = zaman;
        this.kullaniciAdi = kullaniciAdi;
        this.islem = islem;
        this.varlikTuru = varlikTuru;
        this.varlikId = varlikId;
        this.ayrinti = ayrinti;
    }

    public Long getId() { return id; }
    public LocalDateTime getZaman() { return zaman; }
    public String getKullaniciAdi() { return kullaniciAdi; }
    public String getIslem() { return islem; }
    public String getVarlikTuru() { return varlikTuru; }
    public Long getVarlikId() { return varlikId; }
    public String getAyrinti() { return ayrinti; }
}

package dev.ihaletakip.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "kullanici")
public class Kullanici {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kullanici_adi", nullable = false, unique = true)
    private String kullaniciAdi;

    @Column(name = "ad_soyad", nullable = false)
    private String adSoyad;

    /** LDAP kullanıcılarında boş kalır: parola kurumun dizininde tutulur. */
    @Column(name = "sifre_hash")
    private String sifreHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "birim_id")
    private Birim birim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KullaniciDurumu durum = KullaniciDurumu.AKTIF;

    @Column(name = "basarisiz_giris", nullable = false)
    private int basarisizGiris;

    @Column(name = "kilit_bitis")
    private java.time.LocalDateTime kilitBitis;

    protected Kullanici() {}

    public Kullanici(String kullaniciAdi, String adSoyad, String sifreHash, Rol rol, Birim birim) {
        this.kullaniciAdi = kullaniciAdi;
        this.adSoyad = adSoyad;
        this.sifreHash = sifreHash;
        this.rol = rol;
        this.birim = birim;
    }

    public Long getId() { return id; }
    public String getKullaniciAdi() { return kullaniciAdi; }
    public String getAdSoyad() { return adSoyad; }
    public String getSifreHash() { return sifreHash; }
    public Rol getRol() { return rol; }
    public Birim getBirim() { return birim; }
    public KullaniciDurumu getDurum() { return durum; }
    public int getBasarisizGiris() { return basarisizGiris; }
    public java.time.LocalDateTime getKilitBitis() { return kilitBitis; }

    public void rolBelirle(Rol rol, Birim birim) {
        this.rol = rol;
        this.birim = birim;
    }

    public void durumBelirle(KullaniciDurumu durum) {
        this.durum = durum;
    }

    public void sifreBelirle(String sifreHash) {
        this.sifreHash = sifreHash;
    }

    public boolean kilitliMi(java.time.LocalDateTime simdi) {
        return kilitBitis != null && kilitBitis.isAfter(simdi);
    }

    /** Hatalı girişi sayar; eşiğe ulaşılınca hesabı süreli kilitler ve sayacı sıfırlar. */
    public void basarisizGirisKaydet(java.time.LocalDateTime simdi, int esik, int kilitDakika) {
        basarisizGiris++;
        if (basarisizGiris >= esik) {
            kilitBitis = simdi.plusMinutes(kilitDakika);
            basarisizGiris = 0;
        }
    }

    public void basariliGirisKaydet() {
        basarisizGiris = 0;
        kilitBitis = null;
    }
}

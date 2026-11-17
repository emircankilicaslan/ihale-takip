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
}

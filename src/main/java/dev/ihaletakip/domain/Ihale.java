package dev.ihaletakip.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ihale")
public class Ihale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ihale_no", unique = true)
    private String ihaleNo;

    @Column(nullable = false, length = 300)
    private String ad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IhaleUsulu usul;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IhaleTuru tur;

    @Column(name = "yaklasik_maliyet", nullable = false, precision = 19, scale = 2)
    private BigDecimal yaklasikMaliyet;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "birim_id")
    private Birim birim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IhaleDurumu durum = IhaleDurumu.TASLAK;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "olusturan_id")
    private Kullanici olusturan;

    @Column(name = "gerekli_onay_kademesi")
    private Integer gerekliOnayKademesi;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "onaylayan_id")
    private Kullanici onaylayan;

    @Column(name = "onay_zamani")
    private LocalDateTime onayZamani;

    @Column(name = "ilan_tarihi")
    private LocalDate ilanTarihi;

    @Column(name = "ihale_tarihi")
    private LocalDate ihaleTarihi;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "kazanan_teklif_id")
    private Teklif kazananTeklif;

    @Column(name = "karar_zamani")
    private LocalDateTime kararZamani;

    @Column(length = 1000)
    private String gerekce;

    /** İyimser kilit: aynı ihaleyi iki kişi aynı anda ilerletemez. */
    @Version
    private Long surum;

    @Column(name = "olusturma_zamani", nullable = false)
    private LocalDateTime olusturmaZamani;

    protected Ihale() {}

    public Ihale(String ad, IhaleUsulu usul, IhaleTuru tur, BigDecimal yaklasikMaliyet,
                 Birim birim, Kullanici olusturan, LocalDateTime zaman) {
        this.ad = ad;
        this.usul = usul;
        this.tur = tur;
        this.yaklasikMaliyet = yaklasikMaliyet;
        this.birim = birim;
        this.olusturan = olusturan;
        this.olusturmaZamani = zaman;
    }

    public Long getId() { return id; }
    public String getIhaleNo() { return ihaleNo; }
    public void setIhaleNo(String ihaleNo) { this.ihaleNo = ihaleNo; }
    public String getAd() { return ad; }
    public IhaleUsulu getUsul() { return usul; }
    public IhaleTuru getTur() { return tur; }
    public BigDecimal getYaklasikMaliyet() { return yaklasikMaliyet; }
    public Birim getBirim() { return birim; }
    public IhaleDurumu getDurum() { return durum; }
    public void setDurum(IhaleDurumu durum) { this.durum = durum; }
    public Kullanici getOlusturan() { return olusturan; }
    public Integer getGerekliOnayKademesi() { return gerekliOnayKademesi; }
    public void setGerekliOnayKademesi(Integer k) { this.gerekliOnayKademesi = k; }
    public Kullanici getOnaylayan() { return onaylayan; }
    public void onayla(Kullanici k, LocalDateTime zaman) { this.onaylayan = k; this.onayZamani = zaman; }
    public LocalDateTime getOnayZamani() { return onayZamani; }
    public LocalDate getIlanTarihi() { return ilanTarihi; }
    public LocalDate getIhaleTarihi() { return ihaleTarihi; }
    public void ilanBilgisi(LocalDate ilan, LocalDate ihale) { this.ilanTarihi = ilan; this.ihaleTarihi = ihale; }
    public Teklif getKazananTeklif() { return kazananTeklif; }
    public LocalDateTime getKararZamani() { return kararZamani; }
    public void karar(Teklif kazanan, LocalDateTime zaman) { this.kazananTeklif = kazanan; this.kararZamani = zaman; }
    public String getGerekce() { return gerekce; }
    public void setGerekce(String gerekce) { this.gerekce = gerekce; }
    public LocalDateTime getOlusturmaZamani() { return olusturmaZamani; }
}

package dev.ihaletakip.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "teklif")
public class Teklif {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ihale_id")
    private Ihale ihale;

    @Column(name = "istekli_adi", nullable = false)
    private String istekliAdi;

    @Column(name = "vergi_no", nullable = false)
    private String vergiNo;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal tutar;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TeklifDurumu durum = TeklifDurumu.GECERLI;

    @Column(name = "ret_gerekcesi", length = 1000)
    private String retGerekcesi;

    /** Aşırı düşük teklif için komisyon kararı: KABUL veya RED; karar yoksa null. */
    @Column(name = "asiri_dusuk_aciklama")
    private String asiriDusukAciklama;

    @Column(name = "olusturma_zamani", nullable = false)
    private LocalDateTime olusturmaZamani;

    protected Teklif() {}

    public Teklif(Ihale ihale, String istekliAdi, String vergiNo, BigDecimal tutar, LocalDateTime zaman) {
        this.ihale = ihale;
        this.istekliAdi = istekliAdi;
        this.vergiNo = vergiNo;
        this.tutar = tutar;
        this.olusturmaZamani = zaman;
    }

    public Long getId() { return id; }
    public Ihale getIhale() { return ihale; }
    public String getIstekliAdi() { return istekliAdi; }
    public String getVergiNo() { return vergiNo; }
    public BigDecimal getTutar() { return tutar; }
    public TeklifDurumu getDurum() { return durum; }
    public String getRetGerekcesi() { return retGerekcesi; }
    public String getAsiriDusukAciklama() { return asiriDusukAciklama; }

    public void reddet(String gerekce) {
        this.durum = TeklifDurumu.REDDEDILDI;
        this.retGerekcesi = gerekce;
    }

    public void asiriDusukAciklamaKarari(String karar) {
        this.asiriDusukAciklama = karar;
    }
}

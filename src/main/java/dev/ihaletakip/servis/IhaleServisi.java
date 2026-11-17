package dev.ihaletakip.servis;

import dev.ihaletakip.config.IhaleProperties;
import dev.ihaletakip.domain.*;
import dev.ihaletakip.kural.IhaleKurallari;
import dev.ihaletakip.kural.IsGunuTakvimi;
import dev.ihaletakip.repo.*;
import dev.ihaletakip.servis.Hatalar.IsKuraliIhlali;
import dev.ihaletakip.servis.Hatalar.KayitBulunamadi;
import dev.ihaletakip.web.Dto.*;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * İhale yaşam döngüsü: TASLAK → ONAY_BEKLIYOR → ONAYLANDI → ILANDA → DEGERLENDIRMEDE →
 * KARARLANDI → SOZLESME_IMZALANDI (her aşamadan IPTAL'e gidilebilir, son durumlar hariç).
 * Her durum değişikliği denetim kaydına yazılır.
 */
@Service
@Transactional
public class IhaleServisi {
    private static final String VARLIK = "IHALE";

    private final IhaleDeposu ihaleler;
    private final TeklifDeposu teklifler;
    private final BirimDeposu birimler;
    private final KullaniciDeposu kullanicilar;
    private final ResmiTatilDeposu tatiller;
    private final DenetimServisi denetim;
    private final IhaleProperties.Kurallar kurallar;
    private final Clock clock;

    public IhaleServisi(IhaleDeposu ihaleler, TeklifDeposu teklifler, BirimDeposu birimler,
                        KullaniciDeposu kullanicilar, ResmiTatilDeposu tatiller, DenetimServisi denetim,
                        IhaleProperties props, Clock clock) {
        this.ihaleler = ihaleler;
        this.teklifler = teklifler;
        this.birimler = birimler;
        this.kullanicilar = kullanicilar;
        this.tatiller = tatiller;
        this.denetim = denetim;
        this.kurallar = props.kurallar();
        this.clock = clock;
    }

    // ---------------------------------------------------------------- okuma

    @Transactional(readOnly = true)
    public Page<IhaleYaniti> listele(Kullanici k, IhaleDurumu durum, Pageable sayfa) {
        Specification<Ihale> kosul = (kok, sorgu, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (durum != null) {
                p.add(cb.equal(kok.get("durum"), durum));
            }
            if (k.getRol().birimleSinirli()) {
                if (k.getBirim() == null) {
                    p.add(cb.disjunction());
                } else {
                    p.add(cb.equal(kok.get("birim").get("id"), k.getBirim().getId()));
                }
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        IsGunuTakvimi takvim = takvim();
        return ihaleler.findAll(kosul, sayfa).map(i -> yanit(i, takvim));
    }

    @Transactional(readOnly = true)
    public IhaleYaniti getir(Kullanici k, Long id) {
        Ihale i = bul(id);
        birimErisimi(k, i);
        return yanit(i, takvim());
    }

    @Transactional(readOnly = true)
    public List<TeklifYaniti> teklifleriListele(Kullanici k, Long ihaleId) {
        Ihale i = bul(ihaleId);
        birimErisimi(k, i);
        return teklifler.findByIhaleIdOrderByTutarAsc(ihaleId).stream()
                .map(t -> teklifYaniti(t, i))
                .toList();
    }

    // ------------------------------------------------------------ yaşam döngüsü

    public IhaleYaniti olustur(Kullanici k, IhaleOlusturIstegi istek) {
        Birim birim;
        if (k.getRol().birimleSinirli()) {
            birim = k.getBirim();
            if (birim == null) {
                throw new IsKuraliIhlali("Kullanıcının birimi tanımlı değil");
            }
        } else {
            if (istek.birimId() == null) {
                throw new IsKuraliIhlali("birimId zorunludur");
            }
            birim = birimler.findById(istek.birimId())
                    .orElseThrow(() -> new KayitBulunamadi("Birim bulunamadı"));
        }
        if (!kurallar.ilanSuresiGun().containsKey(istek.usul())) {
            throw new IsKuraliIhlali("Bu usul için ilan süresi tanımlı değil: " + istek.usul());
        }
        LocalDateTime simdi = LocalDateTime.now(clock);
        Ihale i = ihaleler.save(new Ihale(istek.ad(), istek.usul(), istek.tur(), istek.yaklasikMaliyet(),
                birim, kullanicilar.getReferenceById(k.getId()), simdi));
        i.setIhaleNo("%d/%05d".formatted(simdi.getYear(), i.getId()));
        denetim.kaydet(k.getKullaniciAdi(), "OLUSTUR", VARLIK, i.getId(),
                "%s, yaklaşık maliyet %s".formatted(i.getAd(), i.getYaklasikMaliyet().toPlainString()));
        return yanit(i, takvim());
    }

    public IhaleYaniti onayaGonder(Kullanici k, Long id) {
        Ihale i = bul(id);
        birimErisimi(k, i);
        durumBekle(i, IhaleDurumu.TASLAK);
        int kademe = IhaleKurallari.onayKademesi(i.getYaklasikMaliyet(),
                kurallar.onaySiniri1(), kurallar.onaySiniri2());
        i.setGerekliOnayKademesi(kademe);
        i.setDurum(IhaleDurumu.ONAY_BEKLIYOR);
        denetim.kaydet(k.getKullaniciAdi(), "ONAYA_GONDER", VARLIK, i.getId(), "Gerekli onay kademesi: " + kademe);
        return yanit(i, takvim());
    }

    public IhaleYaniti onayla(Kullanici k, Long id) {
        Ihale i = bul(id);
        birimErisimi(k, i);
        durumBekle(i, IhaleDurumu.ONAY_BEKLIYOR);
        if (k.getRol().onaySeviyesi() < i.getGerekliOnayKademesi()) {
            throw new AccessDeniedException("Bu ihale için en az %d. kademe onay yetkisi gerekir"
                    .formatted(i.getGerekliOnayKademesi()));
        }
        if (i.getOlusturan().getId().equals(k.getId())) {
            throw new IsKuraliIhlali("Kullanıcı kendi oluşturduğu ihaleyi onaylayamaz");
        }
        i.onayla(kullanicilar.getReferenceById(k.getId()), LocalDateTime.now(clock));
        i.setDurum(IhaleDurumu.ONAYLANDI);
        denetim.kaydet(k.getKullaniciAdi(), "ONAYLA", VARLIK, i.getId(), null);
        return yanit(i, takvim());
    }

    public IhaleYaniti geriCevir(Kullanici k, Long id, String gerekce) {
        Ihale i = bul(id);
        birimErisimi(k, i);
        durumBekle(i, IhaleDurumu.ONAY_BEKLIYOR);
        if (k.getRol().onaySeviyesi() < i.getGerekliOnayKademesi()) {
            throw new AccessDeniedException("Geri çevirme için en az %d. kademe yetkisi gerekir"
                    .formatted(i.getGerekliOnayKademesi()));
        }
        i.setDurum(IhaleDurumu.TASLAK);
        i.setGerekce(gerekce);
        denetim.kaydet(k.getKullaniciAdi(), "GERI_CEVIR", VARLIK, i.getId(), gerekce);
        return yanit(i, takvim());
    }

    public IhaleYaniti ilanEt(Kullanici k, Long id, LocalDate ilanTarihi, LocalDate ihaleTarihi) {
        Ihale i = bul(id);
        durumBekle(i, IhaleDurumu.ONAYLANDI);
        IsGunuTakvimi takvim = takvim();
        int asgari = kurallar.ilanSuresiGun().get(i.getUsul());
        LocalDate enErken = takvim.enErkenIhaleTarihi(ilanTarihi, asgari);
        if (!takvim.isGunu(ihaleTarihi)) {
            throw new IsKuraliIhlali("İhale tarihi iş günü olmalıdır: " + ihaleTarihi);
        }
        if (ihaleTarihi.isBefore(enErken)) {
            throw new IsKuraliIhlali("%s usulünde ilan süresi en az %d gündür; en erken ihale tarihi %s"
                    .formatted(i.getUsul(), asgari, enErken));
        }
        i.ilanBilgisi(ilanTarihi, ihaleTarihi);
        i.setDurum(IhaleDurumu.ILANDA);
        denetim.kaydet(k.getKullaniciAdi(), "ILAN_ET", VARLIK, i.getId(),
                "İlan " + ilanTarihi + ", ihale " + ihaleTarihi);
        return yanit(i, takvim);
    }

    public TeklifYaniti teklifEkle(Kullanici k, Long ihaleId, TeklifIstegi istek) {
        Ihale i = bul(ihaleId);
        durumBekle(i, IhaleDurumu.ILANDA);
        LocalDate bugun = LocalDate.now(clock);
        if (bugun.isBefore(i.getIlanTarihi())) {
            throw new IsKuraliIhlali("İlan tarihinden önce teklif alınamaz");
        }
        if (bugun.isAfter(i.getIhaleTarihi())) {
            throw new IsKuraliIhlali("Teklif verme süresi dolmuştur");
        }
        if (teklifler.existsByIhaleIdAndVergiNo(ihaleId, istek.vergiNo())) {
            throw new IsKuraliIhlali("Bu istekli bu ihaleye zaten teklif vermiştir");
        }
        Teklif t = teklifler.save(new Teklif(i, istek.istekliAdi(), istek.vergiNo(), istek.tutar(),
                LocalDateTime.now(clock)));
        denetim.kaydet(k.getKullaniciAdi(), "TEKLIF_EKLE", "TEKLIF", t.getId(),
                "İhale %s, tutar %s".formatted(i.getIhaleNo(), t.getTutar().toPlainString()));
        return teklifYaniti(t, i);
    }

    public IhaleYaniti degerlendirmeyeAl(Kullanici k, Long id) {
        Ihale i = bul(id);
        durumBekle(i, IhaleDurumu.ILANDA);
        if (LocalDate.now(clock).isBefore(i.getIhaleTarihi())) {
            throw new IsKuraliIhlali("İhale tarihi (%s) gelmeden değerlendirmeye geçilemez".formatted(i.getIhaleTarihi()));
        }
        if (teklifler.findByIhaleIdOrderByTutarAsc(id).isEmpty()) {
            throw new IsKuraliIhlali("Teklif yok; ihale iptal edilmelidir");
        }
        i.setDurum(IhaleDurumu.DEGERLENDIRMEDE);
        denetim.kaydet(k.getKullaniciAdi(), "DEGERLENDIRMEYE_AL", VARLIK, i.getId(), null);
        return yanit(i, takvim());
    }

    public TeklifYaniti teklifReddet(Kullanici k, Long ihaleId, Long teklifId, String gerekce) {
        Ihale i = bul(ihaleId);
        durumBekle(i, IhaleDurumu.DEGERLENDIRMEDE);
        Teklif t = teklifBul(i, teklifId);
        t.reddet(gerekce);
        denetim.kaydet(k.getKullaniciAdi(), "TEKLIF_RET", "TEKLIF", t.getId(), gerekce);
        return teklifYaniti(t, i);
    }

    public TeklifYaniti asiriDusukAciklamaKarari(Kullanici k, Long ihaleId, Long teklifId, String karar) {
        Ihale i = bul(ihaleId);
        durumBekle(i, IhaleDurumu.DEGERLENDIRMEDE);
        Teklif t = teklifBul(i, teklifId);
        if (!IhaleKurallari.asiriDusukMu(t.getTutar(), i.getYaklasikMaliyet(), kurallar.asiriDusukOran())) {
            throw new IsKuraliIhlali("Teklif aşırı düşük değil; açıklama kararı gerekmez");
        }
        t.asiriDusukAciklamaKarari(karar);
        if ("RED".equals(karar)) {
            t.reddet("Aşırı düşük teklif açıklaması uygun bulunmadı");
        }
        denetim.kaydet(k.getKullaniciAdi(), "ASIRI_DUSUK_" + karar, "TEKLIF", t.getId(), null);
        return teklifYaniti(t, i);
    }

    public IhaleYaniti kararVer(Kullanici k, Long ihaleId, Long teklifId) {
        Ihale i = bul(ihaleId);
        durumBekle(i, IhaleDurumu.DEGERLENDIRMEDE);
        Teklif secilen = teklifBul(i, teklifId);
        var oran = kurallar.asiriDusukOran();
        if (!IhaleKurallari.uygunMu(secilen, i.getYaklasikMaliyet(), oran)) {
            throw new IsKuraliIhlali("Teklif geçerli değil ya da aşırı düşük teklifin açıklaması kabul edilmemiş");
        }
        if (secilen.getTutar().compareTo(i.getYaklasikMaliyet()) > 0) {
            throw new IsKuraliIhlali("Teklif yaklaşık maliyeti aşıyor; ihale kararı verilemez");
        }
        Teklif enDusuk = IhaleKurallari.enDusukUygunTeklif(
                teklifler.findByIhaleIdOrderByTutarAsc(ihaleId), i.getYaklasikMaliyet(), oran).orElseThrow();
        if (enDusuk.getTutar().compareTo(secilen.getTutar()) < 0) {
            throw new IsKuraliIhlali("Kazanan, uygun teklifler arasındaki en düşük teklif olmalıdır");
        }
        i.karar(secilen, LocalDateTime.now(clock));
        i.setDurum(IhaleDurumu.KARARLANDI);
        denetim.kaydet(k.getKullaniciAdi(), "KARAR", VARLIK, i.getId(),
                "Kazanan teklif %d, tutar %s".formatted(secilen.getId(), secilen.getTutar().toPlainString()));
        return yanit(i, takvim());
    }

    public IhaleYaniti sozlesmeImzala(Kullanici k, Long id) {
        Ihale i = bul(id);
        durumBekle(i, IhaleDurumu.KARARLANDI);
        IsGunuTakvimi takvim = takvim();
        LocalDate bekleme = itirazSonTarihi(i, takvim);
        if (!LocalDate.now(clock).isAfter(bekleme)) {
            throw new IsKuraliIhlali("İtiraz süresi %s tarihinde bitiyor; bu tarihten sonra sözleşme imzalanabilir"
                    .formatted(bekleme));
        }
        i.setDurum(IhaleDurumu.SOZLESME_IMZALANDI);
        denetim.kaydet(k.getKullaniciAdi(), "SOZLESME_IMZALA", VARLIK, i.getId(), null);
        return yanit(i, takvim);
    }

    public IhaleYaniti iptalEt(Kullanici k, Long id, String gerekce) {
        Ihale i = bul(id);
        if (i.getDurum().sonDurum()) {
            throw new IsKuraliIhlali("Sonlanmış ihale iptal edilemez: " + i.getDurum());
        }
        i.setDurum(IhaleDurumu.IPTAL);
        i.setGerekce(gerekce);
        denetim.kaydet(k.getKullaniciAdi(), "IPTAL", VARLIK, i.getId(), gerekce);
        return yanit(i, takvim());
    }

    public void tatilEkle(Kullanici k, LocalDate tarih, String ad) {
        if (tatiller.existsById(tarih)) {
            throw new IsKuraliIhlali("Bu tarih zaten resmi tatil olarak kayıtlı");
        }
        tatiller.save(new ResmiTatil(tarih, ad));
        denetim.kaydet(k.getKullaniciAdi(), "TATIL_EKLE", "RESMI_TATIL", null, tarih + " " + ad);
    }

    // --------------------------------------------------------------- yardımcılar

    private Ihale bul(Long id) {
        return ihaleler.findById(id).orElseThrow(() -> new KayitBulunamadi("İhale bulunamadı: " + id));
    }

    private Teklif teklifBul(Ihale i, Long teklifId) {
        Teklif t = teklifler.findById(teklifId).orElseThrow(() -> new KayitBulunamadi("Teklif bulunamadı"));
        if (!t.getIhale().getId().equals(i.getId())) {
            throw new IsKuraliIhlali("Teklif bu ihaleye ait değil");
        }
        return t;
    }

    private void durumBekle(Ihale i, IhaleDurumu beklenen) {
        if (i.getDurum() != beklenen) {
            throw new IsKuraliIhlali("İhale %s durumunda olmalıdır; şu an %s".formatted(beklenen, i.getDurum()));
        }
    }

    private void birimErisimi(Kullanici k, Ihale i) {
        if (k.getRol().birimleSinirli()
                && (k.getBirim() == null || !k.getBirim().getId().equals(i.getBirim().getId()))) {
            throw new AccessDeniedException("Başka birimin ihalesine erişilemez");
        }
    }

    private IsGunuTakvimi takvim() {
        return new IsGunuTakvimi(tatiller.findAll().stream().map(ResmiTatil::getTarih).toList());
    }

    private LocalDate itirazSonTarihi(Ihale i, IsGunuTakvimi takvim) {
        return takvim.sureSonu(i.getKararZamani().toLocalDate(), kurallar.itirazSuresiGun());
    }

    private IhaleYaniti yanit(Ihale i, IsGunuTakvimi takvim) {
        LocalDate itiraz = i.getKararZamani() == null ? null : itirazSonTarihi(i, takvim);
        return new IhaleYaniti(i.getId(), i.getIhaleNo(), i.getAd(), i.getUsul(), i.getTur(),
                i.getYaklasikMaliyet(), i.getBirim().getAd(), i.getDurum(), i.getOlusturan().getKullaniciAdi(),
                i.getGerekliOnayKademesi(),
                i.getOnaylayan() == null ? null : i.getOnaylayan().getKullaniciAdi(),
                i.getIlanTarihi(), i.getIhaleTarihi(),
                i.getKazananTeklif() == null ? null : i.getKazananTeklif().getId(),
                i.getKararZamani(), itiraz, i.getGerekce());
    }

    private TeklifYaniti teklifYaniti(Teklif t, Ihale i) {
        return new TeklifYaniti(t.getId(), t.getIstekliAdi(), t.getVergiNo(), t.getTutar(), t.getDurum(),
                IhaleKurallari.asiriDusukMu(t.getTutar(), i.getYaklasikMaliyet(), kurallar.asiriDusukOran()),
                t.getAsiriDusukAciklama(), t.getRetGerekcesi());
    }
}

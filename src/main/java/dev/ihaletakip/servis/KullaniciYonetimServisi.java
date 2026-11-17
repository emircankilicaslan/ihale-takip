package dev.ihaletakip.servis;

import dev.ihaletakip.config.IhaleProperties;
import dev.ihaletakip.domain.Birim;
import dev.ihaletakip.domain.Kullanici;
import dev.ihaletakip.domain.KullaniciDurumu;
import dev.ihaletakip.domain.Rol;
import dev.ihaletakip.kural.SifreKurali;
import dev.ihaletakip.repo.BirimDeposu;
import dev.ihaletakip.repo.KullaniciDeposu;
import dev.ihaletakip.servis.Hatalar.IsKuraliIhlali;
import dev.ihaletakip.servis.Hatalar.KayitBulunamadi;
import dev.ihaletakip.web.Dto.KullaniciGuncelleIstegi;
import dev.ihaletakip.web.Dto.KullaniciOlusturIstegi;
import dev.ihaletakip.web.Dto.KullaniciYaniti;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Yönetici işlemleri (kullanıcı ekleme, rol/birim/durum değişikliği, kilit açma) ve parola değiştirme. */
@Service
@Transactional
public class KullaniciYonetimServisi {
    private static final String VARLIK = "KULLANICI";

    private final KullaniciDeposu kullanicilar;
    private final BirimDeposu birimler;
    private final PasswordEncoder kodlayici;
    private final DenetimServisi denetim;
    private final IhaleProperties props;
    private final Clock clock;

    public KullaniciYonetimServisi(KullaniciDeposu kullanicilar, BirimDeposu birimler, PasswordEncoder kodlayici,
                                   DenetimServisi denetim, IhaleProperties props, Clock clock) {
        this.kullanicilar = kullanicilar;
        this.birimler = birimler;
        this.kodlayici = kodlayici;
        this.denetim = denetim;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<KullaniciYaniti> listele() {
        LocalDateTime simdi = LocalDateTime.now(clock);
        return kullanicilar.findAllByOrderByKullaniciAdiAsc().stream().map(k -> yanit(k, simdi)).toList();
    }

    public KullaniciYaniti olustur(Kullanici yonetici, KullaniciOlusturIstegi istek) {
        if (kullanicilar.existsByKullaniciAdi(istek.kullaniciAdi())) {
            throw new IsKuraliIhlali("Bu kullanıcı adı zaten kullanılıyor");
        }
        Birim birim = birimBul(istek.rol(), istek.birimId());
        String hash = null;
        boolean dizinKipi = "ldap".equals(props.kimlik().mod());
        if (istek.sifre() != null && !istek.sifre().isBlank()) {
            SifreKurali.hata(istek.sifre(), props.guvenlik().sifreMinUzunluk()).ifPresent(m -> {
                throw new IsKuraliIhlali(m);
            });
            hash = kodlayici.encode(istek.sifre());
        } else if (!dizinKipi) {
            throw new IsKuraliIhlali("Parola zorunludur");
        }
        Kullanici k = kullanicilar.save(new Kullanici(istek.kullaniciAdi(), istek.adSoyad(), hash, istek.rol(), birim));
        denetim.kaydet(yonetici.getKullaniciAdi(), "KULLANICI_OLUSTUR", VARLIK, k.getId(),
                "%s, rol %s".formatted(k.getKullaniciAdi(), k.getRol()));
        return yanit(k, LocalDateTime.now(clock));
    }

    public KullaniciYaniti guncelle(Kullanici yonetici, Long id, KullaniciGuncelleIstegi istek) {
        Kullanici k = bul(id);
        if (k.getId().equals(yonetici.getId())
                && (istek.durum() == KullaniciDurumu.PASIF || istek.rol() != Rol.ADMIN)) {
            throw new IsKuraliIhlali("Yönetici kendi hesabını pasife alamaz veya rolünü düşüremez");
        }
        k.rolBelirle(istek.rol(), birimBul(istek.rol(), istek.birimId()));
        k.durumBelirle(istek.durum());
        denetim.kaydet(yonetici.getKullaniciAdi(), "KULLANICI_GUNCELLE", VARLIK, k.getId(),
                "%s: rol %s, durum %s".formatted(k.getKullaniciAdi(), k.getRol(), k.getDurum()));
        return yanit(k, LocalDateTime.now(clock));
    }

    public KullaniciYaniti kilitAc(Kullanici yonetici, Long id) {
        Kullanici k = bul(id);
        k.basariliGirisKaydet();
        denetim.kaydet(yonetici.getKullaniciAdi(), "KILIT_AC", VARLIK, k.getId(), k.getKullaniciAdi());
        return yanit(k, LocalDateTime.now(clock));
    }

    public void sifreDegistir(Kullanici oturumAcan, String mevcutSifre, String yeniSifre) {
        Kullanici k = bul(oturumAcan.getId());
        if (k.getSifreHash() == null) {
            throw new IsKuraliIhlali("Bu hesabın parolası kurumsal dizinde yönetilir");
        }
        if (!kodlayici.matches(mevcutSifre, k.getSifreHash())) {
            throw new IsKuraliIhlali("Mevcut parola hatalı");
        }
        if (mevcutSifre.equals(yeniSifre)) {
            throw new IsKuraliIhlali("Yeni parola mevcut paroladan farklı olmalıdır");
        }
        SifreKurali.hata(yeniSifre, props.guvenlik().sifreMinUzunluk()).ifPresent(m -> {
            throw new IsKuraliIhlali(m);
        });
        k.sifreBelirle(kodlayici.encode(yeniSifre));
        denetim.kaydet(k.getKullaniciAdi(), "SIFRE_DEGISTIR", VARLIK, k.getId(), null);
    }

    // ------------------------------------------------------------ yardımcılar

    private Kullanici bul(Long id) {
        return kullanicilar.findById(id).orElseThrow(() -> new KayitBulunamadi("Kullanıcı bulunamadı: " + id));
    }

    private Birim birimBul(Rol rol, Long birimId) {
        if (birimId == null) {
            if (rol.birimleSinirli()) {
                throw new IsKuraliIhlali("%s rolü için birim zorunludur".formatted(rol));
            }
            return null;
        }
        return birimler.findById(birimId).orElseThrow(() -> new KayitBulunamadi("Birim bulunamadı"));
    }

    private KullaniciYaniti yanit(Kullanici k, LocalDateTime simdi) {
        return new KullaniciYaniti(k.getId(), k.getKullaniciAdi(), k.getAdSoyad(), k.getRol(),
                k.getBirim() == null ? null : k.getBirim().getAd(), k.getDurum(), k.kilitliMi(simdi));
    }
}

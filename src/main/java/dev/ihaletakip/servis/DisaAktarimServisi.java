package dev.ihaletakip.servis;

import dev.ihaletakip.disaaktar.CsvYazici;
import dev.ihaletakip.disaaktar.ExcelYazici;
import dev.ihaletakip.domain.IhaleDurumu;
import dev.ihaletakip.domain.IhaleTuru;
import dev.ihaletakip.domain.IhaleUsulu;
import dev.ihaletakip.domain.Kullanici;
import dev.ihaletakip.servis.Hatalar.IsKuraliIhlali;
import dev.ihaletakip.web.Dto.IhaleYaniti;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** İhale listesi ve denetim kaydını Excel veya CSV olarak üretir. Her dışa aktarma denetim kaydına yazılır. */
@Service
public class DisaAktarimServisi {
    /** Tek dosyadaki en çok satır; daha fazlası için süzgeç kullanılmalıdır. */
    public static final int EN_COK_SATIR = 10_000;

    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String CSV = "text/csv; charset=UTF-8";

    public record Dosya(byte[] icerik, String ad, String icerikTuru) {}

    private static final Map<IhaleDurumu, String> DURUM = Map.of(
            IhaleDurumu.TASLAK, "Taslak",
            IhaleDurumu.ONAY_BEKLIYOR, "Onay bekliyor",
            IhaleDurumu.ONAYLANDI, "Onaylandı",
            IhaleDurumu.ILANDA, "İlanda",
            IhaleDurumu.DEGERLENDIRMEDE, "Değerlendirmede",
            IhaleDurumu.KARARLANDI, "Karara bağlandı",
            IhaleDurumu.SOZLESME_IMZALANDI, "Sözleşme imzalandı",
            IhaleDurumu.IPTAL, "İptal");
    private static final Map<IhaleUsulu, String> USUL = Map.of(
            IhaleUsulu.ACIK, "Açık ihale",
            IhaleUsulu.BELLI_ISTEKLILER, "Belli istekliler arasında",
            IhaleUsulu.PAZARLIK, "Pazarlık");
    private static final Map<IhaleTuru, String> TUR = Map.of(
            IhaleTuru.MAL, "Mal", IhaleTuru.HIZMET, "Hizmet", IhaleTuru.YAPIM, "Yapım");

    private final IhaleServisi ihaleler;
    private final DenetimServisi denetim;
    private final Clock clock;

    public DisaAktarimServisi(IhaleServisi ihaleler, DenetimServisi denetim, Clock clock) {
        this.ihaleler = ihaleler;
        this.denetim = denetim;
        this.clock = clock;
    }

    public Dosya ihaleler(Kullanici k, IhaleDurumu durum, String bicim) {
        var sayfa = PageRequest.of(0, EN_COK_SATIR, Sort.by(Sort.Direction.DESC, "id"));
        List<IhaleYaniti> liste = ihaleler.listele(k, durum, sayfa).getContent();

        List<String> baslik = List.of("İhale no", "Ad", "Usul", "Tür", "Birim", "Yaklaşık maliyet (TL)", "Durum",
                "Onay kademesi", "İlan tarihi", "İhale tarihi", "Oluşturan", "Onaylayan", "Karar zamanı");
        List<List<Object>> satirlar = new ArrayList<>();
        for (IhaleYaniti i : liste) {
            satirlar.add(java.util.Arrays.asList(i.ihaleNo(), i.ad(), USUL.get(i.usul()), TUR.get(i.tur()), i.birim(),
                    i.yaklasikMaliyet(), DURUM.get(i.durum()), i.gerekliOnayKademesi(), i.ilanTarihi(),
                    i.ihaleTarihi(), i.olusturan(), i.onaylayan(), i.kararZamani()));
        }
        Dosya d = uret("ihaleler", "İhaleler", baslik, satirlar, bicim);
        denetim.kaydet(k.getKullaniciAdi(), "DISA_AKTAR", "IHALE", null,
                "%s, %d kayıt".formatted(bicim, liste.size()));
        return d;
    }

    public Dosya denetim(Kullanici k, String bicim) {
        var sayfa = PageRequest.of(0, EN_COK_SATIR, Sort.by(Sort.Direction.DESC, "id"));
        var kayitlar = denetim.listele(null, null, sayfa).getContent();

        List<String> baslik = List.of("Zaman", "Kullanıcı", "İşlem", "Kayıt türü", "Kayıt no", "Ayrıntı");
        List<List<Object>> satirlar = new ArrayList<>();
        for (var kayit : kayitlar) {
            satirlar.add(java.util.Arrays.asList(kayit.getZaman(), kayit.getKullaniciAdi(), kayit.getIslem(),
                    kayit.getVarlikTuru(), kayit.getVarlikId(), kayit.getAyrinti()));
        }
        Dosya d = uret("denetim-kaydi", "Denetim kaydı", baslik, satirlar, bicim);
        denetim.kaydet(k.getKullaniciAdi(), "DISA_AKTAR", "DENETIM_KAYDI", null,
                "%s, %d kayıt".formatted(bicim, kayitlar.size()));
        return d;
    }

    private Dosya uret(String dosyaAdi, String sayfaAdi, List<String> baslik, List<List<Object>> satirlar, String bicim) {
        String gun = LocalDate.now(clock).toString();
        return switch (bicim == null ? "" : bicim.toLowerCase()) {
            case "xlsx" -> new Dosya(ExcelYazici.uret(sayfaAdi, baslik, satirlar), dosyaAdi + "-" + gun + ".xlsx", XLSX);
            case "csv" -> new Dosya(CsvYazici.uret(baslik, metneCevir(satirlar)), dosyaAdi + "-" + gun + ".csv", CSV);
            default -> throw new IsKuraliIhlali("Biçim xlsx veya csv olmalıdır");
        };
    }

    /** CSV için hücreler metne çevrilir; tutarlarda Türkçe Excel'in beklediği ondalık virgül kullanılır. */
    private static List<List<String>> metneCevir(List<List<Object>> satirlar) {
        List<List<String>> sonuc = new ArrayList<>();
        for (List<Object> satir : satirlar) {
            List<String> s = new ArrayList<>();
            for (Object v : satir) {
                if (v == null) {
                    s.add("");
                } else if (v instanceof java.math.BigDecimal b) {
                    s.add(b.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',')); // her zaman iki ondalık
                } else if (v instanceof LocalDate d) {
                    s.add("%02d.%02d.%d".formatted(d.getDayOfMonth(), d.getMonthValue(), d.getYear()));
                } else if (v instanceof java.time.LocalDateTime z) {
                    s.add("%02d.%02d.%d %02d:%02d".formatted(z.getDayOfMonth(), z.getMonthValue(), z.getYear(),
                            z.getHour(), z.getMinute()));
                } else {
                    s.add(v.toString());
                }
            }
            sonuc.add(s);
        }
        return sonuc;
    }
}

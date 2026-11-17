package dev.ihaletakip.web;

import dev.ihaletakip.domain.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** İstek ve yanıt modelleri. */
public final class Dto {
    private Dto() {}

    public record GirisIstegi(@NotBlank String kullaniciAdi, @NotBlank String sifre) {}

    public record GirisYaniti(String token, String kullaniciAdi, String adSoyad, Rol rol) {}

    public record SifreDegistirIstegi(@NotBlank String mevcutSifre, @NotBlank @Size(max = 200) String yeniSifre) {}

    public record KullaniciOlusturIstegi(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,100}",
                    message = "3-100 karakter; harf, rakam, nokta, alt çizgi ve tire kullanılabilir") String kullaniciAdi,
            @NotBlank @Size(max = 200) String adSoyad,
            @Size(max = 200) String sifre,
            @NotNull Rol rol,
            Long birimId) {}

    public record KullaniciGuncelleIstegi(@NotNull Rol rol, Long birimId, @NotNull KullaniciDurumu durum) {}

    public record KullaniciYaniti(
            Long id, String kullaniciAdi, String adSoyad, Rol rol, String birim, KullaniciDurumu durum, boolean kilitli) {}

    public record IhaleOlusturIstegi(
            @NotBlank @Size(max = 300) String ad,
            @NotNull IhaleUsulu usul,
            @NotNull IhaleTuru tur,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal yaklasikMaliyet,
            Long birimId) {}

    public record IlanIstegi(@NotNull LocalDate ilanTarihi, @NotNull LocalDate ihaleTarihi) {}

    public record GerekceIstegi(@NotBlank @Size(max = 1000) String gerekce) {}

    public record TeklifIstegi(
            @NotBlank @Size(max = 200) String istekliAdi,
            @NotBlank @Pattern(regexp = "\\d{10,11}", message = "Vergi/TC kimlik no 10 veya 11 rakam olmalıdır") String vergiNo,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal tutar) {}

    public record AciklamaKarariIstegi(@NotBlank @Pattern(regexp = "KABUL|RED") String karar) {}

    public record KararIstegi(@NotNull Long teklifId) {}

    public record TatilIstegi(@NotNull LocalDate tarih, @NotBlank @Size(max = 100) String ad) {}

    public record IhaleYaniti(
            Long id,
            String ihaleNo,
            String ad,
            IhaleUsulu usul,
            IhaleTuru tur,
            BigDecimal yaklasikMaliyet,
            String birim,
            IhaleDurumu durum,
            String olusturan,
            Integer gerekliOnayKademesi,
            String onaylayan,
            LocalDate ilanTarihi,
            LocalDate ihaleTarihi,
            Long kazananTeklifId,
            LocalDateTime kararZamani,
            LocalDate itirazSonTarihi,
            String gerekce) {}

    public record OzetYaniti(long toplam, java.util.Map<IhaleDurumu, Long> durumlar, BigDecimal devamEdenYaklasikMaliyet) {}

    public record TeklifYaniti(
            Long id,
            String istekliAdi,
            String vergiNo,
            BigDecimal tutar,
            TeklifDurumu durum,
            boolean asiriDusuk,
            String asiriDusukAciklama,
            String retGerekcesi) {}

    public record DenetimYaniti(
            Long id, LocalDateTime zaman, String kullaniciAdi, String islem,
            String varlikTuru, Long varlikId, String ayrinti) {

        public static DenetimYaniti of(DenetimKaydi k) {
            return new DenetimYaniti(k.getId(), k.getZaman(), k.getKullaniciAdi(), k.getIslem(),
                    k.getVarlikTuru(), k.getVarlikId(), k.getAyrinti());
        }
    }
}

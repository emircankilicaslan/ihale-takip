package dev.ihaletakip.web;

import dev.ihaletakip.domain.IhaleDurumu;
import dev.ihaletakip.guvenlik.KullaniciDetay;
import dev.ihaletakip.servis.IhaleServisi;
import dev.ihaletakip.web.Dto.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "İhaleler")
@RestController
@RequestMapping("/api/ihaleler")
public class IhaleController {
    private final IhaleServisi servis;

    public IhaleController(IhaleServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    public Page<IhaleYaniti> listele(@AuthenticationPrincipal KullaniciDetay k,
                                     @RequestParam(required = false) IhaleDurumu durum,
                                     @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable sayfa) {
        return servis.listele(k.kullanici(), durum, sayfa);
    }

    @GetMapping("/ozet")
    public OzetYaniti ozet(@AuthenticationPrincipal KullaniciDetay k) {
        return servis.ozet(k.kullanici());
    }

    @GetMapping("/{id}")
    public IhaleYaniti getir(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id) {
        return servis.getir(k.kullanici(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('BIRIM_SORUMLUSU','SATIN_ALMA_UZMANI')")
    public IhaleYaniti olustur(@AuthenticationPrincipal KullaniciDetay k, @Valid @RequestBody IhaleOlusturIstegi istek) {
        return servis.olustur(k.kullanici(), istek);
    }

    @PostMapping("/{id}/onaya-gonder")
    @PreAuthorize("hasAnyRole('BIRIM_SORUMLUSU','SATIN_ALMA_UZMANI')")
    public IhaleYaniti onayaGonder(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id) {
        return servis.onayaGonder(k.kullanici(), id);
    }

    @PostMapping("/{id}/onayla")
    @PreAuthorize("hasAnyRole('BIRIM_MUDURU','DAIRE_BASKANI','UST_YONETICI')")
    public IhaleYaniti onayla(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id) {
        return servis.onayla(k.kullanici(), id);
    }

    @PostMapping("/{id}/geri-cevir")
    @PreAuthorize("hasAnyRole('BIRIM_MUDURU','DAIRE_BASKANI','UST_YONETICI')")
    public IhaleYaniti geriCevir(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                                 @Valid @RequestBody GerekceIstegi istek) {
        return servis.geriCevir(k.kullanici(), id, istek.gerekce());
    }

    @PostMapping("/{id}/ilan")
    @PreAuthorize("hasRole('SATIN_ALMA_UZMANI')")
    public IhaleYaniti ilanEt(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                              @Valid @RequestBody IlanIstegi istek) {
        return servis.ilanEt(k.kullanici(), id, istek.ilanTarihi(), istek.ihaleTarihi());
    }

    @GetMapping("/{id}/teklifler")
    @PreAuthorize("hasAnyRole('SATIN_ALMA_UZMANI','KOMISYON_UYESI','DENETCI','ADMIN','UST_YONETICI','DAIRE_BASKANI')")
    public List<TeklifYaniti> teklifler(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id) {
        return servis.teklifleriListele(k.kullanici(), id);
    }

    @PostMapping("/{id}/teklifler")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SATIN_ALMA_UZMANI')")
    public TeklifYaniti teklifEkle(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                                   @Valid @RequestBody TeklifIstegi istek) {
        return servis.teklifEkle(k.kullanici(), id, istek);
    }

    @PostMapping("/{id}/degerlendirmeye-al")
    @PreAuthorize("hasAnyRole('SATIN_ALMA_UZMANI','KOMISYON_UYESI')")
    public IhaleYaniti degerlendirmeyeAl(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id) {
        return servis.degerlendirmeyeAl(k.kullanici(), id);
    }

    @PostMapping("/{id}/teklifler/{teklifId}/ret")
    @PreAuthorize("hasRole('KOMISYON_UYESI')")
    public TeklifYaniti teklifReddet(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                                     @PathVariable Long teklifId, @Valid @RequestBody GerekceIstegi istek) {
        return servis.teklifReddet(k.kullanici(), id, teklifId, istek.gerekce());
    }

    @PostMapping("/{id}/teklifler/{teklifId}/asiri-dusuk-aciklama")
    @PreAuthorize("hasRole('KOMISYON_UYESI')")
    public TeklifYaniti asiriDusuk(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                                   @PathVariable Long teklifId, @Valid @RequestBody AciklamaKarariIstegi istek) {
        return servis.asiriDusukAciklamaKarari(k.kullanici(), id, teklifId, istek.karar());
    }

    @PostMapping("/{id}/karar")
    @PreAuthorize("hasRole('KOMISYON_UYESI')")
    public IhaleYaniti karar(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                             @Valid @RequestBody KararIstegi istek) {
        return servis.kararVer(k.kullanici(), id, istek.teklifId());
    }

    @PostMapping("/{id}/sozlesme")
    @PreAuthorize("hasRole('SATIN_ALMA_UZMANI')")
    public IhaleYaniti sozlesme(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id) {
        return servis.sozlesmeImzala(k.kullanici(), id);
    }

    @PostMapping("/{id}/iptal")
    @PreAuthorize("hasAnyRole('DAIRE_BASKANI','UST_YONETICI')")
    public IhaleYaniti iptal(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                             @Valid @RequestBody GerekceIstegi istek) {
        return servis.iptalEt(k.kullanici(), id, istek.gerekce());
    }
}

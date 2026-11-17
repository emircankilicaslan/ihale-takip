package dev.ihaletakip.web;

import dev.ihaletakip.guvenlik.KullaniciDetay;
import dev.ihaletakip.servis.DenetimServisi;
import dev.ihaletakip.servis.IhaleServisi;
import dev.ihaletakip.web.Dto.DenetimYaniti;
import dev.ihaletakip.web.Dto.TatilIstegi;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class YonetimController {
    private final DenetimServisi denetim;
    private final IhaleServisi ihaleler;

    public YonetimController(DenetimServisi denetim, IhaleServisi ihaleler) {
        this.denetim = denetim;
        this.ihaleler = ihaleler;
    }

    /** Denetim kaydı yalnızca okunur; ekleme/değiştirme/silme uç noktası yoktur. */
    @GetMapping("/denetim")
    @PreAuthorize("hasAnyRole('DENETCI','ADMIN','UST_YONETICI')")
    public Page<DenetimYaniti> denetim(@RequestParam(required = false) String varlikTuru,
                                       @RequestParam(required = false) Long varlikId,
                                       @PageableDefault(size = 50, sort = "id", direction = Sort.Direction.DESC) Pageable sayfa) {
        return denetim.listele(varlikTuru, varlikId, sayfa).map(DenetimYaniti::of);
    }

    @PostMapping("/resmi-tatiller")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public void tatilEkle(@AuthenticationPrincipal KullaniciDetay k, @Valid @RequestBody TatilIstegi istek) {
        ihaleler.tatilEkle(k.kullanici(), istek.tarih(), istek.ad());
    }
}

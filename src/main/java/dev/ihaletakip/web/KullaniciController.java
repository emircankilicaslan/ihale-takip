package dev.ihaletakip.web;

import dev.ihaletakip.guvenlik.KullaniciDetay;
import dev.ihaletakip.servis.KullaniciYonetimServisi;
import dev.ihaletakip.web.Dto.KullaniciGuncelleIstegi;
import dev.ihaletakip.web.Dto.KullaniciOlusturIstegi;
import dev.ihaletakip.web.Dto.KullaniciYaniti;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Kullanıcı yönetimi yalnızca yöneticiye açıktır. */
@io.swagger.v3.oas.annotations.tags.Tag(name = "Kullanıcı yönetimi")
@RestController
@RequestMapping("/api/kullanicilar")
@PreAuthorize("hasRole('ADMIN')")
public class KullaniciController {
    private final KullaniciYonetimServisi servis;

    public KullaniciController(KullaniciYonetimServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    public List<KullaniciYaniti> listele() {
        return servis.listele();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KullaniciYaniti olustur(@AuthenticationPrincipal KullaniciDetay k,
                                   @Valid @RequestBody KullaniciOlusturIstegi istek) {
        return servis.olustur(k.kullanici(), istek);
    }

    @PutMapping("/{id}")
    public KullaniciYaniti guncelle(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                                    @Valid @RequestBody KullaniciGuncelleIstegi istek) {
        return servis.guncelle(k.kullanici(), id, istek);
    }

    @PostMapping("/{id}/kilit-ac")
    public KullaniciYaniti kilitAc(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id) {
        return servis.kilitAc(k.kullanici(), id);
    }
}

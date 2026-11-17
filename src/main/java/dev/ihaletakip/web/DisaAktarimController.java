package dev.ihaletakip.web;

import dev.ihaletakip.domain.IhaleDurumu;
import dev.ihaletakip.guvenlik.KullaniciDetay;
import dev.ihaletakip.servis.DisaAktarimServisi;
import dev.ihaletakip.servis.DisaAktarimServisi.Dosya;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Dışa aktarma")
@RestController
@RequestMapping("/api/disa-aktar")
public class DisaAktarimController {
    private final DisaAktarimServisi servis;

    public DisaAktarimController(DisaAktarimServisi servis) {
        this.servis = servis;
    }

    /** Kullanıcının görebildiği ihaleler; birim ile sınırlı roller yalnızca kendi birimini alır. */
    @GetMapping("/ihaleler")
    public ResponseEntity<byte[]> ihaleler(@AuthenticationPrincipal KullaniciDetay k,
                                           @RequestParam(defaultValue = "xlsx") String bicim,
                                           @RequestParam(required = false) IhaleDurumu durum) {
        return yanit(servis.ihaleler(k.kullanici(), durum, bicim));
    }

    @GetMapping("/denetim")
    @PreAuthorize("hasAnyRole('DENETCI','ADMIN','UST_YONETICI')")
    public ResponseEntity<byte[]> denetim(@AuthenticationPrincipal KullaniciDetay k,
                                          @RequestParam(defaultValue = "xlsx") String bicim) {
        return yanit(servis.denetim(k.kullanici(), bicim));
    }

    private static ResponseEntity<byte[]> yanit(Dosya d) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.icerikTuru()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.ad()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(d.icerik());
    }
}

package dev.ihaletakip.web;

import dev.ihaletakip.repo.BirimDeposu;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/birimler")
public class BirimController {
    public record BirimYaniti(Long id, String kod, String ad) {}

    private final BirimDeposu birimler;

    public BirimController(BirimDeposu birimler) {
        this.birimler = birimler;
    }

    /** Arayüzdeki birim seçimi için; oturum açmış her kullanıcı okuyabilir. */
    @GetMapping
    public List<BirimYaniti> listele() {
        return birimler.findAll(Sort.by("ad")).stream()
                .map(b -> new BirimYaniti(b.getId(), b.getKod(), b.getAd()))
                .toList();
    }
}

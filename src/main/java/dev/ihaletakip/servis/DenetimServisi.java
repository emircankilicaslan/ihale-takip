package dev.ihaletakip.servis;

import dev.ihaletakip.domain.DenetimKaydi;
import dev.ihaletakip.repo.DenetimDeposu;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DenetimServisi {
    private final DenetimDeposu depo;
    private final Clock clock;

    public DenetimServisi(DenetimDeposu depo, Clock clock) {
        this.depo = depo;
        this.clock = clock;
    }

    /** İşlemle aynı işlemde (transaction) yazılır: iş geri alınırsa kayıt da geri alınır. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void kaydet(String kullaniciAdi, String islem, String varlikTuru, Long varlikId, String ayrinti) {
        String kisa = ayrinti != null && ayrinti.length() > 2000 ? ayrinti.substring(0, 2000) : ayrinti;
        depo.save(new DenetimKaydi(LocalDateTime.now(clock), kullaniciAdi, islem, varlikTuru, varlikId, kisa));
    }

    @Transactional(readOnly = true)
    public Page<DenetimKaydi> listele(String varlikTuru, Long varlikId, Pageable sayfa) {
        if (varlikTuru != null && varlikId != null) {
            return depo.findByVarlikTuruAndVarlikId(varlikTuru, varlikId, sayfa);
        }
        return depo.findAll(sayfa);
    }
}

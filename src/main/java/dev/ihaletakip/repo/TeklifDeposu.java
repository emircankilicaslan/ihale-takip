package dev.ihaletakip.repo;

import dev.ihaletakip.domain.Teklif;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeklifDeposu extends JpaRepository<Teklif, Long> {
    List<Teklif> findByIhaleIdOrderByTutarAsc(Long ihaleId);

    boolean existsByIhaleIdAndVergiNo(Long ihaleId, String vergiNo);
}

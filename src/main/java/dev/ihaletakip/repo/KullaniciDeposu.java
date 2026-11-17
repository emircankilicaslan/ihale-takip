package dev.ihaletakip.repo;

import dev.ihaletakip.domain.Kullanici;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KullaniciDeposu extends JpaRepository<Kullanici, Long> {
    Optional<Kullanici> findByKullaniciAdi(String kullaniciAdi);

    boolean existsByKullaniciAdi(String kullaniciAdi);

    java.util.List<Kullanici> findAllByOrderByKullaniciAdiAsc();
}

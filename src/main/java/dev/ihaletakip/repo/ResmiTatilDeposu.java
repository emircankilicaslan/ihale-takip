package dev.ihaletakip.repo;

import dev.ihaletakip.domain.ResmiTatil;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResmiTatilDeposu extends JpaRepository<ResmiTatil, LocalDate> {}

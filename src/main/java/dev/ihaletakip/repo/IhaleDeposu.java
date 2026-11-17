package dev.ihaletakip.repo;

import dev.ihaletakip.domain.Ihale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IhaleDeposu extends JpaRepository<Ihale, Long>, JpaSpecificationExecutor<Ihale> {}

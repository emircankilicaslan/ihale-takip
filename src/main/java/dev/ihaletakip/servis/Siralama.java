package dev.ihaletakip.servis;

import org.springframework.data.domain.Sort;

/** İhale listesi için izin verilen sıralamalar; istemciden ham alan adı kabul edilmez. */
public enum Siralama {
    YENI(Sort.by(Sort.Direction.DESC, "id")),
    ESKI(Sort.by(Sort.Direction.ASC, "id")),
    MALIYET_ARTAN(Sort.by(Sort.Direction.ASC, "yaklasikMaliyet").and(Sort.by(Sort.Direction.DESC, "id"))),
    MALIYET_AZALAN(Sort.by(Sort.Direction.DESC, "yaklasikMaliyet").and(Sort.by(Sort.Direction.DESC, "id"))),
    /** En yakın ihale tarihi üstte; tarihi olmayanlar sonda. */
    IHALE_TARIHI(Sort.by(Sort.Order.asc("ihaleTarihi").nullsLast(), Sort.Order.desc("id")));

    private final Sort sort;

    Siralama(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }
}

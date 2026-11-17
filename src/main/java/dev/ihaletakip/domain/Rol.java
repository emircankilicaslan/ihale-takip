package dev.ihaletakip.domain;

public enum Rol {
    BIRIM_SORUMLUSU(0),
    SATIN_ALMA_UZMANI(0),
    KOMISYON_UYESI(0),
    BIRIM_MUDURU(1),
    DAIRE_BASKANI(2),
    UST_YONETICI(3),
    DENETCI(0),
    ADMIN(0);

    private final int onaySeviyesi;

    Rol(int onaySeviyesi) {
        this.onaySeviyesi = onaySeviyesi;
    }

    /** 0: onay yetkisi yok; 1..3: onaylayabileceği en yüksek kademe. */
    public int onaySeviyesi() {
        return onaySeviyesi;
    }

    /** Birim ile sınırlı roller yalnızca kendi birimlerinin ihalelerini görür. */
    public boolean birimleSinirli() {
        return this == BIRIM_SORUMLUSU || this == BIRIM_MUDURU;
    }
}

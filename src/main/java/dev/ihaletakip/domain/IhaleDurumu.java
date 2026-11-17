package dev.ihaletakip.domain;

public enum IhaleDurumu {
    TASLAK,
    ONAY_BEKLIYOR,
    ONAYLANDI,
    ILANDA,
    DEGERLENDIRMEDE,
    KARARLANDI,
    SOZLESME_IMZALANDI,
    IPTAL;

    public boolean sonDurum() {
        return this == SOZLESME_IMZALANDI || this == IPTAL;
    }
}

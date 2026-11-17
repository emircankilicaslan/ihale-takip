-- Kullanıcı yönetimi: pasife alma, art arda hatalı girişte geçici kilit
ALTER TABLE kullanici ADD (
    durum           VARCHAR2(10) DEFAULT 'AKTIF' NOT NULL,
    basarisiz_giris NUMBER(10)   DEFAULT 0 NOT NULL,
    kilit_bitis     TIMESTAMP
);

ALTER TABLE kullanici ADD CONSTRAINT ck_kullanici_durum CHECK (durum IN ('AKTIF', 'PASIF'));

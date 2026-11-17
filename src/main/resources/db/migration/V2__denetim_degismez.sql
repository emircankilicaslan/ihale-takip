-- Denetim kaydı eklenir ama değiştirilemez ve silinemez (veritabanı düzeyinde güvence).
CREATE OR REPLACE TRIGGER trg_denetim_degismez
BEFORE UPDATE OR DELETE ON denetim_kaydi
BEGIN
    RAISE_APPLICATION_ERROR(-20001, 'Denetim kaydi degistirilemez veya silinemez');
END;
/

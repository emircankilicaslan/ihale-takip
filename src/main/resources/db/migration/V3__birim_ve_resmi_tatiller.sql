-- Örnek birimler
INSERT INTO birim (kod, ad) VALUES ('BIDB', 'Bilgi İşlem Daire Başkanlığı');
INSERT INTO birim (kod, ad) VALUES ('IDARI', 'İdari ve Mali İşler Daire Başkanlığı');
INSERT INTO birim (kod, ad) VALUES ('TEKNIK', 'Teknik Hizmetler Daire Başkanlığı');

-- Sabit tarihli resmi tatiller (2026-2027). Dini bayramlar her yıl değiştiği için
-- yönetici tarafından POST /api/resmi-tatiller ile eklenmelidir.
INSERT INTO resmi_tatil (tarih, ad) VALUES (DATE '2026-01-01', 'Yılbaşı');
INSERT INTO resmi_tatil (tarih, ad) VALUES (DATE '2026-04-23', 'Ulusal Egemenlik ve Çocuk Bayramı');
INSERT INTO resmi_tatil (tarih, ad) VALUES (DATE '2026-05-01', 'Emek ve Dayanışma Günü');
INSERT INTO resmi_tatil (tarih, ad) VALUES (DATE '2026-05-19', 'Atatürk''ü Anma, Gençlik ve Spor Bayramı');
INSERT INTO resmi_tatil (tarih, ad) VALUES (DATE '2026-07-15', 'Demokrasi ve Milli Birlik Günü');
INSERT INTO resmi_tatil (tarih, ad) VALUES (DATE '2026-08-30', 'Zafer Bayramı');
INSERT INTO resmi_tatil (tarih, ad) VALUES (DATE '2026-10-29', 'Cumhuriyet Bayramı');
INSERT INTO resmi_tatil (tarih, ad) VALUES (DATE '2027-01-01', 'Yılbaşı');
INSERT INTO resmi_tatil (tarih, ad) VALUES (DATE '2027-04-23', 'Ulusal Egemenlik ve Çocuk Bayramı');
INSERT INTO resmi_tatil (tarih, ad) VALUES (DATE '2027-05-01', 'Emek ve Dayanışma Günü');
INSERT INTO resmi_tatil (tarih, ad) VALUES (DATE '2027-05-19', 'Atatürk''ü Anma, Gençlik ve Spor Bayramı');
INSERT INTO resmi_tatil (tarih, ad) VALUES (DATE '2027-07-15', 'Demokrasi ve Milli Birlik Günü');
INSERT INTO resmi_tatil (tarih, ad) VALUES (DATE '2027-08-30', 'Zafer Bayramı');
INSERT INTO resmi_tatil (tarih, ad) VALUES (DATE '2027-10-29', 'Cumhuriyet Bayramı');

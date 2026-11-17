// Çalışan uygulamaya, API üzerinden, kurgusal ama gerçekçi ihale verisi yükler.
// Hiçbir kural atlanmaz: her adım, gerçek kullanıcıların yaptığı gibi sunucu kurallarından geçer.
import { writeFileSync } from 'node:fs';

const BASE = process.env.BASE || 'http://localhost:8080';
const SIFRE = process.env.DEMO_SIFRE;
if (!SIFRE) throw new Error('DEMO_SIFRE tanımlı olmalı');

// ---- tarih yardımcıları (Türkiye saati, V3 migrasyonundaki sabit resmi tatiller)
const TATIL = new Set([
  '2026-01-01', '2026-04-23', '2026-05-01', '2026-05-19', '2026-07-15', '2026-08-30', '2026-10-29',
  '2027-01-01', '2027-04-23', '2027-05-01', '2027-05-19', '2027-07-15', '2027-08-30', '2027-10-29',
]);
const bugun = new Date().toLocaleDateString('sv-SE', { timeZone: 'Europe/Istanbul' });
const ekle = (gun, n) => {
  const d = new Date(gun + 'T00:00:00Z');
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
};
const isGunu = (gun) => {
  const h = new Date(gun + 'T00:00:00Z').getUTCDay();
  return h !== 0 && h !== 6 && !TATIL.has(gun);
};
const ilkIsGunu = (gun) => { while (!isGunu(gun)) gun = ekle(gun, 1); return gun; };

if (!isGunu(bugun)) {
  throw new Error(`Bugün (${bugun}) iş günü değil; değerlendirme aşamasındaki örnekler üretilemez. İş gününde tekrar çalıştırın.`);
}

// ---- API
async function api(yontem, yol, token, govde) {
  const yanit = await fetch(BASE + yol, {
    method: yontem,
    headers: {
      Accept: 'application/json',
      ...(govde !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body: govde !== undefined ? JSON.stringify(govde) : undefined,
  });
  const metin = await yanit.text();
  const veri = metin ? JSON.parse(metin) : null;
  if (!yanit.ok) throw new Error(`${yontem} ${yol} -> ${yanit.status}: ${veri && veri.detail}`);
  return veri;
}
const giris = async (k) => (await api('POST', '/api/auth/giris', null, { kullaniciAdi: k, sifre: SIFRE })).token;

const t = {};
for (const k of ['uzman', 'mudur', 'baskan', 'yonetici', 'komisyon']) t[k] = await giris(k);

const birimler = await api('GET', '/api/birimler', t.uzman);
const birim = (kod) => birimler.find((b) => b.kod === kod).id;

const kimlikler = {};

async function ihale(anahtar, ad, usul, tur, maliyet, birimKodu) {
  const y = await api('POST', '/api/ihaleler', t.uzman, { ad, usul, tur, yaklasikMaliyet: maliyet, birimId: birim(birimKodu) });
  kimlikler[anahtar] = y.id;
  return y.id;
}
const onayaGonder = (id) => api('POST', `/api/ihaleler/${id}/onaya-gonder`, t.uzman);
const onayla = (id, kim) => api('POST', `/api/ihaleler/${id}/onayla`, t[kim]);
const ilan = (id, ilanTarihi, ihaleTarihi) =>
  api('POST', `/api/ihaleler/${id}/ilan`, t.uzman, { ilanTarihi, ihaleTarihi });
const teklif = (id, istekliAdi, vergiNo, tutar) =>
  api('POST', `/api/ihaleler/${id}/teklifler`, t.uzman, { istekliAdi, vergiNo, tutar });

// 1) Taslak
await ihale('taslak', 'Kampüs B Blok Cephe Yenileme İşi', 'ACIK', 'YAPIM', 14200000, 'TEKNIK');

// 2) İptal edilmiş
const iptal = await ihale('iptal', 'Eski Bina Çatı Onarımı', 'ACIK', 'YAPIM', 640000, 'TEKNIK');
await onayaGonder(iptal);
await api('POST', `/api/ihaleler/${iptal}/iptal`, t.baskan, { gerekce: 'Bina yıkım programına alındığı için ihtiyaç ortadan kalkmıştır.' });

// 3) Onaylandı, ilan bekliyor (üst yönetici kademesi)
const guvenlik = await ihale('onaylandi', 'Kampüs Özel Güvenlik Hizmeti Alımı (24 ay)', 'ACIK', 'HIZMET', 8900000, 'IDARI');
await onayaGonder(guvenlik);
await onayla(guvenlik, 'yonetici');

// 4) Onay bekliyor (1. kademe)
await ihale('onayBekliyor', 'Toplantı Salonları İçin Ofis Mobilyası', 'PAZARLIK', 'MAL', 385000, 'IDARI');
await onayaGonder(kimlikler.onayBekliyor);

// 5) İlanda, teklifler geliyor
const arac = await ihale('ilanda', 'Hizmet Aracı Kiralama (36 ay)', 'ACIK', 'HIZMET', 1200000, 'IDARI');
await onayaGonder(arac);
await onayla(arac, 'baskan');
const aracIhale = ilkIsGunu(ekle(bugun, 16));
await ilan(arac, bugun, aracIhale);
await teklif(arac, 'Yıldızkent Filo Hizmetleri A.Ş.', '7410025836', 1050000);
await teklif(arac, 'Mavi Yol Kiralama Ltd. Şti.', '3698521470', 980000);

// 6) Karara bağlanmış (itiraz süresi işliyor)
const lisans = await ihale('kararlandi', 'Kurumsal Yazılım Lisans Yenileme', 'BELLI_ISTEKLILER', 'HIZMET', 960000, 'BIDB');
await onayaGonder(lisans);
await onayla(lisans, 'baskan');
await ilan(lisans, ekle(bugun, -15), bugun);
const l1 = await teklif(lisans, 'Kuzeyışık Yazılım Ltd. Şti.', '1597534682', 700000);
const l2 = await teklif(lisans, 'Doğubatı Bilgi Teknolojileri A.Ş.', '9514728360', 745000);
await teklif(lisans, 'Merkez Çözüm Danışmanlık Ltd. Şti.', '8523697410', 810000);
await api('POST', `/api/ihaleler/${lisans}/degerlendirmeye-al`, t.komisyon);
await api('POST', `/api/ihaleler/${lisans}/teklifler/${l2.id}/ret`, t.komisyon,
  { gerekce: 'Teknik şartnamede istenen destek seviyesi karşılanmamaktadır.' });
await api('POST', `/api/ihaleler/${lisans}/karar`, t.komisyon, { teklifId: l1.id });

// 7) Değerlendirmede: aşırı düşük teklif komisyon kararını bekliyor
const sunucu = await ihale('degerlendirme', 'Sunucu ve Depolama Altyapısı Yenileme', 'ACIK', 'MAL', 2450000, 'BIDB');
await onayaGonder(sunucu);
await onayla(sunucu, 'baskan');
await ilan(sunucu, ekle(bugun, -20), bugun);
await teklif(sunucu, 'Anadolu Sistem Entegrasyon A.Ş.', '4561237890', 1320000); // yaklaşık maliyetin %70'inin altı
await teklif(sunucu, 'Başkent Veri Çözümleri Ltd. Şti.', '2589631470', 1980000);
await teklif(sunucu, 'Marmara Bilişim Altyapı A.Ş.', '7894561230', 2150000);
await teklif(sunucu, 'Ege Teknoloji Sistemleri Ltd. Şti.', '3216549870', 2390000);
await api('POST', `/api/ihaleler/${sunucu}/degerlendirmeye-al`, t.uzman);

writeFileSync(new URL('./kimlikler.json', import.meta.url), JSON.stringify(kimlikler, null, 2));
console.log('Örnek veri yüklendi:', kimlikler);

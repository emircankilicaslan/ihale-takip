// Çalışan uygulamadan README ekran görüntülerini alır (seed.mjs'ten sonra çalıştırılır).
import { chromium } from 'playwright';
import { mkdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const BASE = process.env.BASE || 'http://localhost:8080';
const SIFRE = process.env.DEMO_SIFRE;
if (!SIFRE) throw new Error('DEMO_SIFRE tanımlı olmalı');

const klasor = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../docs/screenshots');
mkdirSync(klasor, { recursive: true });
const kimlik = JSON.parse(readFileSync(new URL('./kimlikler.json', import.meta.url), 'utf8'));

const tarayici = await chromium.launch();

async function baglam(secenek = {}) {
  const b = await tarayici.newContext({
    viewport: { width: 1280, height: 800 },
    locale: 'tr-TR',
    timezoneId: 'Europe/Istanbul',
    colorScheme: 'light',
    ...secenek,
  });
  return b.newPage();
}

async function girisYap(sayfa, kullanici) {
  await sayfa.goto(BASE + '/');
  await sayfa.fill('#ka', kullanici);
  await sayfa.fill('#sifre', SIFRE);
  await sayfa.click('button[type=submit]');
  await sayfa.waitForSelector('table');
}

async function git(sayfa, hash, bekle) {
  await sayfa.goto(BASE + '/' + hash);
  await sayfa.reload(); // aynı belgede hash değişimi yerine temiz yükleme
  await sayfa.waitForSelector(bekle);
  await sayfa.waitForLoadState('networkidle');
}

async function cek(sayfa, ad, secenek = {}) {
  await sayfa.screenshot({ path: path.join(klasor, ad + '.jpg'), type: 'jpeg', quality: 88, ...secenek });
  console.log('Kaydedildi:', ad);
}

// 1) Giriş (oturumsuz)
{
  const s = await baglam({ viewport: { width: 1280, height: 640 } });
  await s.goto(BASE + '/');
  await s.waitForSelector('#ka');
  await cek(s, 'arayuz-giris');
  await s.context().close();
}

// 2) Genel bakış + liste, 6) yeni ihale formu (satın alma uzmanı)
{
  const s = await baglam();
  await girisYap(s, 'uzman');
  await cek(s, 'arayuz-genel-bakis', { fullPage: true });

  await s.click('text=Yeni ihale');
  await s.fill('#alan-ad', 'Laboratuvar Sarf Malzemesi Alımı');
  await s.fill('#alan-yaklasikMaliyet', '540000');
  await cek(s, 'arayuz-yeni-ihale');
  await s.keyboard.press('Escape');

  // 5) Karara bağlanmış ihale: kazanan teklif ve itiraz süresi
  await git(s, '#/ihale/' + kimlik.kararlandi, 'text=Teklifler');
  await cek(s, 'arayuz-karar-itiraz', { fullPage: true });
  await s.context().close();
}

// 3) Onay bekleyen ihale (daire başkanı)
{
  const s = await baglam();
  await girisYap(s, 'baskan');
  await git(s, '#/ihale/' + kimlik.onayBekliyor, 'text=Onayla');
  await cek(s, 'arayuz-onay', { fullPage: true });
  await s.context().close();
}

// 4) Teklif değerlendirme (komisyon üyesi)
{
  const s = await baglam();
  await girisYap(s, 'komisyon');
  await git(s, '#/ihale/' + kimlik.degerlendirme, 'text=Kazanan seç');
  await cek(s, 'arayuz-teklif-degerlendirme', { fullPage: true });
  await s.context().close();
}

// 7) Denetim kaydı (denetçi)
{
  const s = await baglam();
  await girisYap(s, 'denetci');
  await git(s, '#/denetim/' + kimlik.degerlendirme, 'table');
  await cek(s, 'arayuz-denetim-kaydi', { fullPage: true });
  await s.context().close();
}

// 8) Mobil (komisyon üyesi, ihale detayı)
{
  const s = await baglam({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
  await girisYap(s, 'komisyon');
  await git(s, '#/ihale/' + kimlik.degerlendirme, 'text=Kazanan seç');
  await cek(s, 'arayuz-mobil');
  await s.context().close();
}

// 9) Koyu tema (liste)
{
  const s = await baglam({ colorScheme: 'dark' });
  await girisYap(s, 'uzman');
  await cek(s, 'arayuz-koyu-tema', { fullPage: true });
  await s.context().close();
}

await tarayici.close();

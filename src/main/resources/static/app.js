'use strict';

// ------------------------------------------------------------------ sabitler

const DURUM = {
  TASLAK: 'Taslak',
  ONAY_BEKLIYOR: 'Onay bekliyor',
  ONAYLANDI: 'Onaylandı',
  ILANDA: 'İlanda',
  DEGERLENDIRMEDE: 'Değerlendirmede',
  KARARLANDI: 'Karara bağlandı',
  SOZLESME_IMZALANDI: 'Sözleşme imzalandı',
  IPTAL: 'İptal',
};
const USUL = { ACIK: 'Açık ihale', BELLI_ISTEKLILER: 'Belli istekliler arasında', PAZARLIK: 'Pazarlık' };
const TUR = { MAL: 'Mal', HIZMET: 'Hizmet', YAPIM: 'Yapım' };
const ROL = {
  BIRIM_SORUMLUSU: 'Birim sorumlusu',
  SATIN_ALMA_UZMANI: 'Satın alma uzmanı',
  KOMISYON_UYESI: 'Komisyon üyesi',
  BIRIM_MUDURU: 'Birim müdürü',
  DAIRE_BASKANI: 'Daire başkanı',
  UST_YONETICI: 'Üst yönetici',
  DENETCI: 'Denetçi',
  ADMIN: 'Yönetici',
};
const ONAY_SEVIYESI = { BIRIM_MUDURU: 1, DAIRE_BASKANI: 2, UST_YONETICI: 3 };
const TEKLIF_OKUYAN = ['SATIN_ALMA_UZMANI', 'KOMISYON_UYESI', 'DENETCI', 'ADMIN', 'UST_YONETICI', 'DAIRE_BASKANI'];
const DENETIM_OKUYAN = ['DENETCI', 'ADMIN', 'UST_YONETICI'];
const SAYFA_BOYUTU = 15;

const para = new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' });

// ------------------------------------------------------------------- oturum

let oturum = oturumuOku();

function oturumuOku() {
  try {
    return JSON.parse(sessionStorage.getItem('oturum'));
  } catch (e) {
    return null;
  }
}

function oturumuYaz(deger) {
  oturum = deger;
  try {
    if (deger) sessionStorage.setItem('oturum', JSON.stringify(deger));
    else sessionStorage.removeItem('oturum');
  } catch (e) {
    // Depolama kapalıysa oturum yalnızca bellekte yaşar.
  }
}

// -------------------------------------------------------------- yardımcılar

/** DOM üretir; metinler her zaman textContent ile yazılır (XSS'e karşı). */
function el(etiket, ozellikler, ...cocuklar) {
  const dugum = document.createElement(etiket);
  for (const [anahtar, deger] of Object.entries(ozellikler || {})) {
    if (deger === false || deger == null) continue;
    if (anahtar === 'sinif') dugum.className = deger;
    else if (anahtar.startsWith('on')) dugum.addEventListener(anahtar.slice(2), deger);
    else dugum.setAttribute(anahtar, deger === true ? '' : deger);
  }
  for (const cocuk of cocuklar.flat()) {
    if (cocuk == null || cocuk === false) continue;
    dugum.append(cocuk instanceof Node ? cocuk : document.createTextNode(String(cocuk)));
  }
  return dugum;
}

function tarih(deger) {
  if (!deger) return '-';
  const [y, a, g] = String(deger).slice(0, 10).split('-').map(Number);
  return new Date(y, a - 1, g).toLocaleDateString('tr-TR');
}

function zaman(deger) {
  return deger ? new Date(deger).toLocaleString('tr-TR') : '-';
}

function rozet(metin, sinif) {
  return el('span', { sinif: 'rozet ' + (sinif || '') }, metin);
}

let bildirimZamanlayici;
function bildir(mesaj, hata) {
  const kutu = document.getElementById('bildirim');
  kutu.textContent = mesaj;
  kutu.className = 'bildirim' + (hata ? ' hata' : '');
  kutu.hidden = false;
  clearTimeout(bildirimZamanlayici);
  bildirimZamanlayici = setTimeout(() => { kutu.hidden = true; }, hata ? 7000 : 3500);
}

async function api(yol, secenek = {}) {
  const baslik = { Accept: 'application/json' };
  if (secenek.govde !== undefined) baslik['Content-Type'] = 'application/json';
  if (oturum) baslik.Authorization = 'Bearer ' + oturum.token;
  const yanit = await fetch(yol, {
    method: secenek.yontem || 'GET',
    headers: baslik,
    body: secenek.govde !== undefined ? JSON.stringify(secenek.govde) : undefined,
  });
  if (yanit.status === 401 && oturum && yol !== '/api/auth/giris') {
    oturumuYaz(null);
    git('#/');
    throw new Error('Oturum süresi doldu, yeniden giriş yapın');
  }
  const metin = await yanit.text();
  const veri = metin ? JSON.parse(metin) : null;
  if (!yanit.ok) {
    throw new Error((veri && veri.detail) || 'İşlem başarısız (' + yanit.status + ')');
  }
  return veri;
}

/** İşlemi çalıştırır, hatayı bildirir; başarıda true döner. */
async function dene(islem, basariMesaji) {
  try {
    await islem();
    if (basariMesaji) bildir(basariMesaji);
    return true;
  } catch (hata) {
    bildir(hata.message, true);
    return false;
  }
}

function git(hash) {
  if (location.hash === hash) yonlendir();
  else location.hash = hash;
}

// ------------------------------------------------------------ iletişim kutusu

/**
 * Küçük form penceresi. alanlar: [{ad, etiket, tur, secenekler?, zorunlu?, deger?}]
 * Vazgeçilirse null, tamamlanırsa {ad: deger} döner.
 */
function sor(baslik, alanlar) {
  const kutu = document.getElementById('iletisim');
  const form = document.getElementById('iletisim-form');
  const kap = document.getElementById('iletisim-alanlar');
  document.getElementById('iletisim-baslik').textContent = baslik;
  kap.replaceChildren();
  const girdiler = {};
  for (const alan of alanlar) {
    const kimlik = 'alan-' + alan.ad;
    let girdi;
    if (alan.tur === 'select') {
      girdi = el('select', { id: kimlik, required: alan.zorunlu !== false },
        alan.secenekler.map(([deger, etiket]) => el('option', { value: deger }, etiket)));
    } else if (alan.tur === 'textarea') {
      girdi = el('textarea', { id: kimlik, rows: 3, required: alan.zorunlu !== false, maxlength: 1000 });
    } else {
      girdi = el('input', {
        id: kimlik, type: alan.tur || 'text', required: alan.zorunlu !== false,
        step: alan.tur === 'number' ? '0.01' : false, min: alan.tur === 'number' ? '0.01' : false,
      });
    }
    if (alan.deger != null) girdi.value = alan.deger;
    girdiler[alan.ad] = girdi;
    kap.append(el('div', { sinif: 'alan' }, el('label', { for: kimlik }, alan.etiket), girdi));
  }

  return new Promise((coz) => {
    let bitti = false;
    const bitir = (deger) => {
      if (bitti) return;
      bitti = true;
      form.removeEventListener('submit', gonder);
      kutu.removeEventListener('close', kapandi);
      document.getElementById('iletisim-iptal').removeEventListener('click', vazgec);
      if (kutu.open) kutu.close();
      coz(deger);
    };
    const gonder = (olay) => {
      olay.preventDefault();
      if (!form.reportValidity()) return;
      const sonuc = {};
      for (const [ad, girdi] of Object.entries(girdiler)) sonuc[ad] = girdi.value.trim();
      bitir(sonuc);
    };
    const vazgec = () => bitir(null);
    const kapandi = () => bitir(null);
    form.addEventListener('submit', gonder);
    kutu.addEventListener('close', kapandi);
    document.getElementById('iletisim-iptal').addEventListener('click', vazgec);
    kutu.showModal();
    const ilk = kap.querySelector('input, select, textarea');
    if (ilk) ilk.focus();
  });
}

// ------------------------------------------------------------------ sayfalar

const icerik = () => document.getElementById('icerik');

function girisSayfasi() {
  const hata = el('p', { sinif: 'hata-metni', role: 'alert' });
  const form = el('form', { sinif: 'kart giris' },
    el('h2', null, 'Giriş'),
    el('div', { sinif: 'alan' }, el('label', { for: 'ka' }, 'Kullanıcı adı'),
      el('input', { id: 'ka', autocomplete: 'username', required: true })),
    el('div', { sinif: 'alan' }, el('label', { for: 'sifre' }, 'Parola'),
      el('input', { id: 'sifre', type: 'password', autocomplete: 'current-password', required: true })),
    hata,
    el('button', { type: 'submit' }, 'Giriş yap'));
  form.addEventListener('submit', async (olay) => {
    olay.preventDefault();
    hata.textContent = '';
    try {
      const y = await api('/api/auth/giris', {
        yontem: 'POST',
        govde: { kullaniciAdi: form.ka.value.trim(), sifre: form.sifre.value },
      });
      oturumuYaz({ token: y.token, kullaniciAdi: y.kullaniciAdi, adSoyad: y.adSoyad, rol: y.rol });
      git('#/');
    } catch (e) {
      hata.textContent = e.message;
    }
  });
  icerik().replaceChildren(form);
  document.getElementById('ka').focus();
}

async function listeSayfasi(sayfa = 0, durum = '') {
  const yol = '/api/ihaleler?size=' + SAYFA_BOYUTU + '&page=' + sayfa + (durum ? '&durum=' + durum : '');
  const veri = await api(yol);

  const filtre = el('select', { 'aria-label': 'Duruma göre filtrele' },
    el('option', { value: '' }, 'Tüm durumlar'),
    Object.entries(DURUM).map(([k, v]) => el('option', { value: k, selected: k === durum }, v)));
  filtre.addEventListener('change', () => listeSayfasi(0, filtre.value).catch(hataGoster));

  const yeniMi = ['BIRIM_SORUMLUSU', 'SATIN_ALMA_UZMANI'].includes(oturum.rol);
  const arac = el('div', { sinif: 'arac' }, filtre,
    yeniMi && el('button', { type: 'button', sinif: 'bosluk', onclick: yeniIhale }, 'Yeni ihale'));

  let govde;
  if (veri.content.length === 0) {
    govde = el('p', { sinif: 'bos kart' }, 'Kayıt bulunamadı.');
  } else {
    govde = el('div', { sinif: 'tablo-kap' }, el('table', null,
      el('thead', null, el('tr', null,
        ['İhale no', 'Ad', 'Birim', 'Yaklaşık maliyet', 'Durum', 'İhale tarihi']
          .map((b, i) => el('th', { sinif: i === 3 ? 'sayi' : false }, b)))),
      el('tbody', null, veri.content.map((i) => {
        const satir = el('tr', { sinif: 'tikla' },
          el('td', null, el('a', { href: '#/ihale/' + i.id }, i.ihaleNo)),
          el('td', null, i.ad),
          el('td', null, i.birim),
          el('td', { sinif: 'sayi' }, para.format(i.yaklasikMaliyet)),
          el('td', null, rozet(DURUM[i.durum], 'durum-' + i.durum)),
          el('td', null, tarih(i.ihaleTarihi)));
        satir.addEventListener('click', (o) => { if (o.target.tagName !== 'A') git('#/ihale/' + i.id); });
        return satir;
      }))));
  }

  const sayfalama = el('div', { sinif: 'sayfalama' },
    el('span', null, 'Toplam ' + veri.totalElements + ' kayıt'),
    el('button', { type: 'button', sinif: 'ikincil kucuk', disabled: veri.first,
      onclick: () => listeSayfasi(sayfa - 1, durum).catch(hataGoster) }, 'Önceki'),
    el('span', null, (sayfa + 1) + ' / ' + Math.max(veri.totalPages, 1)),
    el('button', { type: 'button', sinif: 'ikincil kucuk', disabled: veri.last,
      onclick: () => listeSayfasi(sayfa + 1, durum).catch(hataGoster) }, 'Sonraki'));

  icerik().replaceChildren(el('h2', null, 'İhaleler'), arac, govde, sayfalama);
}

async function yeniIhale() {
  const alanlar = [
    { ad: 'ad', etiket: 'İhale adı', tur: 'text' },
    { ad: 'usul', etiket: 'Usul', tur: 'select', secenekler: Object.entries(USUL) },
    { ad: 'tur', etiket: 'Tür', tur: 'select', secenekler: Object.entries(TUR) },
    { ad: 'yaklasikMaliyet', etiket: 'Yaklaşık maliyet (TL)', tur: 'number' },
  ];
  if (oturum.rol === 'SATIN_ALMA_UZMANI') {
    try {
      const birimler = await api('/api/birimler');
      alanlar.push({ ad: 'birimId', etiket: 'Birim', tur: 'select',
        secenekler: birimler.map((b) => [b.id, b.ad]) });
    } catch (e) {
      return bildir(e.message, true);
    }
  }
  const d = await sor('Yeni ihale', alanlar);
  if (!d) return;
  const govde = { ad: d.ad, usul: d.usul, tur: d.tur, yaklasikMaliyet: Number(d.yaklasikMaliyet) };
  if (d.birimId) govde.birimId = Number(d.birimId);
  let yeni;
  const tamam = await dene(async () => { yeni = await api('/api/ihaleler', { yontem: 'POST', govde }); },
    'İhale oluşturuldu');
  if (tamam) git('#/ihale/' + yeni.id);
}

function satirDl(etiket, deger) {
  return [el('dt', null, etiket), el('dd', null, deger)];
}

async function detaySayfasi(id) {
  const ihale = await api('/api/ihaleler/' + id);
  let teklifler = [];
  if (TEKLIF_OKUYAN.includes(oturum.rol) && ['ILANDA', 'DEGERLENDIRMEDE', 'KARARLANDI', 'SOZLESME_IMZALANDI', 'IPTAL'].includes(ihale.durum)) {
    try {
      teklifler = await api('/api/ihaleler/' + id + '/teklifler');
    } catch (e) {
      teklifler = [];
    }
  }
  const yenile = () => detaySayfasi(id).catch(hataGoster);
  const yetki = (...roller) => roller.includes(oturum.rol);
  const post = (yol, govde, mesaj) => dene(() => api('/api/ihaleler/' + id + yol, { yontem: 'POST', govde }), mesaj)
    .then((tamam) => { if (tamam) yenile(); });

  const eylemler = [];
  const dugme = (etiket, calistir, sinif) => eylemler.push(el('button', { type: 'button', sinif: sinif || false, onclick: calistir }, etiket));

  if (ihale.durum === 'TASLAK' && yetki('BIRIM_SORUMLUSU', 'SATIN_ALMA_UZMANI')) {
    dugme('Onaya gönder', () => post('/onaya-gonder', undefined, 'Onaya gönderildi'));
  }
  if (ihale.durum === 'ONAY_BEKLIYOR' && (ONAY_SEVIYESI[oturum.rol] || 0) >= ihale.gerekliOnayKademesi) {
    dugme('Onayla', () => post('/onayla', undefined, 'İhale onaylandı'));
    dugme('Geri çevir', async () => {
      const d = await sor('Geri çevir', [{ ad: 'gerekce', etiket: 'Gerekçe', tur: 'textarea' }]);
      if (d) post('/geri-cevir', { gerekce: d.gerekce }, 'Taslağa geri çevrildi');
    }, 'ikincil');
  }
  if (ihale.durum === 'ONAYLANDI' && yetki('SATIN_ALMA_UZMANI')) {
    dugme('İlan et', async () => {
      const d = await sor('İlan bilgileri', [
        { ad: 'ilanTarihi', etiket: 'İlan tarihi', tur: 'date' },
        { ad: 'ihaleTarihi', etiket: 'İhale tarihi (iş günü olmalı)', tur: 'date' },
      ]);
      if (d) post('/ilan', { ilanTarihi: d.ilanTarihi, ihaleTarihi: d.ihaleTarihi }, 'İhale ilan edildi');
    });
  }
  if (ihale.durum === 'ILANDA' && yetki('SATIN_ALMA_UZMANI')) {
    dugme('Teklif ekle', async () => {
      const d = await sor('Teklif ekle', [
        { ad: 'istekliAdi', etiket: 'İstekli adı', tur: 'text' },
        { ad: 'vergiNo', etiket: 'Vergi / TC kimlik no', tur: 'text' },
        { ad: 'tutar', etiket: 'Teklif tutarı (TL)', tur: 'number' },
      ]);
      if (d) post('/teklifler', { istekliAdi: d.istekliAdi, vergiNo: d.vergiNo, tutar: Number(d.tutar) }, 'Teklif eklendi');
    });
  }
  if (ihale.durum === 'ILANDA' && yetki('SATIN_ALMA_UZMANI', 'KOMISYON_UYESI')) {
    dugme('Değerlendirmeye al', () => post('/degerlendirmeye-al', undefined, 'Değerlendirmeye alındı'), 'ikincil');
  }
  if (ihale.durum === 'KARARLANDI' && yetki('SATIN_ALMA_UZMANI')) {
    dugme('Sözleşme imzalandı', () => post('/sozlesme', undefined, 'Sözleşme işlendi'));
  }
  if (!['SOZLESME_IMZALANDI', 'IPTAL'].includes(ihale.durum) && yetki('DAIRE_BASKANI', 'UST_YONETICI')) {
    dugme('İptal et', async () => {
      const d = await sor('İhaleyi iptal et', [{ ad: 'gerekce', etiket: 'Gerekçe', tur: 'textarea' }]);
      if (d) post('/iptal', { gerekce: d.gerekce }, 'İhale iptal edildi');
    }, 'tehlike');
  }

  const ozet = el('div', { sinif: 'kart' }, el('dl', { sinif: 'ayrinti' },
    satirDl('İhale no', ihale.ihaleNo),
    satirDl('Durum', rozet(DURUM[ihale.durum], 'durum-' + ihale.durum)),
    satirDl('Usul / tür', USUL[ihale.usul] + ' · ' + TUR[ihale.tur]),
    satirDl('Birim', ihale.birim),
    satirDl('Yaklaşık maliyet', para.format(ihale.yaklasikMaliyet)),
    satirDl('Oluşturan', ihale.olusturan),
    ihale.gerekliOnayKademesi != null && satirDl('Gerekli onay kademesi', ihale.gerekliOnayKademesi + '. kademe'),
    ihale.onaylayan && satirDl('Onaylayan', ihale.onaylayan),
    ihale.ilanTarihi && satirDl('İlan / ihale tarihi', tarih(ihale.ilanTarihi) + ' – ' + tarih(ihale.ihaleTarihi)),
    ihale.kararZamani && satirDl('Karar zamanı', zaman(ihale.kararZamani)),
    ihale.itirazSonTarihi && satirDl('İtiraz süresi sonu', tarih(ihale.itirazSonTarihi)),
    ihale.gerekce && satirDl('Gerekçe', ihale.gerekce)),
    eylemler.length > 0 && el('div', { sinif: 'eylemler' }, eylemler));

  const parcalar = [
    el('p', null, el('a', { href: '#/' }, '← İhaleler')),
    el('h2', null, ihale.ad),
    ozet,
  ];

  if (teklifler.length > 0) {
    parcalar.push(el('h3', null, 'Teklifler'), teklifTablosu(ihale, teklifler, post, yetki));
  }
  if (DENETIM_OKUYAN.includes(oturum.rol)) {
    parcalar.push(el('p', null, el('a', { href: '#/denetim/' + id }, 'Bu ihalenin denetim kaydı →')));
  }
  icerik().replaceChildren(...parcalar);
}

function teklifTablosu(ihale, teklifler, post, yetki) {
  const degerlendirme = ihale.durum === 'DEGERLENDIRMEDE' && yetki('KOMISYON_UYESI');
  const baslik = ['İstekli', 'Vergi no', 'Tutar', 'Durum'];
  if (degerlendirme) baslik.push('İşlem');
  return el('div', { sinif: 'tablo-kap' }, el('table', null,
    el('thead', null, el('tr', null, baslik.map((b, i) => el('th', { sinif: i === 2 ? 'sayi' : false }, b)))),
    el('tbody', null, teklifler.map((t) => {
      const kazanan = ihale.kazananTeklifId === t.id;
      const hucreler = [
        el('td', null, t.istekliAdi, kazanan && [' ', rozet('Kazanan', 'durum-GECERLI')]),
        el('td', null, t.vergiNo),
        el('td', { sinif: 'sayi' }, para.format(t.tutar)),
        el('td', null, rozet(t.durum === 'GECERLI' ? 'Geçerli' : 'Reddedildi', 'durum-' + t.durum), ' ',
          t.asiriDusuk && rozet('Aşırı düşük' + (t.asiriDusukAciklama ? ' · ' + t.asiriDusukAciklama : ''), 'uyari'),
          t.retGerekcesi && el('div', { sinif: 'soluk' }, t.retGerekcesi)),
      ];
      if (degerlendirme) {
        const islemler = [];
        const yol = '/teklifler/' + t.id;
        if (t.durum === 'GECERLI') {
          if (t.asiriDusuk && !t.asiriDusukAciklama) {
            islemler.push(
              el('button', { type: 'button', sinif: 'kucuk ikincil', onclick: () => post(yol + '/asiri-dusuk-aciklama', { karar: 'KABUL' }, 'Açıklama kabul edildi') }, 'Açıklamayı kabul et'),
              el('button', { type: 'button', sinif: 'kucuk tehlike', onclick: () => post(yol + '/asiri-dusuk-aciklama', { karar: 'RED' }, 'Teklif reddedildi') }, 'Açıklamayı reddet'));
          } else {
            islemler.push(el('button', { type: 'button', sinif: 'kucuk', onclick: () => post('/karar', { teklifId: t.id }, 'Karar verildi') }, 'Kazanan seç'));
          }
          islemler.push(el('button', { type: 'button', sinif: 'kucuk tehlike', onclick: async () => {
            const d = await sor('Teklifi reddet', [{ ad: 'gerekce', etiket: 'Ret gerekçesi', tur: 'textarea' }]);
            if (d) post(yol + '/ret', { gerekce: d.gerekce }, 'Teklif reddedildi');
          } }, 'Reddet'));
        }
        hucreler.push(el('td', null, el('div', { sinif: 'eylemler' }, islemler)));
      }
      return el('tr', null, hucreler);
    }))));
}

async function denetimSayfasi(ihaleId) {
  if (!DENETIM_OKUYAN.includes(oturum.rol)) {
    icerik().replaceChildren(el('p', { sinif: 'bos kart' }, 'Bu sayfaya erişim yetkiniz yok.'));
    return;
  }
  const yol = '/api/denetim?size=50' + (ihaleId ? '&varlikTuru=IHALE&varlikId=' + encodeURIComponent(ihaleId) : '');
  const veri = await api(yol);
  const govde = veri.content.length === 0
    ? el('p', { sinif: 'bos kart' }, 'Kayıt yok.')
    : el('div', { sinif: 'tablo-kap' }, el('table', null,
      el('thead', null, el('tr', null, ['Zaman', 'Kullanıcı', 'İşlem', 'Kayıt', 'Ayrıntı'].map((b) => el('th', null, b)))),
      el('tbody', null, veri.content.map((k) => el('tr', null,
        el('td', null, zaman(k.zaman)),
        el('td', null, k.kullaniciAdi),
        el('td', null, k.islem),
        el('td', null, k.varlikTuru + (k.varlikId != null ? ' #' + k.varlikId : '')),
        el('td', null, k.ayrinti || ''))))));
  icerik().replaceChildren(
    ihaleId && el('p', null, el('a', { href: '#/ihale/' + ihaleId }, '← İhale')),
    el('h2', null, 'Denetim kaydı' + (ihaleId ? ' · ihale #' + ihaleId : '')),
    el('p', { sinif: 'soluk' }, 'Kayıtlar değiştirilemez ve silinemez. En yeni kayıtlar üstte.'),
    govde);
}

// ----------------------------------------------------------------- yönlendirme

function hataGoster(hata) {
  bildir(hata.message, true);
  icerik().replaceChildren(el('p', { sinif: 'bos kart' }, hata.message));
}

function ustuGuncelle() {
  const girisli = !!oturum;
  document.getElementById('menu').hidden = !girisli;
  document.getElementById('kullanici').hidden = !girisli;
  document.getElementById('denetim-menu').hidden = !girisli || !DENETIM_OKUYAN.includes(oturum.rol);
  if (girisli) {
    document.getElementById('kullanici-bilgi').textContent = oturum.adSoyad + ' · ' + ROL[oturum.rol];
  }
  const hash = location.hash || '#/';
  for (const d of document.querySelectorAll('.menu-dugme')) {
    const hedef = d.dataset.sayfa === 'denetim' ? '#/denetim' : '#/';
    d.classList.toggle('aktif', hash === hedef || (d.dataset.sayfa === 'liste' && hash.startsWith('#/ihale')));
  }
}

function yonlendir() {
  ustuGuncelle();
  if (!oturum) return girisSayfasi();
  const hash = location.hash || '#/';
  let is;
  let m;
  if ((m = hash.match(/^#\/ihale\/(\d+)$/))) is = detaySayfasi(m[1]);
  else if ((m = hash.match(/^#\/denetim(?:\/(\d+))?$/))) is = denetimSayfasi(m[1]);
  else is = listeSayfasi();
  is.catch(hataGoster);
}

document.querySelectorAll('.menu-dugme').forEach((d) => {
  d.addEventListener('click', () => git(d.dataset.sayfa === 'denetim' ? '#/denetim' : '#/'));
});
document.getElementById('cikis').addEventListener('click', () => {
  oturumuYaz(null);
  git('#/');
});
window.addEventListener('hashchange', yonlendir);
yonlendir();

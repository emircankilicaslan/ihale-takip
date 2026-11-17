# ihale-takip

Kamu ihale süreçlerini baştan sona izleyen REST API: taslaktan sözleşmeye kadar durum akışı, tutara göre onay kademeleri, iş günü ve resmi tatil bilen süre hesabı, rol ve birim bazlı yetki ve değiştirilemez denetim kaydı.

**Java 21 · Spring Boot 3 · Oracle · Flyway · Spring Security (JWT + Active Directory) · JUnit 5**

[![CI](https://github.com/emircankilicaslan/ihale-takip/actions/workflows/ci.yml/badge.svg)](https://github.com/emircankilicaslan/ihale-takip/actions/workflows/ci.yml)

> **Doğrulama:** Her push'ta GitHub Actions, Oracle Free servis konteynerine karşı 19 testi (birim ve uçtan uca akış) çalıştırır.
>
> **Mevzuat uyarısı:** Bu bir portfolyo/demo projesidir, hukuki danışmanlık değildir. Onay sınırları, ilan süreleri ve itiraz süresi **örnek değerlerdir** ve `application.yml` içinden ayarlanır; gerçek bir kurumda 4734 sayılı Kanun ve ilgili mevzuat ile doğrulanmalıdır. Kazanan seçimi "en düşük fiyat" olarak sadeleştirilmiştir.

## Neden bu proje

Kamu kurumlarında bilişim işleri çoğunlukla Java/.NET, Oracle ve kurumsal dizin (Active Directory) üzerinde yürür. Bu proje o ortama uyumlu bir iş uygulamasını gösterir: kimin neyi ne zaman yaptığı kayıt altındadır, yetkiler kuralla sınırlıdır, süreler takvime göre hesaplanır.

## Süreç akışı

```
TASLAK → ONAY_BEKLIYOR → ONAYLANDI → ILANDA → DEGERLENDIRMEDE → KARARLANDI → SOZLESME_IMZALANDI
                ↓ (geri çevir)                                          (her aşamadan IPTAL)
              TASLAK
```

| Kural | Davranış |
| :--- | :--- |
| **Onay kademesi** | Yaklaşık maliyete göre 1/2/3. kademe; ihale, o kademe veya üstü onay seviyesindeki kullanıcı tarafından onaylanır (birim müdürü → daire başkanı → üst yönetici). |
| **Görev ayrılığı** | Kullanıcı kendi oluşturduğu ihaleyi onaylayamaz. |
| **İlan süresi** | İhale usulüne göre asgari ilan süresi geçmeden ihale tarihi verilemez; ihale tarihi iş günü olmalıdır. |
| **Teklif süresi** | İlan tarihinden önce ve ihale tarihinden sonra teklif alınmaz; aynı istekli aynı ihaleye tek teklif verir. |
| **Aşırı düşük teklif** | Yaklaşık maliyetin ayarlanan oranının altındaki teklif işaretlenir; komisyon açıklamayı kabul veya reddeder. |
| **Karar** | Kazanan, uygun teklifler arasındaki en düşük teklif olmalı ve yaklaşık maliyeti aşmamalıdır. |
| **İtiraz süresi** | Karar tarihinden itibaren süre dolmadan sözleşme imzalanamaz; süre tatile/hafta sonuna denk gelirse sonraki iş gününe uzar. |
| **Eşzamanlılık** | İyimser kilit (`@Version`): aynı ihaleyi iki kişi aynı anda ilerletemez (409). |

## Roller

| Rol | Yapabildiği |
| :--- | :--- |
| `BIRIM_SORUMLUSU` | Kendi biriminde ihale oluşturur, onaya gönderir |
| `BIRIM_MUDURU` / `DAIRE_BASKANI` / `UST_YONETICI` | Kademesi yettiği ölçüde onaylar veya geri çevirir; daire başkanı ve üstü iptal eder |
| `SATIN_ALMA_UZMANI` | İlan eder, teklif girer, sözleşmeyi işler |
| `KOMISYON_UYESI` | Teklifleri değerlendirir, ret ve aşırı düşük kararı verir, karara bağlar |
| `DENETCI` | Her şeyi okur (denetim kaydı dahil), hiçbir şeyi değiştiremez |
| `ADMIN` | Resmi tatilleri yönetir |

Birim müdürü ve sorumlusu yalnızca kendi birimini görür.

## Denetim kaydı

Her durum değişikliği `denetim_kaydi` tablosuna yazılır (kim, ne, hangi kayıt, ne zaman). Kayıt, işlemle aynı transaction içinde yazıldığı için işlem geri alınırsa kayıt da geri alınır. **Veritabanı tetikleyicisi** (`V2__denetim_degismez.sql`) güncelleme ve silmeyi engeller; uygulama hatalı olsa bile kayıt değiştirilemez.

## Kimlik doğrulama

- **`yerel` (varsayılan):** Kullanıcı tablosu + BCrypt parola.
- **`ldap`:** Kimlik kurumun Active Directory'sinde doğrulanır (`ActiveDirectoryLdapAuthenticationProvider`); rol ve birim uygulamanın kendi kullanıcı tablosundan gelir. Dizinde olup tabloda kaydı olmayan kullanıcı giremez.

```bash
KIMLIK_MODU=ldap AD_DOMAIN=kurum.gov.tr LDAP_URL=ldaps://dc.kurum.gov.tr:636
```

Oturum JWT ile açılır; rol ve birim her istekte veritabanından okunduğu için yetki değişikliği hemen etkili olur.

## Çalıştırma

Gereken: Docker. Proje köküne `.env` dosyası oluşturun (bu dosya git'e girmez):

```
ORACLE_SYS_SIFRE=<oracle-yonetici-parolasi>
DB_PASSWORD=<uygulama-veritabani-parolasi>
JWT_SECRET=<en-az-32-karakterlik-rastgele-metin>
DEMO_SIFRE=<demo-kullanicilar-icin-parola>
```

```bash
docker compose up --build
```

`dev` profili her rol için bir demo kullanıcı oluşturur (`sorumlu`, `mudur`, `baskan`, `yonetici`, `uzman`, `komisyon`, `denetci`, `admin`); parola `DEMO_SIFRE`'dir.

```bash
curl -X POST localhost:8080/api/auth/giris -H "Content-Type: application/json" \
     -d '{"kullaniciAdi":"sorumlu","sifre":"<DEMO_SIFRE>"}'
```

## Test

`mvn verify`: birim testleri (iş günü ve kural hesapları) ve Oracle'a karşı uçtan uca akış testi. Akış testi saati ileri alarak ilan, teklif, itiraz süresi ve sözleşme adımlarını gerçek veritabanı ve güvenlik zinciriyle sınar. CI, GitHub Actions'ta bir Oracle Free servis konteyneri kullanır.

## API özeti

| Yöntem | Yol | Açıklama |
| :--- | :--- | :--- |
| POST | `/api/auth/giris` | Oturum aç, JWT al |
| GET/POST | `/api/ihaleler` | Listele (sayfalı, `durum` filtreli) / oluştur |
| POST | `/api/ihaleler/{id}/onaya-gonder` · `onayla` · `geri-cevir` | Onay akışı |
| POST | `/api/ihaleler/{id}/ilan` | İlan ve ihale tarihi |
| GET/POST | `/api/ihaleler/{id}/teklifler` | Teklifler |
| POST | `/api/ihaleler/{id}/degerlendirmeye-al` · `karar` · `sozlesme` · `iptal` | Sonraki aşamalar |
| POST | `/api/ihaleler/{id}/teklifler/{tid}/ret` · `asiri-dusuk-aciklama` | Komisyon kararları |
| GET | `/api/denetim` | Denetim kaydı (denetçi/admin/üst yönetici) |
| POST | `/api/resmi-tatiller` | Resmi tatil ekle (dini bayramlar her yıl eklenmelidir) |

## Mimari notlar

- Şema yalnızca Flyway migrasyonlarıyla değişir; Hibernate `ddl-auto: validate` ile şemayı doğrular.
- Kural mantığı (`kural/`) veritabanından bağımsız saf sınıflardır ve ayrı birim testleriyle sınanır.
- Mevzuat eşikleri ve süreleri kodda değil yapılandırmadadır.
- Hata yanıtları `ProblemDetail` (RFC 9457) biçimindedir; kimlik hatalarında neden sızdırılmaz.

## Kapsam dışı

Arayüz, e-imza/EKAP entegrasyonu, belge yükleme ve mevzuatın tam modellenmesi bu sürümde yoktur.

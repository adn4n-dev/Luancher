# Sade Launcher

Siyah arka plan, beyaz yazı, ikon yok, rozet yok. Uygulamalar alfabetik liste.

- **İnternet izni yok.** Veri hiçbir yere gidemez. Dış kütüphane yok, tüm kod tek dosya: `app/src/main/java/com/sade/launcher/MainActivity.java`
- **Ana ekran:** üstte saat (dokun → alarmlar), tarih (dokun → takvim), pil ve isteğe bağlı ekran süresi. Altta, başparmak hizasında favoriler (en fazla 7).
- **Sağ kenarda harf şeridi:** parmağını üzerinde gezdir, harfler dalga gibi kayar ve liste o harfe atlar.
- **Yukarı kaydır:** tüm uygulamalar + arama (klavye açık gelir).
- **Aşağı kaydır:** bildirim paneli. Çekmecedeyken ana ekrana döner.
- **İkonlar:** gri (varsayılan), renkli veya kapalı.
- **Uzun bas (uygulama):** alttan menü. Favori / Yeniden adlandır / Gizle / Bilgi / Kaldır, ayrıca bugünkü kullanım süresi.
- **Uzun bas (boş alan):** Ayarlar. İkonlar, ekran süresi, gizli uygulamalar.
- **Gizle:** uygulama sadece aramayla çıkar, açmadan önce 5 sn bekletir.

## Derleme

**A) Bilgisayarsız (GitHub):**
1. GitHub'da yeni bir repo aç, bu klasördeki her şeyi yükle (`.github` klasörü dahil).
2. Actions sekmesi → "APK derle" çalışır (birkaç dakika).
3. Bitince en alttaki **SadeLauncher-apk**'yı indir, zip'ten `app-debug.apk`'yı çıkar.

**B) Android Studio:** Klasörü aç → Build → Build APK(s).

## Kurulum
1. APK'yı telefona at, aç (bilinmeyen kaynaklara izin isteyecek).
2. Ayarlar → Uygulamalar → Varsayılan uygulamalar → Ana ekran uygulaması → **Sade**.

Geri dönmek istersen aynı yerden eski launcher'ı seç.

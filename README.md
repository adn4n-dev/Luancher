# Sade Launcher

Siyah arka plan, beyaz yazı, ikon yok, rozet yok. Uygulamalar alfabetik liste.

- **İnternet izni yok.** Veri hiçbir yere gidemez. Dış kütüphane yok, tüm kod tek dosya: `app/src/main/java/com/sade/launcher/MainActivity.java`
- **Üstte:** saat (dokun → alarmlar), tarih (dokun → takvim), pil, bugünkü ekran süresi (isteğe bağlı).
- **Favoriler:** saatin altında büyük yazıyla, en fazla 7.
- **Tüm uygulamalar:** harf başlıklarıyla alfabetik.
- **Aşağı kaydır:** bildirim paneli.
- **Uzun bas:** alttan açılan menü. Favori / Yeniden adlandır / Gizle / Bilgi / Kaldır, ayrıca o uygulamada bugün ne kadar kaldığın.
- **Gizle:** sadece aramayla çıkar, açmadan önce 5 sn bekletir ve bugünkü süreni hatırlatır.
- **Ara:** yazınca üst kısım kaybolur. Sonuç yoksa internette arar.

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

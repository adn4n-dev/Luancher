# Sade Launcher

Siyah arka plan, beyaz yazı, ikon yok, rozet yok. Uygulamalar alfabetik liste.

- **İzin yok, internet yok.** Manifest'e bakabilirsin: `INTERNET` izni yok, veri hiçbir yere gidemez.
- **Dış kütüphane yok.** Sadece Android'in kendi API'leri. Tüm kod tek dosya: `app/src/main/java/com/sade/launcher/MainActivity.java`
- **Üstte:** saat, tarih, pil. Saate dokun → alarmlar, tarihe dokun → takvim.
- **Favoriler:** saatin altında büyük yazıyla, en fazla 7 tane.
- **Uzun bas:** Favorilere ekle / Yeniden adlandır / Gizle / Uygulama bilgisi / Kaldır.
- **Gizle:** Uygulama listeden kalkar, sadece aramayla çıkar ve açmadan önce 5 saniye bekletir.
- **Ara + klavyede Git:** ilk sonucu açar. Sonuç yoksa internette arar.
- **Geri tuşu:** aramayı temizler.

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

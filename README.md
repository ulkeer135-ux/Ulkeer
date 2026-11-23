# Annem — Yaşlılar için sade Android ana ekranı

Kotlin + Jetpack Compose ile yazılmış, internet ya da hesap istemeyen bir launcher (ana ekran) uygulaması.

## Ekranlar

- **Ana ekran:** büyük saat ve tarih, yanında şarj yüzdesi (şarjdayken yeşil şimşek, %20 altında kırmızı "şarja tak" uyarısı).
  - **Hava durumu** kutusu: anlık sıcaklık, durum, günün en yüksek/en düşük değerleri. 30 dakikada bir güncellenir, internet yoksa son değeri gösterir. Dokununca Google'ın hava durumu sayfası açılır.
  - Altı büyük kutu: Kişiler (telefonun rehberi), Telefon, WhatsApp, Mesajlar, Kamera, Fotoğraflar.
  - **Ekranı Kapat** düğmesi: güç tuşu gibi ekranı kilitler.
- **Ayarlar (yakınlar için):** saate **3 saniye basılı tutunca** açılır. Ekranı kapatmayı etkinleştirme, hava durumu şehri, varsayılan ana ekran seçimi.

Geri tuşu ana ekranda bir şey yapmaz; Ana Sayfa tuşu her zaman ana ekrana döner. Sistem yazı boyutu yerleşimi bozmasın diye sabitlenmiştir.

## APK'yı derlemek

**Seçenek A: GitHub (bilgisayara bir şey kurmadan)**
1. `annem-launcher` dalına her gönderimde "Annem APK derle" iş akışı çalışır.
2. Bitince APK, **Releases › annem-latest** altında `Annem.apk` olarak yayınlanır; telefondan doğrudan indirilebilir.

**Seçenek B: Android Studio**
1. *File › Open* ile bu klasörü aç, Gradle senkronunun bitmesini bekle.
2. *Build › Build App Bundle(s) / APK(s) › Build APK(s)*.
3. Çıktı: `app/build/outputs/apk/debug/app-debug.apk`. Telefon USB ile bağlıysa doğrudan ▶ Run da olur.

## Annenin telefonuna kurulum

1. APK'yı telefona aktar ve aç. "Bilinmeyen kaynaklardan yükleme" izni istenirse ver.
2. Uygulamayı bir kez aç, saate 3 sn basılı tut → **Ayarlar**:
   - **Ekranı Kapat düğmesi** → Erişilebilirlik ayarlarında "Annem – Ekranı kapat"ı aç. Seçenek gri ise önce *Uygulama bilgisi › ⋮ › Kısıtlanmış ayarlara izin ver* (Android 13+, Play Store dışından kurulan uygulamalar için).
   - **Hava durumu şehri** (varsayılan İstanbul).
   - **Varsayılan ana ekranı seç** → "Annem"i seç.

## Bilinen sınırlar

- Ekranı kapatma Android 9 ve üstünde çalışır. Erişilebilirlik servisi yalnızca kilitleme komutunu gönderir, ekran içeriğini okumaz. (Cihaz yöneticisi yöntemi yerine bu seçildi: o yöntem bir sonraki açılışta parmak izi yerine PIN ister.)
- Hava durumu verisi Open-Meteo'dan gelir (ücretsiz, anahtarsız). Google'ın Weather API'si faturalandırmalı API anahtarı istediği için kullanılmadı; kutuya dokununca Google'ın sayfası açılır.
- Ekranı dokunarak *açmak* (çift dokunma / KnockOn) telefonun kendi özelliğidir; uygulamadan yapılamaz. Çoğu telefonda *Ayarlar › Ekran* altında "Uyandırmak için iki kez dokun" olarak bulunur.
- Bildirim çubuğu aşağı çekilebilir; tam kiosk kilidi bu sürümde yok.
- Bu bir debug APK'dır (sabit debug anahtarıyla imzalı). Play Store'a koymak için ayrı bir release imzası gerekir.

## Teknik

- minSdk 26 (Android 8.0), targetSdk 35, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Gradle 8.14.3.
- Veri: hava durumu şehri ve son hava durumu (`SharedPreferences`).
- Enerji: saat dakika başlarında güncellenir; saat, şarj ve hava durumu yalnızca ekran görünürken çalışır; arka plan servisi yok.
- İmza: depodaki `app/debug.keystore` (sabit debug anahtarı, güncellemeler üzerine kurulsun diye).
- Kod: `MainActivity.kt` (ekran geçişleri), `Actions.kt` (arama/uygulama açma), `SettingsStore.kt` (ayar), `Weather.kt` (hava durumu), `LockService.kt` (ekranı kapat), `ui/` (ekranlar ve tema).

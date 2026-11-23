# Annem — Yaşlılar için sade Android ana ekranı

Kotlin + Jetpack Compose ile yazılmış, internet ya da hesap istemeyen bir launcher (ana ekran) uygulaması.

## Ekranlar

- **Ana ekran:** büyük saat ve tarih; altı büyük kutu (Rehber, Telefon, WhatsApp, Mesajlar, Kamera, Fotoğraflar); kırmızı **Acil Yardım** düğmesi (önce "aransın mı?" diye onay ister).
- **Rehber:** telefonun kendi rehber uygulamasını açar.
- **Ayarlar (yakınlar için):** saate **3 saniye basılı tutunca** açılır. Acil durum numarası, arama izni, varsayılan ana ekran seçimi.

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
   - **İzni ver** (doğrudan arama).
   - **Varsayılan ana ekranı seç** → "Annem"i seç.

## Bilinen sınırlar

- Android, 112 gibi acil numaraları güvenlik gereği uygulamaların doğrudan aramasına izin vermez: telefon uygulaması numara yazılı açılır, yeşil düğmeye basmak gerekir. Tek dokunuşla arama istenirse acil durum numarası olarak bir yakının numarası yazılabilir.
- Bildirim çubuğu aşağı çekilebilir; tam kiosk kilidi bu sürümde yok.
- Bu bir debug APK'dır (debug anahtarıyla imzalı). Play Store'a koymak için ayrı bir release imzası gerekir.

## Teknik

- minSdk 26 (Android 8.0), targetSdk 35, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Gradle 8.14.3.
- Veri: yalnızca acil durum numarası (`SharedPreferences`).
- İmza: depodaki `app/debug.keystore` (sabit debug anahtarı, güncellemeler üzerine kurulsun diye).
- Kod: `MainActivity.kt` (ekran geçişleri), `Actions.kt` (arama/uygulama açma), `SettingsStore.kt` (ayar), `ui/` (ekranlar ve tema).

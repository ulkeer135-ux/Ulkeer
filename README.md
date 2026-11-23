# Annem — Yaşlılar için sade Android ana ekranı

Kotlin + Jetpack Compose ile yazılmış, internet ya da hesap istemeyen bir launcher (ana ekran) uygulaması.

## Görünüm

"Sıcak ve yumuşak": krem zemin ve yumuşak şeftali/pembe yuvarlaklar; iki tonlu kutular (üstü pastel renkli, simge; altı beyaz, yazı); yuvarlak hatlı **Nunito** yazı tipi. Kutu yazıları hiçbir ekranda kesilmez, sığmazsa küçülür. Hava durumu kartı havaya göre renk alır (gece lacivert ve yıldızlı, gündüz mavi, yağmurda gri-mavi). Nunito, SIL Open Font License ile uygulamaya gömülüdür (`licenses/OFL-Nunito.txt`).

## Ekranlar

- **Ana ekran:** büyük saat ve tarih, yanında şarj yüzdesi (şarjdayken yeşil şimşek, %20 altında kırmızı "şarja tak" uyarısı).
  - **Hava durumu** kutusu (büyük): anlık sıcaklık, durum, günün en yüksek/en düşük değerleri ve semt adı. Telefonun yaklaşık konumunu kullanır; izin yoksa ya da konum kapalıysa Ayarlar'daki şehri. 30 dakikada bir güncellenir, internet yoksa son değeri gösterir. Dokununca Google'ın hava durumu sayfası açılır.
  - **9 kutu (3×3, varsayılan)** ya da **6 büyük kutu (2×3)**; Ayarlar › Ana ekran düzeni. Sabit kutular: Kişiler, Telefon, WhatsApp; geri kalanlar (9'da altı, 6'da üç) **uygulaması kullanıcı tarafından seçilen kutular**. Boş kutuda "Uygulama ekle" yazar; dokununca uygulama listesi açılır. Kutuda seçilen uygulamanın simgesi ve verilen ad görünür. Değiştirmek/boşaltmak: Ayarlar › Kutulara uygulama ata.
  - **Kırmızı sayılar:** WhatsApp kutusunda okunmamış mesaj, Telefon kutusunda cevapsız arama sayısı (bildirim erişimiyle; yalnızca sayılır, içerik okunmaz). Ayarlar'dan gizlenebilir.
  - **Ekranı Kapat** düğmesi: ekranı kilitler. Kurulmamışsa basınca kurulumu açan bir pencere çıkar.
- **Kişiler ("Kimi arayalım?"):** önce **favoriler** büyük fotoğraflı kartlarda ("Oğlum", "Gelinim"…; tek favori tam genişlikte), sonra diğer kişiler; her birinde büyük **Ara**. **Başlığa 3 sn basılı tutunca düzenleme modu:** her kişinin yanında yıldız (favoriye ekle/çıkar), kişiye dokununca görünen ad ve galeriden fotoğraf (ör. WhatsApp'tan kaydedilen profil fotoğrafı). Telefonun rehberi değişmez.
- **Ayarlar (yakınlar için):** saate **3 saniye basılı tutunca** açılır. Ekranı kapatma kurulumu (iki yol), kutulara uygulama atama, kişiler ve arama izinleri, hava durumu konumu (telefonun konumu ya da sabit şehir), varsayılan ana ekran seçimi.

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
   - **Ekranı Kapat düğmesi** → Yol 1: Erişilebilirlik ayarlarında "Annem – Ekranı kapat"ı aç (seçenek gri ise önce *Uygulama bilgisi › ⋮ › Kısıtlanmış ayarlara izin ver*). Açılmazsa Yol 2: **Cihaz yöneticisini aç** (tek onay).
   - **Kırmızı bildirim sayıları** → Bildirim erişimini aç, "Annem – Bildirim sayıları"na izin ver.
   - **Kişiler ve arama** → İzinleri ver. Telefonun rehberinde önemli kişileri yıldızla favorilere ekle; en üstte görünürler.
   - İlk açılışta çıkan **konum izni** penceresinde "Uygulama kullanılırken" seç (hava durumu için). Vermezsen Ayarlar'daki şehir (varsayılan İstanbul) kullanılır.
   - **Varsayılan ana ekranı seç** → "Annem"i seç.

## Bilinen sınırlar

- Ekranı kapatma: erişilebilirlik yolu Android 9+ ister, ekran içeriğini okumaz. Cihaz yöneticisi yolu her sürümde çalışır ama parmak izi kullanılıyorsa kilitten sonraki ilk açılışta PIN/desen isteyebilir; ayrıca uygulamayı silmeden önce Ayarlar'dan kapatılmalıdır.
- Hava durumu verisi Open-Meteo'dan gelir (ücretsiz, anahtarsız). Google'ın Weather API'si faturalandırmalı API anahtarı istediği için kullanılmadı; kutuya dokununca Google'ın sayfası açılır.
- Ekranı dokunarak *açmak* (çift dokunma / KnockOn) telefonun kendi özelliğidir; uygulamadan yapılamaz. Çoğu telefonda *Ayarlar › Ekran* altında "Uyandırmak için iki kez dokun" olarak bulunur.
- Bildirim çubuğu aşağı çekilebilir; tam kiosk kilidi bu sürümde yok.
- Bu bir debug APK'dır (sabit debug anahtarıyla imzalı). Play Store'a koymak için ayrı bir release imzası gerekir.

## Teknik

- minSdk 26 (Android 8.0), targetSdk 35, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Gradle 8.14.3.
- Veri: hava durumu konumu ve son hava durumu (`SharedPreferences`).
- Konum: yalnızca yaklaşık konum izni (`ACCESS_COARSE_LOCATION`), `LocationManager` ile; Google Play Hizmetleri gerekmez. Semt adı Android `Geocoder` ile bulunur.
- Enerji: saat dakika başlarında güncellenir; saat, şarj ve hava durumu yalnızca ekran görünürken çalışır; arka plan servisi yok.
- İmza: depodaki `app/debug.keystore` (sabit debug anahtarı, güncellemeler üzerine kurulsun diye).
- Kod: `MainActivity.kt` (ekran geçişleri), `Actions.kt` (arama/uygulama açma), `SettingsStore.kt` (ayar), `Weather.kt` (hava durumu), `LockService.kt` (ekranı kapat), `DeviceLocation.kt` (konum), `ScreenLock.kt` (iki yollu kilit), `PhoneContacts.kt` (rehber), `InstalledApps.kt` (kutulara uygulama atama), `BadgeService.kt` (kırmızı sayılar), `ui/` (ekranlar ve tema).

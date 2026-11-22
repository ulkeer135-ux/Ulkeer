# Amperimetre

Şarj akımını (mA), gücü (W), voltajı ve sıcaklığı gösteren Android uygulaması.
Ampere'den farkı: ölçüm **ekran açılıp kapandığında sıfırlanmaz**.

## Nasıl çalışır
- Ön plan servisi her 1 sn'de `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` okur.
- Şarj sırasında `PARTIAL_WAKE_LOCK` tutulur, ekran kapalıyken de ölçüm sürer.
- Oturum sadece kablo takılınca başlar, çıkarılınca biter (ort/min/maks, dolan mAh, süre, tahmini dolma).
- Oturum verileri diske yazılır; servis öldürülse bile kaldığı yerden devam eder.
- Canlı değer bildirimde de görünür.

## APK derleme
**GitHub (bilgisayar gerekmez):** Bu klasörü yeni bir GitHub deposuna yükleyin →
Actions sekmesi → "APK derle" → bitince "Amperimetre-apk" dosyasını indirin.

**Android Studio:** Klasörü açın → Build → Build APK(s).

Min. Android 8.0, hedef Android 14.

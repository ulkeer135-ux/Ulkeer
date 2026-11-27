package com.annem.launcher.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.annem.launcher.BadgeService
import com.annem.launcher.Badges
import com.annem.launcher.DeviceLocation
import com.annem.launcher.LockService
import com.annem.launcher.PhoneContacts
import com.annem.launcher.ScreenLock
import com.annem.launcher.Slots
import com.annem.launcher.SettingsStore
import com.annem.launcher.StartupStats
import com.annem.launcher.WeatherApi
import kotlinx.coroutines.launch

/** Yakınlar için ayarlar: saate 3 sn basılı tutunca açılır. */
@Composable
fun SettingsScreen(
    store: SettingsStore,
    dreams: com.annem.launcher.DreamStore,
    onBack: () -> Unit,
    onPickApp: (slotId: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Ayarlardan geri dönünce (ON_RESUME) servis durumunu yeniden oku.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val lockEnabled = remember(lifecycleState) { LockService.isEnabled(context) }
    val badgeAccess = remember(lifecycleState) { BadgeService.isEnabled(context) }
    var adminTick by remember { mutableIntStateOf(0) }
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        ScreenLock.reportActivation(context)
        adminTick++
    }
    val adminActive = remember(lifecycleState, adminTick) { ScreenLock.isAdminActive(context) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val contactsGranted = remember(lifecycleState, permissionTick) { PhoneContacts.hasPermission(context) }
    val callGranted = remember(lifecycleState, permissionTick) {
        context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionTick++
    }
    val locationGranted = remember(lifecycleState) { DeviceLocation.hasPermission(context) }
    val keepAlive = remember(lifecycleState) {
        context.getSystemService(android.os.PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    var cityDraft by remember { mutableStateOf(store.weatherCity) }
    var cityMessage by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BigBackButton(label = "Ana Ekran", onClick = onBack)
        Text("Ayarlar", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
        Text(
            "Bu ekran yakınlar içindir. Saate 3 saniye basılı tutarak açılır.",
            fontSize = 16.sp,
            color = Palette.InkSoft,
        )

        // --- Kırmızı sayılar ---
        SectionTitle("Kırmızı bildirim sayıları")
        Hint(
            "WhatsApp kutusunda okunmamış mesaj, Telefon kutusunda cevapsız arama sayısı görünür. " +
                "Uygulama bildirimleri yalnızca sayar; mesaj içeriğini okumaz, saklamaz, göndermez."
        )
        StatusRow("Bildirim erişimi", badgeAccess, onText = "Açık", offText = "Kapalı")
        if (!badgeAccess) {
            Hint("Açılan ekranda \"Annem – Bildirim sayıları\"ı aç ve izin ver.")
            OutlinedButton(onClick = { openSettings(context, BadgeService.settingsIntent(context)) }) {
                Text("Bildirim erişimini aç", fontSize = 18.sp)
            }
        }
        if (badgeAccess) {
            Hint(
                if (BadgeService.connected) {
                    "Şu an sayılan: WhatsApp ${Badges.whatsapp}, cevapsız arama ${Badges.missedCalls}."
                } else {
                    "Erişim açık ama telefon dinleyiciyi henüz başlatmadı. Ana ekrana dönüp tekrar bak; " +
                        "değişmezse erişimi kapatıp yeniden aç."
                }
            )
        }
        StatusRow("Ana ekranda göster", store.badgesEnabled, onText = "Evet", offText = "Hayır")
        OutlinedButton(onClick = { store.updateBadgesEnabled(!store.badgesEnabled) }) {
            Text(if (store.badgesEnabled) "Sayıları gizle" else "Sayıları göster", fontSize = 18.sp)
        }

        HorizontalDivider()

        // --- Ekranı kapat ---
        SectionTitle("Ekranı Kapat düğmesi")
        val lockReady = lockEnabled || adminActive
        StatusRow("Durum", lockReady, onText = "Çalışıyor", offText = "Kurulmadı")
        if (lockReady) {
            OutlinedButton(onClick = { ScreenLock.lock(context) }) { Text("Dene (ekran kapanır)", fontSize = 18.sp) }
        }

        SubTitle("Yol 1 – Erişilebilirlik (önerilen)")
        if (!LockService.isSupported) {
            Hint("Bu telefonun Android sürümü (9'dan eski) bu yolu desteklemiyor; Yol 2'yi kullan.")
        } else {
            Hint("Güç tuşu gibi çalışır; parmak izi kilidi etkilenmez.")
            StatusRow("Erişilebilirlik", lockEnabled, onText = "Açık", offText = "Kapalı")
            if (!lockEnabled) {
                Hint(
                    "1) Aşağıdaki düğmeyle Erişilebilirlik ayarlarını aç.\n" +
                        "2) \"İndirilen uygulamalar\" ya da \"Yüklü hizmetler\" altında \"Annem – Ekranı kapat\"ı bul ve aç.\n" +
                        "Seçenek gri ve açılmıyorsa: \"Uygulama bilgisi\"ni aç, sağ üstteki ⋮ menüsünden " +
                        "\"Kısıtlanmış ayarlara izin ver\"i seç, sonra 1. adımı tekrarla. (Bu menü, gri seçeneğe bir kez " +
                        "dokunduktan sonra görünür.)\n" +
                        "OPPO/realme: Ayarlar › Ek ayarlar › Erişilebilirlik › İndirilen uygulamalar."
                )
                OutlinedButton(onClick = { openSettings(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text("Erişilebilirlik ayarlarını aç", fontSize = 18.sp)
                }
                OutlinedButton(onClick = { openAppInfo(context) }) {
                    Text("Uygulama bilgisi", fontSize = 18.sp)
                }
            } else {
                Hint(
                    "Bazı telefonlarda (OPPO, realme, Xiaomi) servis bir süre sonra kapanabilir. Olursa: " +
                        "Uygulama bilgisi › Pil kullanımı › kısıtlamayı kaldır."
                )
            }
        }

        SubTitle("Yol 2 – Cihaz yöneticisi (tek onay)")
        Hint(
            "Yol 1 açılamazsa kullan. Not: Parmak izi kullanılıyorsa, ekran bu yolla kapandıktan sonraki ilk " +
                "açılışta telefon PIN/desen isteyebilir."
        )
        StatusRow("Cihaz yöneticisi", adminActive, onText = "Açık", offText = "Kapalı")
        if (!adminActive) {
            OutlinedButton(onClick = { ScreenLock.launchAdminActivation(context, adminLauncher) }) {
                Text("Cihaz yöneticisini aç", fontSize = 18.sp)
            }
        } else {
            Hint("Uygulamayı telefondan silmeden önce bunu kapatman gerekir.")
            OutlinedButton(onClick = {
                ScreenLock.removeAdmin(context)
                adminTick++
            }) { Text("Cihaz yöneticisini kapat", fontSize = 18.sp) }
        }

        HorizontalDivider()

        // --- Düzen ---
        SectionTitle("Ana ekran düzeni")
        Hint("9 kutu: daha çok uygulama. 6 kutu: daha büyük kutular ve yazılar (görmesi zor olanlar için).")
        StatusRow("Şu an", true, onText = "${store.tileCount} kutu", offText = "")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                enabled = store.tileCount != 9,
                onClick = { store.updateTileCount(9) },
            ) { Text("9 kutu", fontSize = 18.sp) }
            OutlinedButton(
                enabled = store.tileCount != 6,
                onClick = { store.updateTileCount(6) },
            ) { Text("6 büyük kutu", fontSize = 18.sp) }
        }

        HorizontalDivider()

        // --- Kutulara uygulama ata ---
        SectionTitle("Kutulara uygulama ata")
        Hint("Ana ekrandaki üç kutuya telefondaki istediğin uygulamayı koyabilirsin. Boş kutuya ana ekranda dokunarak da ekleyebilirsin. Kutuda uygulamanın simgesi ve aşağıda yazdığın ad görünür.")
        Hint("Kutular soldan sağa, yukarıdan aşağıya numaralanır (Kişiler, Telefon ve WhatsApp'tan sonra).")
        Slots.visible(store.tileCount == 9).forEach { id -> SlotSetting(store, id, Slots.title(id), onPickApp) }
        Spacer(Modifier.height(8.dp))
        Text("\"Daha fazla\" çekmecesi", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        Hint("Ana ekranda Ekranı Kapat düğmesinin üstündeki \"Daha fazla\"ya dokununca ya da yukarı kaydırınca açılan 9 kutu.")
        Slots.MORE.forEach { id -> SlotSetting(store, id, Slots.title(id), onPickApp) }

        HorizontalDivider()

        // --- Kişiler ---
        SectionTitle("Kişiler ve arama")
        StatusRow("Rehber izni", contactsGranted, onText = "Verildi", offText = "Verilmedi")
        StatusRow("Doğrudan arama izni", callGranted, onText = "Verildi", offText = "Verilmedi")
        Hint(
            "Telefonun rehberinde yıldızla favorilere eklenen kişiler \"Kişiler\" ekranında en üstte görünür. " +
                "Arama izni yoksa \"Ara\" telefon uygulamasını numara yazılı açar."
        )
        if (!contactsGranted || !callGranted) {
            OutlinedButton(onClick = {
                contactsLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
            }) { Text("İzinleri ver", fontSize = 18.sp) }
        }

        HorizontalDivider()

        // --- Hava durumu ---
        SectionTitle("Hava durumu")
        run {
            val at = remember(lifecycleState) { store.lastWeatherAt() }
            val fmt = remember { java.text.SimpleDateFormat("d MMMM HH:mm", java.util.Locale.forLanguageTag("tr-TR")) }
            Text(
                if (at > 1_000L) "Son güncelleme: " + fmt.format(java.util.Date(at)) else "Henüz güncellenmedi",
                fontSize = 18.sp, color = Palette.Ink, fontWeight = FontWeight.Bold,
            )
            Hint("Ana ekran açıkken 30 dakikada bir kendiliğinden yenilenir; ekran kapalıyken internet harcamaz, açılınca eskiyse hemen yenilenir.")
            var refreshAsked by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { store.invalidateWeather(); refreshAsked = true }) {
                Text("Şimdi yenile", fontSize = 18.sp)
            }
            if (refreshAsked) Hint("Ana ekrana dönünce hemen yenilenecek.")
        }
        Spacer(Modifier.height(8.dp))
        Text("Konum", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        if (store.useDeviceLocation) {
            StatusRow("Telefonun konumu", locationGranted, onText = "Kullanılıyor", offText = "İzin yok")
            Hint(
                if (locationGranted) {
                    "Hava durumu telefonun bulunduğu yere göre gösterilir" +
                        (store.devicePlaceName?.let { " (şu an: $it)" } ?: "") +
                        ". Telefonun Konum ayarı kapalıysa aşağıdaki şehir kullanılır."
                } else {
                    "Konum izni verilmedi; şimdilik ${store.weatherCity} gösteriliyor."
                }
            )
            if (!locationGranted) {
                OutlinedButton(onClick = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                    Text("Konum iznini ver", fontSize = 18.sp)
                }
                Hint("İzin penceresi açılmıyorsa: Uygulama bilgisi › İzinler › Konum › \"Uygulama kullanılırken izin ver\".")
                OutlinedButton(onClick = { openAppInfo(context) }) { Text("Uygulama bilgisi", fontSize = 18.sp) }
            }
        } else {
            StatusRow("Sabit şehir", true, onText = store.weatherCity, offText = "")
            OutlinedButton(onClick = {
                store.updateUseDeviceLocation(true)
                if (!locationGranted) locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }) { Text("Telefonun konumunu kullan", fontSize = 18.sp) }
        }

        Hint("Ya da sabit bir şehir/semt yaz (ör. Kadıköy, Ankara). Kaydedince telefonun konumu yerine bu kullanılır.")
        OutlinedTextField(
            value = cityDraft,
            onValueChange = {
                cityDraft = it
                cityMessage = null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            enabled = !searching && cityDraft.isNotBlank(),
            onClick = {
                scope.launch {
                    searching = true
                    cityMessage = try {
                        val place = WeatherApi.geocode(cityDraft.trim())
                        if (place == null) {
                            "\"${cityDraft.trim()}\" bulunamadı"
                        } else {
                            store.setPlace(place)
                            cityDraft = place.name
                            "Kaydedildi: ${place.label}"
                        }
                    } catch (e: Exception) {
                        "İnternete bağlanılamadı, tekrar dene"
                    }
                    searching = false
                }
            },
        ) { Text(if (searching) "Aranıyor…" else "Sabit şehir olarak kaydet", fontSize = 18.sp) }
        cityMessage?.let { Hint(it) }

        HorizontalDivider()

        // --- Rüya Tabirleri ---
        SectionTitle("Rüya Tabirleri")
        Hint(
            "Annen rüyasını sesle anlatır; yorumu Groq yapay zekâsı yazar ve sesli okunur. Ücretsizdir, " +
                "kredi kartı istemez. Ana ekranda bir kutuya \"Rüya Tabirleri\"ni ata.\n" +
                "1. Aşağıdaki düğmeyle console.groq.com'u aç, Google hesabınla gir.\n" +
                "2. \"Create API Key\" de, \"gsk_\" ile başlayan anahtarı kopyala, buraya yapıştır."
        )
        OutlinedButton(onClick = {
            openSettings(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys")))
        }) { Text("Groq anahtarı al", fontSize = 18.sp) }
        var groqKey by remember { mutableStateOf(dreams.groqKey.value) }
        OutlinedTextField(
            value = groqKey,
            onValueChange = { groqKey = it },
            label = { Text("Groq API anahtarı") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = { dreams.updateGroqKey(groqKey) }) { Text("Kaydet", fontSize = 18.sp) }
        StatusRow("Rüya yorumu", dreams.ready, onText = "Hazır", offText = "Anahtar yok")

        Spacer(Modifier.height(8.dp))
        Text("Doğal ses (isteğe bağlı)", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        Hint(
            "Yorumu daha doğal bir sesle okumak için ücretsiz Google Gemini anahtarı girilebilir " +
                "(aistudio.google.com › Create API key). Girilmezse ya da günlük sınırı dolarsa " +
                "telefonun kendi sesiyle hemen okunur."
        )
        OutlinedButton(onClick = {
            openSettings(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
        }) { Text("Ses anahtarı al", fontSize = 18.sp) }
        var voiceKey by remember { mutableStateOf(dreams.apiKey.value) }
        OutlinedTextField(
            value = voiceKey,
            onValueChange = { voiceKey = it },
            label = { Text("Gemini ses anahtarı") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = { dreams.updateApiKey(voiceKey) }) { Text("Ses anahtarını kaydet", fontSize = 18.sp) }
        StatusRow(
            "Okuma sesi",
            dreams.naturalVoice.value && dreams.apiKey.value.isNotBlank(),
            onText = "Doğal ses",
            offText = "Telefonun sesi",
        )
        if (dreams.apiKey.value.isNotBlank()) {
            OutlinedButton(onClick = { dreams.updateNaturalVoice(!dreams.naturalVoice.value) }) {
                Text(if (dreams.naturalVoice.value) "Telefonun sesini kullan" else "Doğal sesi kullan", fontSize = 18.sp)
            }
        }

        if (dreams.history.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            var confirmClear by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { confirmClear = true }) {
                Text("Tüm rüyaları sil (${dreams.history.size})", fontSize = 18.sp, color = Color(0xFFC62828))
            }
            if (confirmClear) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text("Bütün rüyalar silinsin mi?") },
                    text = { Text("Kayıtlı ${dreams.history.size} rüya, yorumları ve sesleri silinecek. Geri alınamaz.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { dreams.clearAll(); confirmClear = false }) {
                            Text("Hepsini sil", color = Color(0xFFC62828))
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") }
                    },
                )
            }
        }

        HorizontalDivider()

        // --- Arka planda kapanmasın ---
        SectionTitle("Annem arka planda kapanmasın")
        Hint(
            "Telefon pil için Annem'i kapatırsa, ana ekran tuşunda bir an duvar kağıdı görünür " +
                "ve Annem baştan açılır. Bunu önlemek için iki adım:"
        )
        Text(
            if (keepAlive) "1. Pil kısıtlaması kaldırıldı ✓" else "1. Pil kısıtlaması açık",
            fontSize = 18.sp,
            color = if (keepAlive) Color(0xFF2E7D32) else Palette.Ink,
            fontWeight = FontWeight.Bold,
        )
        if (!keepAlive) {
            OutlinedButton(onClick = {
                try {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.fromParts("package", context.packageName, null),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                    openSettings(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }) { Text("Pil kısıtlamasını kaldır", fontSize = 18.sp) }
        }
        Text("2. Uygulama bilgisi › Pil kullanımı › \"Arka planda etkinliğe izin ver\" açık olsun.", fontSize = 16.sp, color = Palette.Ink)
        OutlinedButton(onClick = { openAppInfo(context) }) { Text("Uygulama bilgisini aç", fontSize = 18.sp) }

        HorizontalDivider()

        // --- Ekranın kendiliğinden kapanması ---
        SectionTitle("Ekranın kendiliğinden kapanması")
        run {
            val act = context as? android.app.Activity
            val timeout = remember(lifecycleState) { com.annem.launcher.ScreenAwake.timeoutMs(context) }
            val stayOn = remember(lifecycleState) { com.annem.launcher.ScreenAwake.stayOnWhilePlugged(context) }
            val annemOn = remember(lifecycleState) { act?.let { com.annem.launcher.ScreenAwake.annemKeepsOn(it) } ?: false }
            Text("Telefonun kapanma süresi: " + com.annem.launcher.ScreenAwake.describeTimeout(timeout), fontSize = 18.sp, color = Palette.Ink)
            Text(
                if (stayOn) "\"Uyanık kal\" AÇIK: telefon şarjdayken ekran hiç kapanmaz." else "\"Uyanık kal\" kapalı ✓",
                fontSize = 18.sp,
                color = if (stayOn) Color(0xFFC62828) else Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (annemOn) "Annem ekranı açık tutuyor (bu bir hata, bildir)." else "Annem ekranı açık tutmuyor ✓",
                fontSize = 18.sp,
                color = if (annemOn) Color(0xFFC62828) else Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold,
            )
            Hint("Süreyi telefonun Ayarlar › Ekran bölümünden değiştirebilirsin. Rüya Tabirleri açıkken ekran bilerek açık kalır.")
            OutlinedButton(onClick = { openSettings(context, Intent(Settings.ACTION_DISPLAY_SETTINGS)) }) {
                Text("Ekran ayarlarını aç", fontSize = 18.sp)
            }
            if (stayOn) {
                OutlinedButton(onClick = { openSettings(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }) {
                    Text("Geliştirici seçeneklerini aç", fontSize = 18.sp)
                }
            }
        }

        HorizontalDivider()

        // --- Açılış ölçümleri ---
        SectionTitle("Açılış ölçümleri")
        Hint(
            "Ana ekranın kaç milisaniyede göründüğü. \"Sıfırdan açılış\": telefon Annem'i arka planda " +
                "kapatmış. \"Geri dönüş\": Annem bellekteydi. 300 ms altı akıcı sayılır."
        )
        if (StartupStats.events.isEmpty()) {
            Hint("Henüz ölçüm yok. Bir uygulamaya girip ana ekran tuşuna bas, sonra buraya bak.")
        } else {
            StartupStats.events.forEach { Text(it, fontSize = 16.sp, color = Palette.Ink) }
        }

        HorizontalDivider()

        // --- Ana ekran ---
        SectionTitle("Ana ekran")
        Hint("Bu uygulamayı varsayılan ana ekran yapınca Ana Sayfa tuşu hep buraya döner.")
        OutlinedButton(onClick = { openSettings(context, Intent(Settings.ACTION_HOME_SETTINGS)) }) {
            Text("Varsayılan ana ekranı seç", fontSize = 18.sp)
        }

        Spacer(Modifier.height(12.dp))
        Hint(
            "Hızlı ana ekran tuşu: ana ekran tuşuna basınca Annem, telefonun 1 saniyelik geçişini " +
                "beklemeden hemen açılır. \"Ekranı Kapat\" Yol 1 (Erişilebilirlik) açık olmalı. " +
                "Açıkken ana ekran tuşuna uzun basma (Google Asistan) çalışmaz."
        )
        StatusRow("Hızlı ana ekran tuşu", store.fastHome && lockEnabled, onText = "Açık", offText = "Kapalı")
        if (!lockEnabled) {
            Hint("Önce yukarıdaki \"Ekranı Kapat\" bölümünden Yol 1'i aç.")
        }
        if (lockEnabled) {
            val connected = remember(lifecycleState) { LockService.isConnected }
            val keys = remember(lifecycleState) { LockService.canFilterKeys }
            val count = remember(lifecycleState) { LockService.homeKeyCount(context) }
            val fallbacks = remember(lifecycleState) { LockService.homeFallbackCount(context) }
            Hint(
                "Kontrol: servis ${if (connected) "çalışıyor" else "çalışmıyor"} · " +
                    "tuş yetkisi ${if (keys) "var" else "yok"} · " +
                    "yakalanan ana ekran tuşu: $count · yedek yol: $fallbacks"
            )
        }
        OutlinedButton(onClick = { store.updateFastHome(!store.fastHome) }) {
            Text(if (store.fastHome) "Hızlı ana ekranı kapat" else "Hızlı ana ekranı aç", fontSize = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        Hint(
            "Ekranı Kapat'ta arka plan uygulamalarını kapat: düğmeye basınca telefon bir an son " +
                "uygulamalar ekranını açar, \"Tümünü temizle\"ye basar, sonra ekranı kapatır " +
                "(yaklaşık 1,5 sn). \"Ekranı Kapat\" Yol 1 (Erişilebilirlik) açık olmalı."
        )
        val canRead = remember(lifecycleState) { LockService.canReadScreen }
        StatusRow("Arka plan uygulamalarını kapat", store.clearOnLock && lockEnabled && canRead, onText = "Açık", offText = "Kapalı")
        if (lockEnabled && store.clearOnLock && !canRead) {
            Hint("Telefon bu yetkiyi henüz vermedi: Erişilebilirlik'te Annem'i bir kez kapatıp yeniden aç.")
        }
        if (lockEnabled) {
            val (ok, miss) = remember(lifecycleState) { LockService.clearCounts(context) }
            Hint("Kontrol: temizlendi $ok · bulunamadı $miss")
        }
        OutlinedButton(onClick = { store.updateClearOnLock(!store.clearOnLock) }) {
            Text(if (store.clearOnLock) "Arka plan kapatmayı kapat" else "Arka plan kapatmayı aç", fontSize = 18.sp)
        }
        if (store.clearOnLock) {
            Spacer(Modifier.height(8.dp))
            Hint(
                "Boşta kalınca: ana ekran dokunulmadan beklerken, telefon ekranı kapatmadan hemen önce " +
                    "arka plan uygulamaları da kapatılır (Annem'den uygulama açılmışsa)."
            )
            StatusRow("Boşta kalınca da kapat", store.clearOnIdle, onText = "Açık", offText = "Kapalı")
            OutlinedButton(onClick = { store.updateClearOnIdle(!store.clearOnIdle) }) {
                Text(if (store.clearOnIdle) "Boşta kapatmayı kapat" else "Boşta kapatmayı aç", fontSize = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
            val hours = store.clearIntervalHours
            Hint(
                "Belli aralıklarla: bir uygulamadan ana ekrana dönünce, son temizlikten bu süre geçtiyse " +
                    "arka plan uygulamaları kendiliğinden kapatılır (ekran kapanmaz)."
            )
            StatusRow(
                "Aralıklı temizlik",
                hours > 0,
                onText = "$hours saatte bir",
                offText = "Kapalı",
            )
            OutlinedButton(onClick = {
                val next = when (hours) { 0 -> 1; 1 -> 3; 3 -> 6; else -> 0 }
                store.updateClearIntervalHours(next)
            }) {
                Text("Aralığı değiştir (Kapalı → 1 → 3 → 6 saat)", fontSize = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
            Hint(
                "Bazı uygulamalar kapanmasın (ör. WhatsApp, müzik): telefonun son uygulamalar ekranını aç, " +
                    "o uygulamanın kartını aşağı çek ya da üstündeki menüden \"Kilitle\"ye bas. " +
                    "Kilitli uygulamaları \"Tümünü temizle\" kapatmaz; Annem de aynı düğmeyi kullandığı için onlara dokunmaz."
            )
        }
        Spacer(Modifier.height(12.dp))
        Hint(
            "Ana ekran tuşunda telefon bir an duvar kağıdını gösteriyor (telefonun kendi geçiş " +
                "animasyonu). Duvar kağıdını Annem'in zeminiyle aynı yaparsan bu an fark edilmez. " +
                "Kilit ekranı değişmez."
        )
        var wallpaperResult by remember { mutableStateOf<Boolean?>(null) }
        OutlinedButton(onClick = {
            scope.launch { wallpaperResult = com.annem.launcher.AnnemWallpaper.apply(context) }
        }) {
            Text("Duvar kağıdını Annem'e uydur", fontSize = 18.sp)
        }
        when (wallpaperResult) {
            true -> Text("Duvar kağıdı ayarlandı ✓", fontSize = 18.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
            false -> Text("Duvar kağıdı ayarlanamadı.", fontSize = 18.sp, color = Color(0xFFC62828))
            null -> {}
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun openAppInfo(context: Context) {
    openSettings(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}

private fun openSettings(context: Context, intent: Intent): Boolean {
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        false
    }
}

@Composable
private fun SlotSetting(
    store: SettingsStore,
    slotId: String,
    title: String,
    onPickApp: (String) -> Unit,
) {
    val slot = store.slot(slotId)
    SubTitle(title)
    Hint(if (slot == null) "Şu an: boş" else "Şu an: ${slot.label}")
    if (slot != null) {
        var labelDraft by remember(slot.packageName) { mutableStateOf(slot.label) }
        OutlinedTextField(
            value = labelDraft,
            onValueChange = { labelDraft = it },
            label = { Text("Kutudaki yazı") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            enabled = labelDraft.isNotBlank() && labelDraft.trim() != slot.label,
            onClick = { store.setSlotLabel(slotId, labelDraft.trim()) },
        ) { Text("Yazıyı kaydet", fontSize = 18.sp) }
    }
    OutlinedButton(onClick = { onPickApp(slotId) }) {
        Text(if (slot == null) "Uygulama seç" else "Başka uygulama seç", fontSize = 18.sp)
    }
    if (slot != null) {
        OutlinedButton(onClick = { store.clearSlot(slotId) }) { Text("Kutuyu boşalt", fontSize = 18.sp) }
    }
}

@Composable
private fun SubTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 16.sp, color = Palette.InkSoft, lineHeight = 22.sp)
}

@Composable
private fun StatusRow(label: String, ok: Boolean, onText: String, offText: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(
            if (ok) onText else offText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (ok) Palette.Green else Palette.Red,
        )
    }
}

@Composable
fun BigBackButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(64.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, Palette.Ink),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Palette.Ink),
        contentPadding = PaddingValues(start = 12.dp, end = 22.dp),
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
    }
}

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
import com.annem.launcher.WeatherApi
import kotlinx.coroutines.launch

/** Yakınlar için ayarlar: saate 3 sn basılı tutunca açılır. */
@Composable
fun SettingsScreen(store: SettingsStore, onBack: () -> Unit, onPickApp: (slotId: String) -> Unit) {
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

        // --- Kutulara uygulama ata ---
        SectionTitle("Kutulara uygulama ata")
        Hint("Ana ekrandaki üç kutuya telefondaki istediğin uygulamayı koyabilirsin. Boş kutuya ana ekranda dokunarak da ekleyebilirsin. Kutuda uygulamanın simgesi ve aşağıda yazdığın ad görünür.")
        Slots.ALL.forEach { id -> SlotSetting(store, id, Slots.title(id), onPickApp) }

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
        SectionTitle("Hava durumu konumu")
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

        // --- Ana ekran ---
        SectionTitle("Ana ekran")
        Hint("Bu uygulamayı varsayılan ana ekran yapınca Ana Sayfa tuşu hep buraya döner.")
        OutlinedButton(onClick = { openSettings(context, Intent(Settings.ACTION_HOME_SETTINGS)) }) {
            Text("Varsayılan ana ekranı seç", fontSize = 18.sp)
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

private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

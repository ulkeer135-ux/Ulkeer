package com.annem.launcher.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.annem.launcher.BadgeService
import com.annem.launcher.DeviceLocation
import com.annem.launcher.LockService
import com.annem.launcher.PhoneContacts

/**
 * Kurulum sihirbazı: yeni telefonda Annem'i adım adım hazırlar.
 * Her adım telefonun ilgili ayar ekranını açar; geri dönünce durum kendiliğinden yenilenir.
 * Hiçbir adım zorunlu değildir ("Atla"); sihirbaz Ayarlar'dan yeniden açılabilir.
 */
object SetupWizard {
    private const val KEY_DONE = "setup_wizard_done"

    fun isDone(context: Context): Boolean =
        context.getSharedPreferences("annem", Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    fun markDone(context: Context) {
        context.getSharedPreferences("annem", Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
    }

    /** İlk açılışta gösterilsin mi: sihirbaz bitmemiş ve Annem henüz ana ekran değilse (yeni telefon). */
    fun shouldAutoShow(context: Context): Boolean = !isDone(context) && !isDefaultHome(context)

    fun isDefaultHome(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == context.packageName
    }
}

private enum class Step { Welcome, Home, Lock, Battery, Badges, Contacts, Location, Mic, Done }

private fun granted(context: Context, permission: String) =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }
}

private fun appDetails(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

@Composable
fun SetupWizardScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var tick by remember { mutableIntStateOf(0) }
    var index by remember { mutableIntStateOf(0) }
    var denied by remember { mutableIntStateOf(0) }

    // Ayar ekranından geri dönünce (ON_RESUME) ya da izin yanıtından sonra durumları yeniden oku.
    val status = remember(lifecycleState, tick) {
        mapOf(
            Step.Home to SetupWizard.isDefaultHome(context),
            Step.Lock to LockService.isEnabled(context),
            Step.Battery to (
                context.getSystemService(android.os.PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(context.packageName) == true
                ),
            Step.Badges to BadgeService.isEnabled(context),
            Step.Contacts to (PhoneContacts.hasPermission(context) && granted(context, Manifest.permission.CALL_PHONE)),
            Step.Location to DeviceLocation.hasPermission(context),
            Step.Mic to granted(context, Manifest.permission.RECORD_AUDIO),
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { !it }) denied++
        tick++
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }

    val steps = Step.entries
    val step = steps[index]
    LaunchedEffect(index) { denied = 0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Üst: ilerleme
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Kurulum · ${index + 1}/${steps.size}",
                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft,
                modifier = Modifier.weight(1f),
            )
        }
        LinearProgressIndicator(
            progress = { (index + 1f) / steps.size },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = Palette.Green,
            trackColor = Color.White,
        )

        val content = stepContent(step)
        // Büyük simge
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(116.dp)
                .clip(CircleShape)
                .background(content.bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(content.icon, contentDescription = null, tint = content.tint, modifier = Modifier.size(62.dp))
        }
        Text(content.title, fontSize = 30.sp, fontWeight = FontWeight.Black, color = Palette.Ink, lineHeight = 36.sp)
        Text(content.text, fontSize = 19.sp, color = Palette.Ink, lineHeight = 27.sp)

        val ok = status[step]
        if (ok != null) StatusChip(ok)

        // Adımın düğmeleri
        when (step) {
            Step.Welcome -> Unit
            Step.Home -> if (ok == false) BigAction("Annem'i ana ekran yap") {
                val rm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.getSystemService(android.app.role.RoleManager::class.java)
                } else null
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    try {
                        roleLauncher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME))
                    } catch (e: Exception) {
                        open(context, Intent(Settings.ACTION_HOME_SETTINGS))
                    }
                } else {
                    open(context, Intent(Settings.ACTION_HOME_SETTINGS))
                }
            }
            Step.Lock -> if (ok == false) {
                BigAction("Erişilebilirlik ayarını aç") { open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                Tip(
                    "Açılan listede \"Annem\"i bul ve aç. \"Kısıtlanmış ayar\" uyarısı çıkarsa aşağıdaki " +
                        "düğmeye bas, sağ üstteki ⋮ menüsünden \"Kısıtlanmış ayarlara izin ver\"i seç ve yeniden dene."
                )
                SmallAction("Uygulama bilgisi (kısıtlanmış ayar)") { open(context, appDetails(context)) }
            }
            Step.Battery -> if (ok == false) {
                BigAction("Pil kısıtlamasını kaldır") {
                    open(
                        context,
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                    )
                }
                Tip(
                    "Redmi/Xiaomi'de ayrıca: Uygulama bilgisi › Otomatik başlatma açık, Pil tasarrufu " +
                        "\"Kısıtlama yok\". realme/OPPO'da: Pil kullanımı › \"Arka planda etkinliğe izin ver\"."
                )
                SmallAction("Uygulama bilgisi") { open(context, appDetails(context)) }
            }
            Step.Badges -> if (ok == false) {
                BigAction("Bildirim erişimini aç") { open(context, BadgeService.settingsIntent(context)) }
                Tip("Açılan ekranda \"Annem – Bildirim sayıları\"nı aç. Annem bildirimleri yalnızca sayar, içeriğini okumaz.")
            }
            Step.Contacts -> if (ok == false) {
                BigAction("Kişiler ve arama izni ver") {
                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
                }
                if (denied > 0) DeniedHelp(context)
            }
            Step.Location -> if (ok == false) {
                BigAction("Konum izni ver") {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                Tip("İstemezsen konum vermeden de olur: Ayarlar › Hava durumu bölümünden şehir adı yazılabilir.")
                if (denied > 0) DeniedHelp(context)
            }
            Step.Mic -> if (ok == false) {
                BigAction("Mikrofon izni ver") {
                    permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
                if (denied > 0) DeniedHelp(context)
            }
            Step.Done -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    steps.filter { status[it] != null }.forEach { s ->
                        SummaryRow(stepContent(s).short, status[s] == true)
                    }
                }
                Tip(
                    "Eksik kalanları istediğin zaman tamamlayabilirsin: saate 3 saniye basılı tut › Ayarlar › " +
                        "\"Kurulum sihirbazı\". Rüya Tabirleri için Groq anahtarı da Ayarlar'dan girilir."
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Alt gezinme
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (index > 0) {
                OutlinedButton(
                    onClick = { index-- },
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("Geri", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Palette.Ink) }
            }
            val last = step == Step.Done
            Button(
                onClick = {
                    if (last) {
                        SetupWizard.markDone(context)
                        onFinish()
                    } else {
                        index++
                    }
                },
                modifier = Modifier.weight(1f).height(60.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Ink),
            ) {
                Text(
                    when {
                        last -> "Bitir"
                        step == Step.Welcome -> "Başla"
                        ok == false -> "Atla"
                        else -> "İleri"
                    },
                    fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White,
                )
            }
        }
        if (step != Step.Done) {
            OutlinedButton(
                onClick = { index = steps.lastIndex },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Özete geç", fontSize = 17.sp, color = Palette.InkSoft) }
        }
    }
}

private class StepContent(
    val title: String,
    val short: String,
    val text: String,
    val icon: ImageVector,
    val bg: Color,
    val tint: Color,
)

private fun stepContent(step: Step): StepContent = when (step) {
    Step.Welcome -> StepContent(
        "Hoş geldiniz",
        "Hoş geldiniz",
        "Birkaç adımda telefonu Annem için hazırlayalım. Her adımda telefon kendi ayar ekranını açar; " +
            "orada izni verip geri tuşuyla buraya dönmen yeterli. İstemediğin adımı atlayabilirsin.",
        Icons.Rounded.Favorite, Color(0xFFFFE1DC), Color(0xFFB4361E),
    )
    Step.Home -> StepContent(
        "Ana ekran yap",
        "Annem ana ekran",
        "Ana ekran tuşuna basınca her zaman Annem gelsin. Açılan pencerede \"Annem\"i seç.",
        Icons.Rounded.Home, Palette.ContactsBg, Palette.ContactsIcon,
    )
    Step.Lock -> StepContent(
        "\"Ekranı Kapat\" düğmesi",
        "Ekranı Kapat (Erişilebilirlik)",
        "Ana ekrandaki büyük düğmeyle ekranın kapanabilmesi için Erişilebilirlik'te Annem'i aç. " +
            "Annem bu yetkiyle yalnızca ekranı kapatır ve ana ekran tuşunu hızlandırır.",
        Icons.Rounded.Bedtime, Color(0xFF2B2118), Color(0xFFFFE08A),
    )
    Step.Battery -> StepContent(
        "Annem kapanmasın",
        "Pil kısıtlaması yok",
        "Telefon pil tasarrufu için ana ekranı arada kapatmasın; açılan soruya \"İzin ver\" de.",
        Icons.Rounded.BatteryFull, Palette.PhoneBg, Palette.PhoneIcon,
    )
    Step.Badges -> StepContent(
        "Kırmızı sayılar",
        "Bildirim sayıları",
        "WhatsApp kutusunda okunmamış mesaj, Telefon kutusunda cevapsız arama sayısı görünsün.",
        Icons.Rounded.Notifications, Color(0xFFFFE6CC), Color(0xFFC2560C),
    )
    Step.Contacts -> StepContent(
        "Kişiler ve arama",
        "Kişiler ve arama izni",
        "Kişiler ekranında rehber görünsün ve bir dokunuşla arama yapılabilsin.",
        Icons.Rounded.Contacts, Palette.ContactsBg, Palette.ContactsIcon,
    )
    Step.Location -> StepContent(
        "Hava durumu konumu",
        "Konum (hava durumu)",
        "Ana ekrandaki hava durumu bulunduğun yere göre gösterilsin.",
        Icons.Rounded.LocationOn, Color(0xFFDDEBF5), Color(0xFF2F6FC0),
    )
    Step.Mic -> StepContent(
        "Rüya Tabirleri mikrofonu",
        "Mikrofon (Rüya Tabirleri)",
        "Rüyayı sesle anlatabilmek için mikrofon izni gerekir.",
        Icons.Rounded.Mic, Color(0xFFFDE2E2), Color(0xFFB02A2A),
    )
    Step.Done -> StepContent(
        "Hazır",
        "Hazır",
        "Kurulum tamam. Aşağıda hangi adımların yapıldığını görebilirsin.",
        Icons.Rounded.Check, Palette.PhoneBg, Palette.Green,
    )
}

@Composable
private fun StatusChip(ok: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (ok) Palette.PhoneBg else Color(0xFFFDE2DC),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (ok) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                tint = if (ok) Palette.Green else Palette.Red,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (ok) "Tamam, bu adım yapıldı" else "Henüz yapılmadı",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = if (ok) Palette.Green else Palette.Red,
            )
        }
    }
}

@Composable
private fun BigAction(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Green),
    ) { Text(label, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White) }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
        Text(label, fontSize = 17.sp, color = Palette.Ink)
    }
}

@Composable
private fun Tip(text: String) {
    Text(text, fontSize = 16.sp, color = Palette.InkSoft, lineHeight = 22.sp)
}

@Composable
private fun DeniedHelp(context: Context) {
    Tip("İzin penceresi çıkmadıysa ya da \"Reddet\" dendiyse: Uygulama bilgisi › İzinler bölümünden elle ver.")
    SmallAction("Uygulama bilgisi › İzinler") { open(context, appDetails(context)) }
}

@Composable
private fun SummaryRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Rounded.Check else Icons.Rounded.Close,
            contentDescription = null,
            tint = if (ok) Palette.Green else Palette.Red,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, modifier = Modifier.weight(1f))
        Text(if (ok) "Tamam" else "Eksik", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (ok) Palette.Green else Palette.Red)
    }
}

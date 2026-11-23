package com.annem.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.annem.launcher.Actions
import com.annem.launcher.LockResult
import com.annem.launcher.ScreenLock
import com.annem.launcher.SettingsStore
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TURKISH: Locale = Locale.forLanguageTag("tr-TR")

@Composable
fun HomeScreen(store: SettingsStore, onOpenContacts: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var lockProblem by remember { mutableStateOf<LockResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ClockHeader(onSecretHold = onOpenSettings)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Hava durumu kutusu diğer satırlardan %40 daha yüksek.
            WeatherTile(store = store, modifier = Modifier.fillMaxWidth().weight(1.4f))
            TileRow {
                Tile("Kişiler", Icons.Rounded.Contacts, Palette.ContactsBg, Palette.ContactsLine, Palette.ContactsIcon, it, onOpenContacts)
                Tile("Telefon", Icons.Rounded.Call, Palette.PhoneBg, Palette.PhoneLine, Palette.PhoneIcon, it) {
                    Actions.openDialer(context)
                }
            }
            TileRow {
                Tile("WhatsApp", Icons.Rounded.Forum, Palette.WaBg, Palette.WaLine, Palette.WaIcon, it) {
                    Actions.openWhatsApp(context)
                }
                Tile("Mesajlar", Icons.Rounded.Sms, Palette.SmsBg, Palette.SmsLine, Palette.SmsIcon, it) {
                    Actions.openMessages(context)
                }
            }
            TileRow {
                Tile("Kamera", Icons.Rounded.PhotoCamera, Palette.CamBg, Palette.CamLine, Palette.CamIcon, it) {
                    Actions.openCamera(context)
                }
                Tile("Fotoğraflar", Icons.Rounded.PhotoLibrary, Palette.PicBg, Palette.PicLine, Palette.PicIcon, it) {
                    Actions.openGallery(context)
                }
            }
        }

        LockButton(onClick = {
            val result = ScreenLock.lock(context)
            if (result != LockResult.LOCKED) lockProblem = result
        })
    }

    lockProblem?.let { problem ->
        AlertDialog(
            onDismissRequest = { lockProblem = null },
            containerColor = Color.White,
            title = { Text("Ekranı Kapat kurulmadı", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    if (problem == LockResult.SERVICE_NOT_CONNECTED) {
                        "Ayar açık görünüyor ama telefon servisi durdurmuş. Ayarlar'da \"Cihaz yöneticisi\" " +
                            "yöntemini açabilir ya da erişilebilirlik ayarını kapatıp yeniden açabilirsin."
                    } else {
                        "Bu düğmenin çalışması için bir kez kurulum gerekiyor. Ayarlar'da iki kolay yol var."
                    },
                    fontSize = 20.sp,
                    color = Palette.Ink,
                    lineHeight = 26.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        lockProblem = null
                        onOpenSettings()
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(56.dp),
                ) { Text("Kurulumu aç", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { lockProblem = null }) {
                    Text("Kapat", fontSize = 20.sp, color = Palette.Ink)
                }
            },
        )
    }
}

/**
 * Saat, tarih ve şarj. Saate 3 saniye basılı tutmak ayarları açar: annenin yanlışlıkla
 * girmeyeceği, yakınların bildiği gizli giriş.
 */
@Composable
private fun ClockHeader(onSecretHold: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    // Enerji: yalnızca ekran görünürken ve dakika başlarında güncellenir.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = LocalDateTime.now()
                val untilNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
                delay(untilNextMinute + 50L)
            }
        }
    }
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm", TURKISH))
    val date = now.format(DateTimeFormatter.ofPattern("d MMMM EEEE", TURKISH))
    val battery = rememberBatteryState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    // Parmak 3 sn içinde kalkarsa (ya da hareket iptal olursa) hiçbir şey olmaz.
                    val endedEarly = withTimeoutOrNull(3_000L) {
                        waitForUpOrCancellation()
                        true
                    }
                    if (endedEarly == null) onSecretHold()
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClockText(
                time = time,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Saat $time, $date" },
            )
            battery?.let { BatteryIndicator(it) }
        }
        Text(date, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft)
        if (battery?.low == true) {
            Text(
                "Şarj azaldı, telefonu şarja tak",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Red,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private const val CLOCK_SP = 70f

/**
 * Saat her zaman tek satırda kalır: 70 sp sığmıyorsa (dar ekran, geniş yazı tipi)
 * tam sığacak kadar küçülür; sığıyorsa 70 sp kalır.
 */
@Composable
private fun ClockText(time: String, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        val available = constraints.maxWidth
        val fontSize = remember(time, available) {
            val style = TextStyle(fontSize = CLOCK_SP.sp, fontWeight = FontWeight.ExtraBold)
            val needed = measurer.measure(time, style = style, softWrap = false, maxLines = 1).size.width
            if (needed <= available || needed == 0) CLOCK_SP else CLOCK_SP * available / needed * 0.97f
        }
        Text(
            time,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Palette.Ink,
            lineHeight = (fontSize + 4f).sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ColumnScope.TileRow(content: @Composable (Modifier) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().weight(1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content(Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun Tile(
    label: String,
    icon: ImageVector,
    background: Color,
    line: Color,
    iconTint: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = background,
        border = BorderStroke(2.dp, line),
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            // Uzun etiketler ("Fotoğraflar") dar telefonlarda tek satıra sığsın.
            val fitted = ((maxWidth.value - 16f) / (label.length * 0.62f)).coerceIn(20f, 28f)
            // Kısa ekranlarda simge küçülür, yazı küçülmez.
            val iconSize = if (maxHeight.value < 110f) 40.dp else 52.dp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(iconSize))
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    fontSize = fitted.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Palette.Ink,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun LockButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(76.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Ink, contentColor = Color.White),
    ) {
        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(14.dp))
        Text("Ekranı Kapat", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
    }
}

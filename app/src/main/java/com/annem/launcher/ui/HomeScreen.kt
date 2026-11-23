package com.annem.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Forum
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.annem.launcher.Actions
import com.annem.launcher.InstalledApps
import com.annem.launcher.Slots
import com.annem.launcher.LockResult
import com.annem.launcher.ScreenLock
import com.annem.launcher.SettingsStore
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TURKISH: Locale = Locale.forLanguageTag("tr-TR")

@Composable
fun HomeScreen(
    store: SettingsStore,
    onOpenContacts: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickApp: (slotId: String) -> Unit,
) {
    val context = LocalContext.current
    var lockProblem by remember { mutableStateOf<LockResult?>(null) }
    // Cihaz yöneticisi onay ekranı: sonuç gelince kurulduysa haber ver.
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        ScreenLock.reportActivation(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
    // Arka plandaki yumuşak yuvarlaklar (yalnızca süs).
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 90.dp, y = (-120).dp)
            .size(300.dp)
            .clip(CircleShape)
            .background(Palette.BlobPeach),
    )
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = (-130).dp, y = 130.dp)
            .size(220.dp)
            .clip(CircleShape)
            .background(Palette.BlobPink),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ClockHeader(greetingName = store.greetingName, onSecretHold = onOpenSettings)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Hava durumu kutusu diğer satırlardan %40 daha yüksek.
            WeatherTile(store = store, modifier = Modifier.fillMaxWidth().weight(1.4f))
            TileRow {
                Tile("Kişiler", Icons.Rounded.Contacts, Palette.ContactsBg, Palette.ContactsLine, Palette.ContactsIcon, it) {
                    onOpenContacts()
                }
                Tile("Telefon", Icons.Rounded.Call, Palette.PhoneBg, Palette.PhoneLine, Palette.PhoneIcon, it) {
                    Actions.openDialer(context)
                }
            }
            TileRow {
                Tile("WhatsApp", Icons.Rounded.Forum, Palette.WaBg, Palette.WaLine, Palette.WaIcon, it) {
                    Actions.openWhatsApp(context)
                }
                SlotTile(store, Slots.MESSAGES, Palette.SmsBg, Palette.SmsLine, Palette.SmsIcon, it, onPickApp)
            }
            TileRow {
                SlotTile(store, Slots.CAMERA, Palette.CamBg, Palette.CamLine, Palette.CamIcon, it, onPickApp)
                SlotTile(store, Slots.GALLERY, Palette.PicBg, Palette.PicLine, Palette.PicIcon, it, onPickApp)
            }
        }

        LockButton(onClick = {
            val result = ScreenLock.lock(context)
            if (result != LockResult.LOCKED) lockProblem = result
        })
    }
    }

    lockProblem?.let { problem ->
        AlertDialog(
            onDismissRequest = { lockProblem = null },
            containerColor = Color.White,
            title = { Text("Ekranı Kapat kurulmadı", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (problem == LockResult.SERVICE_NOT_CONNECTED) {
                            "Erişilebilirlik ayarı açık ama telefon servisi durdurmuş. " +
                                "\"Şimdi kur\" ile tek onaylık yolu açabilirsin."
                        } else {
                            "Bir kez kurulum gerekiyor. \"Şimdi kur\"a dokun, açılan ekranda \"Etkinleştir\"e bas."
                        },
                        fontSize = 20.sp,
                        color = Palette.Ink,
                        lineHeight = 26.sp,
                    )
                    TextButton(onClick = {
                        lockProblem = null
                        onOpenSettings()
                    }) { Text("Diğer yol (Ayarlar)", fontSize = 18.sp) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        lockProblem = null
                        ScreenLock.launchAdminActivation(context, adminLauncher)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(56.dp),
                ) { Text("Şimdi kur", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { lockProblem = null }) {
                    Text("Vazgeç", fontSize = 20.sp, color = Palette.Ink)
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
private fun ClockHeader(greetingName: String, onSecretHold: () -> Unit) {
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
    val greeting = when (now.hour) {
        in 5..10 -> "Günaydın"
        in 11..16 -> "İyi günler"
        in 17..21 -> "İyi akşamlar"
        else -> "İyi geceler"
    }
    val greetingText = if (greetingName.isBlank()) greeting else "$greeting, $greetingName"

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
        // Saate göre selam: "İyi akşamlar, Anneciğim"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = Palette.Heart,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(8.dp))
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val fitted = (maxWidth.value / (greetingText.length * 0.55f)).coerceIn(16f, 22f)
                Text(
                    greetingText,
                    fontSize = fitted.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Palette.Greeting,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClockText(
                time = time,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Saat $time, $date" },
            )
            battery?.let { BatteryIndicator(it) }
        }
        Text(date, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft, lineHeight = 28.sp)
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

private const val CLOCK_SP = 84f

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
            val style = TextStyle(fontSize = CLOCK_SP.sp, fontWeight = FontWeight.Black, fontFamily = Nunito)
            val needed = measurer.measure(time, style = style, softWrap = false, maxLines = 1).size.width
            if (needed <= available || needed == 0) CLOCK_SP else CLOCK_SP * available / needed * 0.97f
        }
        Text(
            time,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Black,
            color = Palette.Ink,
            lineHeight = (fontSize + 2f).sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ColumnScope.TileRow(content: @Composable (Modifier) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().weight(1f),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content(Modifier.weight(1f).fillMaxHeight())
    }
}

/**
 * Kullanıcının uygulama seçtiği kutu. Boşsa (ya da atanan uygulama silinmişse)
 * "Uygulama ekle" yazar ve dokununca uygulama listesi açılır; doluysa uygulamanın
 * kendi simgesi ve verilen adla o uygulamayı açar. Değiştirmek/boşaltmak: Ayarlar.
 */
@Composable
private fun SlotTile(
    store: SettingsStore,
    slotId: String,
    background: Color,
    line: Color,
    iconTint: Color,
    modifier: Modifier,
    onPickApp: (String) -> Unit,
) {
    val context = LocalContext.current
    val slot = store.slot(slotId)
    val installed = remember(slot?.packageName) {
        slot != null && context.packageManager.getLaunchIntentForPackage(slot.packageName) != null
    }
    val appIcon by produceState<ImageBitmap?>(initialValue = null, slot?.packageName) {
        value = slot?.packageName?.let { InstalledApps.loadIcon(context, it) }
    }

    if (slot != null && installed) {
        Tile(
            label = slot.label,
            icon = Icons.Rounded.Apps,
            background = background,
            line = line,
            iconTint = iconTint,
            modifier = modifier,
            appIcon = appIcon,
        ) {
            if (!Actions.openApp(context, slot.packageName)) onPickApp(slotId)
        }
    } else {
        Tile("Uygulama ekle", Icons.Rounded.Add, Palette.EmptyBg, Palette.EmptyLine, Palette.EmptyIcon, modifier, empty = true) {
            onPickApp(slotId)
        }
    }
}

/**
 * Beyaz, yuvarlak köşeli kutu; altında renkli bir "gölge" şeridi (basılabilir görünür),
 * ortada renkli balon içinde simge ve büyük yazı. Boş kutu: kesikli çerçeve.
 */
@Composable
private fun Tile(
    label: String,
    icon: ImageVector,
    background: Color,
    line: Color,
    iconTint: Color,
    modifier: Modifier,
    appIcon: ImageBitmap? = null,
    empty: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(modifier = modifier) {
        if (!empty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 6.dp)
                    .clip(shape)
                    .background(line),
            )
        }
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 6.dp)
                .then(if (empty) Modifier.dashedBorder(line, 28.dp) else Modifier),
            shape = shape,
            color = if (empty) Color.Transparent else Color.White,
        ) {
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                // Uzun etiketler dar telefonlarda tek satıra sığsın.
                val fitted = ((maxWidth.value - 16f) / (label.length * 0.58f)).coerceIn(19f, 26f)
                // Kısa ekranlarda balon küçülür, yazı küçülmez.
                val bubble = if (maxHeight.value < 112f) 50.dp else 62.dp
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (appIcon != null) {
                        Image(bitmap = appIcon, contentDescription = null, modifier = Modifier.size(bubble))
                    } else {
                        Box(
                            modifier = Modifier.size(bubble).clip(CircleShape).background(background),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(bubble * 0.56f))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        label,
                        fontSize = fitted.sp,
                        fontWeight = FontWeight.Black,
                        color = if (empty) Palette.EmptyIcon else Palette.Ink,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

/** Kesikli yuvarlak çerçeve ("Uygulama ekle" kutusu için). */
private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    val stroke = 3.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))),
    )
}

@Composable
private fun LockButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(74.dp),
        shape = RoundedCornerShape(37.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Ink, contentColor = Color.White),
    ) {
        Icon(Icons.Rounded.NightsStay, contentDescription = null, tint = Palette.Moon, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Text("Ekranı Kapat", fontSize = 27.sp, fontWeight = FontWeight.Black)
    }
}

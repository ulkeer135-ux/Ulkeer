package com.annem.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.ui.draw.blur
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import com.annem.launcher.Badges
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
    onOpenDreams: () -> Unit = {},
) {
    val context = LocalContext.current
    var lockProblem by remember { mutableStateOf<LockResult?>(null) }
    var moreOpen by remember { mutableStateOf(false) }
    // Cihaz yöneticisi onay ekranı: sonuç gelince kurulduysa haber ver.
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        ScreenLock.reportActivation(context)
    }

    // Çekmece açıkken arkadaki ana ekran yumuşakça bulanıklaşır (buzlu cam hissi, Android 12+).
    val behindBlur by androidx.compose.animation.core.animateDpAsState(if (moreOpen) 16.dp else 0.dp, label = "blur")
    Box(modifier = Modifier.fillMaxSize().then(if (behindBlur > 0.dp) Modifier.blur(behindBlur) else Modifier)) {
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
        ClockHeader(onSecretHold = onOpenSettings)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val nine = store.tileCount == 9
            val cols = if (nine) 3 else 2
            // 6 kutuda hava durumu kartı diğer satırlardan %40 yüksek; 9 kutuda bir satır boyunda.
            WeatherTile(store = store, modifier = Modifier.fillMaxWidth().weight(if (nine) 1.05f else 1.4f))
            val showBadges = store.badgesEnabled

            // Kutular soldan sağa, yukarıdan aşağıya: 3 sabit kutu + kullanıcının seçtiği kutular.
            val tiles = mutableListOf<@Composable (Modifier) -> Unit>()
            tiles += { m ->
                Tile("Kişiler", Icons.Rounded.Contacts, Palette.ContactsBg, Palette.ContactsLine, Palette.ContactsIcon, m, compact = nine) {
                    onOpenContacts()
                }
            }
            tiles += { m ->
                Tile(
                    "Telefon", Icons.Rounded.Call, Palette.PhoneBg, Palette.PhoneLine, Palette.PhoneIcon, m,
                    badge = if (showBadges) Badges.missedCalls else 0,
                    badgeWord = "cevapsız arama",
                    compact = nine,
                ) {
                    Actions.openDialer(context)
                }
            }
            tiles += { m ->
                Tile(
                    "WhatsApp", Icons.Rounded.Forum, Palette.WaBg, Palette.WaLine, Palette.WaIcon, m,
                    badge = if (showBadges) Badges.whatsapp else 0,
                    badgeWord = "okunmamış mesaj",
                    compact = nine,
                ) {
                    Actions.openWhatsApp(context)
                }
            }
            val slotColors = listOf(
                Triple(Palette.SmsBg, Palette.SmsLine, Palette.SmsIcon),
                Triple(Palette.CamBg, Palette.CamLine, Palette.CamIcon),
                Triple(Palette.PicBg, Palette.PicLine, Palette.PicIcon),
                Triple(Palette.Slot4Bg, Palette.Slot4Line, Palette.Slot4Icon),
                Triple(Palette.Slot5Bg, Palette.Slot5Line, Palette.Slot5Icon),
                Triple(Palette.Slot6Bg, Palette.Slot6Line, Palette.Slot6Icon),
            )
            Slots.visible(nine).forEachIndexed { index, id ->
                val (bg, line, icon) = slotColors[index]
                tiles += { m -> SlotTile(store, id, bg, line, icon, m, onPickApp, compact = nine, onOpenDreams = onOpenDreams) }
            }
            tiles.chunked(cols).forEach { row ->
                TileRow { m -> row.forEach { tile -> tile(m) } }
            }
        }

        // Ekranı Kapat tam genişlikte; "Daha fazla" sekmesi düğmenin üst kenarına oturur
        // (yarısı kutularla düğme arasındaki boşlukta), dikeyde hiç yer kaplamaz.
        // Sekme kutulara değmesin: düğmenin üstünde küçük ek boşluk.
        Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            LockButton(onClick = {
                val result = ScreenLock.lock(context)
                if (result != LockResult.LOCKED) lockProblem = result
            })
            MoreTab(
                onOpen = { moreOpen = true },
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-13).dp),
            )
        }
    }
    }

    if (moreOpen) {
        MoreSheet(
            store = store,
            onDismiss = { moreOpen = false },
            onPickApp = { id -> moreOpen = false; onPickApp(id) },
            onOpenDreams = { moreOpen = false; onOpenDreams() },
        )
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
    compact: Boolean = false,
    onOpenDreams: () -> Unit = {},
    glass: Boolean = false,
) {
    val context = LocalContext.current
    val slot = store.slot(slotId)
    if (slot != null && com.annem.launcher.InternalApps.isInternal(slot.packageName)) {
        Tile(
            label = slot.label,
            icon = Icons.Rounded.Bedtime,
            background = background,
            line = line,
            iconTint = iconTint,
            modifier = modifier,
            compact = compact,
            glass = glass,
        ) { onOpenDreams() }
        return
    }
    val installed = remember(slot?.packageName) {
        slot != null && context.packageManager.getLaunchIntentForPackage(slot.packageName) != null
    }
    val appIcon by produceState(initialValue = slot?.packageName?.let { InstalledApps.cachedIcon(it) }, slot?.packageName) {
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
            compact = compact,
            glass = glass,
        ) {
            if (!Actions.openApp(context, slot.packageName)) onPickApp(slotId)
        }
    } else {
        Tile(
            if (compact) "Ekle" else "Uygulama ekle",
            Icons.Rounded.Add, Palette.EmptyBg, Palette.EmptyLine, Palette.EmptyIcon, modifier,
            empty = true, compact = compact, glass = glass,
        ) {
            onPickApp(slotId)
        }
    }
}

/**
 * İki tonlu kutu: üst kısım pastel renkli (simge), alt kısım beyaz (yazı).
 * Boş kutu: açık bej üst, kesikli çerçeve, "Uygulama ekle".
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
    badge: Int = 0,
    badgeWord: String = "yeni",
    compact: Boolean = false,
    /** Kendi çizdiğimiz sevimli simge (varsa düz simgenin yerine). */
    cuteIcon: (@Composable (Modifier) -> Unit)? = null,
    /** Buzlu cam: "Daha fazla" çekmecesinde yarı saydam. */
    glass: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(modifier = modifier) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxSize().then(if (empty) Modifier.dashedBorder(line, 28.dp) else Modifier),
        shape = shape,
        color = if (glass) Color.White.copy(alpha = 0.55f) else Color.White,
        border = if (empty) null else BorderStroke(if (glass) 1.5.dp else 2.dp, if (glass) Color.White.copy(alpha = 0.9f) else line),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Üst: renkli alan + simge
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (glass) background.copy(alpha = 0.6f) else background),
                contentAlignment = Alignment.Center,
            ) {
                val iconSize = (maxHeight * 0.62f).coerceIn(28.dp, 56.dp)
                if (appIcon != null) {
                    Image(bitmap = appIcon, contentDescription = null, modifier = Modifier.size(iconSize))
                } else if (cuteIcon != null) {
                    cuteIcon(Modifier.size(iconSize * 1.15f))
                } else {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(iconSize))
                }
            }
            // Alt: beyaz alan + yazı (her zaman tam sığar)
            FitText(
                text = label,
                maxSp = if (compact) 19f else 25f,
                minSp = 14f,
                fontWeight = FontWeight.Black,
                color = if (empty) Palette.EmptyIcon else Palette.Ink,
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
    // Kırmızı sayı (okunmamış mesaj / cevapsız arama): kutunun sağ üst köşesinde.
    if (badge > 0) {
        CountBadge(
            count = badge,
            description = "$badge $badgeWord",
            size = if (compact) 34.dp else 44.dp,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp),
        )
    }
    }
}

@Composable
private fun CountBadge(count: Int, description: String, size: Dp, modifier: Modifier) {
    Box(
        modifier = modifier
            .semantics { contentDescription = description }
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .padding(3.dp)
            .clip(CircleShape)
            .background(Palette.Red),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 9) "9+" else count.toString(),
            fontSize = (if (count > 9) 0.39f else 0.5f).let { (size.value * it).sp },
            fontWeight = FontWeight.Black,
            color = Color.White,
            lineHeight = 24.sp,
        )
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

/** Ekranı Kapat'ın üst kenarındaki küçük sekme: dokun ya da yukarı kaydır → "Daha fazla" çekmecesi. */
@Composable
private fun MoreTab(onOpen: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onOpen,
        // Buzlu cam: yarı saydam beyaz, ince beyaz kenar; altı Ekranı Kapat'ın üstüne biner.
        shape = RoundedCornerShape(50),
        // Beyaz yazı krem zeminde de okunsun diye cam hafif koyu tonlu.
        color = Palette.Ink.copy(alpha = 0.32f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
        shadowElevation = 0.dp,
        modifier = modifier
            .height(26.dp)
            .semantics { contentDescription = "Daha fazla uygulama" }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { _ -> total = 0f },
                    onVerticalDrag = { _, dy: Float -> total += dy; if (total < -30f) { total = 0f; onOpen() } },
                )
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(3.dp))
            Text("Daha fazla", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }
    }
}

/** Alttan açılan çekmece: 3 uygulama kutusu. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(
    store: SettingsStore,
    onDismiss: () -> Unit,
    onPickApp: (String) -> Unit,
    onOpenDreams: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Palette.Cream.copy(alpha = 0.72f),
        scrimColor = Color.Black.copy(alpha = 0.12f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Daha fazla", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Palette.Ink)
            val colors = listOf(
                Triple(Palette.SmsBg, Palette.SmsLine, Palette.SmsIcon),
                Triple(Palette.CamBg, Palette.CamLine, Palette.CamIcon),
                Triple(Palette.PicBg, Palette.PicLine, Palette.PicIcon),
                Triple(Palette.Slot4Bg, Palette.Slot4Line, Palette.Slot4Icon),
                Triple(Palette.Slot5Bg, Palette.Slot5Line, Palette.Slot5Icon),
                Triple(Palette.Slot6Bg, Palette.Slot6Line, Palette.Slot6Icon),
                Triple(Palette.ContactsBg, Palette.ContactsLine, Palette.ContactsIcon),
                Triple(Palette.PhoneBg, Palette.PhoneLine, Palette.PhoneIcon),
                Triple(Palette.WaBg, Palette.WaLine, Palette.WaIcon),
            )
            // 9 kutu: 3'erli üç sıra
            Slots.MORE.chunked(3).forEachIndexed { r, row ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(136.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEachIndexed { c, id ->
                        val (bg, line, icon) = colors[r * 3 + c]
                        SlotTile(
                            store, id, bg, line, icon, Modifier.weight(1f).fillMaxHeight(),
                            onPickApp = onPickApp, compact = true, onOpenDreams = onOpenDreams, glass = true,
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(18.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.5f)),
            ) { Text("Kapat", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink) }
        }
    }
}

package com.annem.launcher.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.annem.launcher.Actions
import com.annem.launcher.ContactPhotos
import com.annem.launcher.PhoneContact
import com.annem.launcher.PhoneContacts
import com.annem.launcher.SettingsStore
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

private val TR: Locale = Locale.forLanguageTag("tr-TR")
private val StarGold = Color(0xFFD99A00)

private val avatarColors = listOf(
    Palette.ContactsBg to Palette.ContactsIcon,
    Palette.PicBg to Palette.PicIcon,
    Palette.CamBg to Palette.CamIcon,
    Palette.SmsBg to Palette.SmsIcon,
    Palette.PhoneBg to Palette.PhoneIcon,
)

/**
 * "Kimi arayalım?": önce favoriler (büyük fotoğraflı kartlar, ör. "Oğlum", "Gelinim"),
 * sonra diğer kişiler. Başlığa 3 saniye basılı tutunca düzenleme modu açılır: her kişinin
 * yanında yıldız (favoriye ekle/çıkar) çıkar, kişiye dokununca görünen ad ve fotoğraf
 * değiştirilir. Annen için yıldızlar normalde görünmez. Telefonun rehberi değişmez.
 */
@Composable
fun ContactsScreen(store: SettingsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val granted = remember(lifecycleState) { PhoneContacts.hasPermission(context) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        if (!granted) {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
        }
    }

    var contacts by remember { mutableStateOf<List<PhoneContact>?>(null) }
    LaunchedEffect(granted) {
        if (granted) contacts = PhoneContacts.load(context)
    }
    var editMode by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PhoneContact?>(null) }

    // Fotoğraf seçici: seçilen fotoğraf düzenlenen kişiye kaydedilir.
    var photoTarget by remember { mutableStateOf<Long?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = photoTarget
        photoTarget = null
        if (uri != null && id != null) {
            scope.launch {
                if (ContactPhotos.save(context, id, uri)) {
                    store.setPhotoVersion(id, System.currentTimeMillis())
                } else {
                    Toast.makeText(context, "Fotoğraf okunamadı, başka bir fotoğraf dene", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val labels = store.contactLabels
    fun displayName(c: PhoneContact) = labels[c.id] ?: c.name
    fun isFavorite(c: PhoneContact) = store.isFavorite(c.id, c.starred)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BigBackButton(label = "Ana Ekran", onClick = onBack)
        Text(
            "Kimi arayalım?",
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            color = Palette.Ink,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .holdFor(3_000L, "title") { editMode = true },
        )

        if (editMode) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Palette.SmsBg,
                border = BorderStroke(2.dp, Palette.SmsLine),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Düzenleme: yıldız = favori, kişiye dokun = ad ve fotoğraf",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.Ink,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { editMode = false },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Ink, contentColor = Color.White),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tamam", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val list = contacts
        when {
            !granted -> {
                Message("Kişileri göstermek için rehber izni gerekiyor.")
                Button(
                    onClick = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("İzin ver", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = { Actions.openContacts(context) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Telefonun rehberini aç", fontSize = 20.sp, color = Palette.Ink) }
            }
            list == null -> Message("Yükleniyor…")
            list.isEmpty() -> Message("Rehberde telefon numarası olan kişi yok.")
            else -> {
                val collator = remember { Collator.getInstance(TR) }
                val favorites = list
                    .filter { isFavorite(it) }
                    .sortedWith { a, b -> collator.compare(displayName(a), displayName(b)) }
                val others = list.filterNot { isFavorite(it) }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    if (favorites.isEmpty() && !editMode) {
                        item(key = "hint") {
                            Text(
                                "Favori eklemek için başlığa 3 saniye basılı tut.",
                                fontSize = 16.sp,
                                color = Palette.InkSoft,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                    if (favorites.isNotEmpty()) {
                        item(key = "h-fav") { SectionHeader("Favoriler") }
                        if (favorites.size == 1) {
                            // Tek favori: tam genişlikte, yatay kart.
                            val c = favorites.first()
                            item(key = "f${c.id}") {
                                WideFavoriteCard(
                                    store = store,
                                    contact = c,
                                    title = displayName(c),
                                    realName = if (labels.containsKey(c.id)) c.name else null,
                                    editMode = editMode,
                                    onEdit = { editing = c },
                                )
                            }
                        } else {
                            items(favorites.chunked(2), key = { row -> "f" + row.joinToString("-") { it.id.toString() } }) { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { c ->
                                        FavoriteCard(
                                            store = store,
                                            contact = c,
                                            title = displayName(c),
                                            realName = if (labels.containsKey(c.id)) c.name else null,
                                            editMode = editMode,
                                            onEdit = { editing = c },
                                            // Yan yana iki kart aynı boyda kalsın.
                                            reserveVideoSpace = row.any { it.whatsappVideo != null },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    if (others.isNotEmpty()) {
                        item(key = "h-all") { SectionHeader(if (favorites.isEmpty()) "Kişiler" else "Diğer kişiler") }
                        items(others, key = { "o${it.id}" }) { c ->
                            ContactRow(
                                store = store,
                                contact = c,
                                title = displayName(c),
                                realName = if (labels.containsKey(c.id)) c.name else null,
                                editMode = editMode,
                                onEdit = { editing = c },
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { c ->
        EditContactDialog(
            contact = c,
            currentLabel = labels[c.id].orEmpty(),
            hasOwnPhoto = store.contactPhotos.containsKey(c.id),
            onSave = { label ->
                store.updateLabel(c.id, label)
                editing = null
            },
            onPickPhoto = {
                photoTarget = c.id
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemovePhoto = {
                ContactPhotos.delete(context, c.id)
                store.setPhotoVersion(c.id, null)
            },
            onDismiss = { editing = null },
        )
    }
}

/** Belirli süre basılı tutunca [onHold]; kısa dokunuş ve kaydırma etkilenmez. */
private fun Modifier.holdFor(millis: Long, key: Any, onHold: () -> Unit): Modifier = pointerInput(key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val endedEarly = withTimeoutOrNull(millis) {
            waitForUpOrCancellation()
            true
        }
        if (endedEarly == null) onHold()
    }
}

@Composable
private fun StarToggle(store: SettingsStore, contact: PhoneContact, modifier: Modifier = Modifier) {
    val favorite = store.isFavorite(contact.id, contact.starred)
    IconButton(
        onClick = { store.toggleFavorite(contact.id, contact.starred) },
        modifier = modifier
            .size(52.dp)
            .semantics { contentDescription = if (favorite) "Favorilerden çıkar" else "Favorilere ekle" },
    ) {
        Icon(
            if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = null,
            tint = if (favorite) StarGold else Palette.InkSoft,
            modifier = Modifier.size(38.dp),
        )
    }
}

@Composable
private fun CallButtonWide(contact: PhoneContact, modifier: Modifier) {
    val context = LocalContext.current
    Button(
        onClick = { Actions.call(context, contact.number) },
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Green, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text("Ara", fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
}

/** WhatsApp görüntülü arama rengi (WhatsApp'ın koyu yeşili; "Ara"dan ayırt edilsin). */
private val VideoTeal = Color(0xFF0B7A6E)
private val VIDEO_BUTTON_HEIGHT = 44.dp

@Composable
private fun VideoButtonWide(contact: PhoneContact, modifier: Modifier, label: String = "Görüntülü") {
    val video = contact.whatsappVideo ?: return
    val context = LocalContext.current
    OutlinedButton(
        onClick = { Actions.whatsappVideoCall(context, video) },
        modifier = modifier.height(VIDEO_BUTTON_HEIGHT),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, VideoTeal),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = VideoTeal),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        FitText(
            text = label,
            maxSp = 17f,
            minSp = 12f,
            fontWeight = FontWeight.Bold,
            color = VideoTeal,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun FavoriteCard(
    store: SettingsStore,
    contact: PhoneContact,
    title: String,
    realName: String?,
    editMode: Boolean,
    onEdit: () -> Unit,
    reserveVideoSpace: Boolean,
    modifier: Modifier,
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.White,
        border = BorderStroke(2.dp, Palette.ContactsLine),
        modifier = modifier,
    ) {
        Box {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (editMode) Modifier.clickable(onClick = onEdit) else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ContactAvatar(store, contact, size = 96.dp, preferFull = true)
                    Spacer(Modifier.height(8.dp))
                    FitText(
                        text = title,
                        maxSp = 26f,
                        minSp = 16f,
                        fontWeight = FontWeight.Black,
                        color = Palette.Ink,
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (realName != null) {
                        Text(realName, fontSize = 15.sp, color = Palette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                CallButtonWide(contact, Modifier.fillMaxWidth())
                if (contact.whatsappVideo != null) {
                    VideoButtonWide(contact, Modifier.fillMaxWidth())
                } else if (reserveVideoSpace) {
                    Spacer(Modifier.height(VIDEO_BUTTON_HEIGHT))
                }
            }
            if (editMode) StarToggle(store, contact, Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun WideFavoriteCard(
    store: SettingsStore,
    contact: PhoneContact,
    title: String,
    realName: String?,
    editMode: Boolean,
    onEdit: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.White,
        border = BorderStroke(2.dp, Palette.ContactsLine),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (editMode) Modifier.clickable(onClick = onEdit) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContactAvatar(store, contact, size = 104.dp, preferFull = true)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        FitText(
                            text = title,
                            maxSp = 30f,
                            minSp = 18f,
                            fontWeight = FontWeight.Black,
                            color = Palette.Ink,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (realName != null) {
                            Text(realName, fontSize = 16.sp, color = Palette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (editMode) StarToggle(store, contact)
            }
            CallButtonWide(contact, Modifier.fillMaxWidth())
            VideoButtonWide(contact, Modifier.fillMaxWidth(0.6f).align(Alignment.CenterHorizontally), label = "Görüntülü ara")
        }
    }
}

@Composable
private fun ContactRow(
    store: SettingsStore,
    contact: PhoneContact,
    title: String,
    realName: String?,
    editMode: Boolean,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Palette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(if (editMode) Modifier.clickable(onClick = onEdit) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContactAvatar(store, contact, size = 56.dp, preferFull = false)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Palette.Ink,
                        lineHeight = 26.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (realName != null) {
                        Text(realName, fontSize = 14.sp, color = Palette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (editMode) {
                StarToggle(store, contact)
            } else {
                val video = contact.whatsappVideo
                if (video != null) {
                    // Küçük ve sade (beyaz zemin, ince çerçeve): asıl düğme "Ara"; yanlışlıkla
                    // dokunulmasın diye araya da boşluk var.
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = { Actions.whatsappVideoCall(context, video) },
                        modifier = Modifier.size(46.dp).semantics { contentDescription = "$title görüntülü ara" },
                        shape = CircleShape,
                        border = BorderStroke(2.dp, VideoTeal),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VideoTeal),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Button(
                    onClick = { Actions.call(context, contact.number) },
                    modifier = Modifier.size(64.dp).semantics { contentDescription = "$title ara" },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Green, contentColor = Color.White),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun EditContactDialog(
    contact: PhoneContact,
    currentLabel: String,
    hasOwnPhoto: Boolean,
    onSave: (label: String) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember(contact.id) { mutableStateOf(currentLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(contact.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Görünen ad (ör. Oğlum, Gelinim)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = onPickPhoto, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (hasOwnPhoto) "Fotoğrafı değiştir" else "Galeriden fotoğraf seç", fontSize = 17.sp)
                }
                if (hasOwnPhoto) {
                    TextButton(onClick = onRemovePhoto) { Text("Seçilen fotoğrafı kaldır", fontSize = 16.sp) }
                }
                Text(
                    "WhatsApp fotoğrafı için: WhatsApp'ta kişinin profil fotoğrafını açıp telefona kaydet, sonra buradan seç. " +
                        "Telefonun rehberi değişmez.",
                    fontSize = 14.sp,
                    color = Palette.InkSoft,
                    lineHeight = 19.sp,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(label) }) { Text("Kaydet", fontSize = 18.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç", fontSize = 18.sp) }
        },
    )
}

@Composable
private fun Message(text: String) {
    Text(text, fontSize = 24.sp, color = Palette.InkSoft, lineHeight = 32.sp, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Black,
        color = Palette.InkSoft,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/** Fotoğraf önceliği: Annem'de seçilen fotoğraf → rehberdeki fotoğraf → renkli baş harf. */
@Composable
private fun ContactAvatar(store: SettingsStore, contact: PhoneContact, size: Dp, preferFull: Boolean) {
    val context = LocalContext.current
    val ownVersion = store.contactPhotos[contact.id]
    val uri = if (preferFull) contact.fullPhotoUri ?: contact.photoUri else contact.photoUri
    val photo by produceState<ImageBitmap?>(initialValue = null, ownVersion, uri) {
        value = (if (ownVersion != null) ContactPhotos.load(context, contact.id) else null)
            ?: uri?.let { PhoneContacts.loadPhoto(context, it) }
    }
    val current = photo
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        val (bg, fg) = avatarColors[Math.floorMod(contact.id, avatarColors.size.toLong()).toInt()]
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                contact.name.trim().take(1).uppercase(TR),
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Black,
                color = fg,
            )
        }
    }
}

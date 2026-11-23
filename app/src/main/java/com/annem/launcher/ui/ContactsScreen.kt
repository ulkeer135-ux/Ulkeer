package com.annem.launcher.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import com.annem.launcher.PhoneContact
import com.annem.launcher.PhoneContacts
import com.annem.launcher.SettingsStore
import java.text.Collator
import java.util.Locale

private val TR: Locale = Locale.forLanguageTag("tr-TR")

private val avatarColors = listOf(
    Palette.ContactsBg to Palette.ContactsIcon,
    Palette.PicBg to Palette.PicIcon,
    Palette.CamBg to Palette.CamIcon,
    Palette.SmsBg to Palette.SmsIcon,
    Palette.PhoneBg to Palette.PhoneIcon,
)

/**
 * "Kimi arayalım?": önce favoriler (büyük fotoğraflı kartlar, ör. "Oğlum", "Gelinim"),
 * sonra rehberdeki diğer kişiler. Bir kişiye 2 saniye basılı tutmak görünen adını ve
 * favori durumunu değiştirir (telefonun rehberi değişmez).
 */
@Composable
fun ContactsScreen(store: SettingsStore, onBack: () -> Unit) {
    val context = LocalContext.current
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
    var editing by remember { mutableStateOf<PhoneContact?>(null) }

    val labels = store.contactLabels
    val annemFavorites = store.contactFavorites
    fun displayName(c: PhoneContact) = labels[c.id] ?: c.name

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
            modifier = Modifier.padding(horizontal = 4.dp),
        )

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
                    .filter { it.starred || it.id in annemFavorites }
                    .sortedWith { a, b -> collator.compare(displayName(a), displayName(b)) }
                val others = list.filterNot { it.starred || it.id in annemFavorites }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    if (favorites.isEmpty()) {
                        item(key = "hint") {
                            Text(
                                "Bir kişiyi öne almak için adına 2 saniye basılı tut.",
                                fontSize = 16.sp,
                                color = Palette.InkSoft,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    } else {
                        item(key = "h-fav") { SectionHeader("Favoriler") }
                        // Favoriler ikişerli büyük kartlar.
                        items(favorites.chunked(2), key = { row -> "f" + row.joinToString("-") { it.id.toString() } }) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { c ->
                                    FavoriteCard(
                                        contact = c,
                                        title = displayName(c),
                                        realName = if (labels.containsKey(c.id)) c.name else null,
                                        onEdit = { editing = c },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    if (others.isNotEmpty()) {
                        item(key = "h-all") { SectionHeader(if (favorites.isEmpty()) "Kişiler" else "Diğer kişiler") }
                        items(others, key = { "o${it.id}" }) { c ->
                            ContactRow(
                                contact = c,
                                title = displayName(c),
                                realName = if (labels.containsKey(c.id)) c.name else null,
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
            isAnnemFavorite = c.id in annemFavorites,
            onSave = { label, favorite ->
                store.updateContact(c.id, label, favorite)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** 2 saniye basılı tutunca [onHold]; kısa dokunuş ve kaydırma etkilenmez. */
private fun Modifier.holdToEdit(key: Any, onHold: () -> Unit): Modifier = pointerInput(key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val endedEarly = withTimeoutOrNull(2_000L) {
            waitForUpOrCancellation()
            true
        }
        if (endedEarly == null) onHold()
    }
}

@Composable
private fun FavoriteCard(
    contact: PhoneContact,
    title: String,
    realName: String?,
    onEdit: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.White,
        border = BorderStroke(2.dp, Palette.ContactsLine),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().holdToEdit(contact.id, onEdit),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ContactAvatar(contact, size = 96.dp, preferFull = true)
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
                    Text(
                        realName,
                        fontSize = 15.sp,
                        color = Palette.InkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Button(
                onClick = { Actions.call(context, contact.number) },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Green, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ara", fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ContactRow(contact: PhoneContact, title: String, realName: String?, onEdit: () -> Unit) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Palette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).holdToEdit(contact.id, onEdit),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContactAvatar(contact, size = 56.dp, preferFull = false)
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
            Spacer(Modifier.width(8.dp))
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

@Composable
private fun EditContactDialog(
    contact: PhoneContact,
    currentLabel: String,
    isAnnemFavorite: Boolean,
    onSave: (label: String, favorite: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember(contact.id) { mutableStateOf(currentLabel) }
    var favorite by remember(contact.id) { mutableStateOf(isAnnemFavorite || contact.starred) }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = favorite,
                        onCheckedChange = { favorite = it },
                        enabled = !contact.starred,
                    )
                    Text("Favorilerde göster", fontSize = 18.sp)
                }
                if (contact.starred) {
                    Text(
                        "Telefonun rehberinde yıldızlı olduğu için favorilerde. Çıkarmak için rehberden yıldızı kaldır.",
                        fontSize = 14.sp,
                        color = Palette.InkSoft,
                    )
                }
                Text("Telefonun rehberindeki ad değişmez.", fontSize = 14.sp, color = Palette.InkSoft)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(label, favorite && !contact.starred) }) { Text("Kaydet", fontSize = 18.sp) }
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

@Composable
private fun ContactAvatar(contact: PhoneContact, size: Dp, preferFull: Boolean) {
    val context = LocalContext.current
    val uri = if (preferFull) contact.fullPhotoUri ?: contact.photoUri else contact.photoUri
    val photo by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = uri?.let { PhoneContacts.loadPhoto(context, it) }
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

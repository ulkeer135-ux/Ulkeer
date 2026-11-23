package com.annem.launcher.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.annem.launcher.Actions
import com.annem.launcher.PhoneContact
import com.annem.launcher.PhoneContacts
import java.util.Locale

private val avatarColors = listOf(
    Palette.ContactsBg to Palette.ContactsIcon,
    Palette.PicBg to Palette.PicIcon,
    Palette.CamBg to Palette.CamIcon,
    Palette.SmsBg to Palette.SmsIcon,
    Palette.PhoneBg to Palette.PhoneIcon,
)

/** "Kimi arayalım?": telefon rehberindeki kişiler, her birinde büyük bir Ara düğmesi. */
@Composable
fun ContactsScreen(onBack: () -> Unit) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BigBackButton(label = "Ana Ekran", onClick = onBack)
        Text(
            "Kimi arayalım?",
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
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
                val favorites = list.filter { it.starred }
                val others = list.filterNot { it.starred }
                val showHeaders = favorites.isNotEmpty() && others.isNotEmpty()
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    if (showHeaders) item(key = "h-fav") { SectionHeader("Favoriler") }
                    items(favorites, key = { "f${it.id}" }) { ContactCard(it) }
                    if (showHeaders) item(key = "h-all") { SectionHeader("Tüm kişiler") }
                    items(others, key = { "o${it.id}" }) { ContactCard(it) }
                }
            }
        }
    }
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
        fontWeight = FontWeight.ExtraBold,
        color = Palette.InkSoft,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

@Composable
private fun ContactCard(contact: PhoneContact) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(2.dp, Palette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ContactAvatar(contact)
                Spacer(Modifier.width(16.dp))
                Text(
                    contact.name,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Palette.Ink,
                    lineHeight = 34.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = { Actions.call(context, contact.number) },
                modifier = Modifier.fillMaxWidth().height(68.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Green, contentColor = Color.White),
            ) {
                Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text("Ara", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun ContactAvatar(contact: PhoneContact) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(initialValue = null, contact.photoUri) {
        value = contact.photoUri?.let { PhoneContacts.loadPhoto(context, it) }
    }
    val size = 72.dp
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
                contact.name.trim().take(1).uppercase(Locale.forLanguageTag("tr-TR")),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = fg,
            )
        }
    }
}

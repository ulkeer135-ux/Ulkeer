package com.annem.launcher.ui

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annem.launcher.Actions
import com.annem.launcher.ContactStore
import com.annem.launcher.FamilyContact

private val avatarColors = listOf(
    Palette.FamilyBg to Palette.FamilyIcon,
    Palette.PicBg to Palette.PicIcon,
    Palette.CamBg to Palette.CamIcon,
    Palette.SmsBg to Palette.SmsIcon,
    Palette.PhoneBg to Palette.PhoneIcon,
)

@Composable
fun FamilyScreen(store: ContactStore, onBack: () -> Unit) {
    val context = LocalContext.current

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

        if (store.contacts.isEmpty()) {
            Text(
                "Henüz kimse eklenmemiş.\nBir yakının ayarlardan ekleyebilir.",
                fontSize = 24.sp,
                color = Palette.InkSoft,
                lineHeight = 32.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(store.contacts, key = { it.id }) { contact ->
                ContactCard(
                    store = store,
                    contact = contact,
                    colorIndex = store.contacts.indexOf(contact),
                    onCall = { Actions.call(context, contact.phone) },
                    onVideo = { Actions.videoCall(context, contact.phone) },
                )
            }
        }
    }
}

@Composable
private fun ContactCard(
    store: ContactStore,
    contact: FamilyContact,
    colorIndex: Int,
    onCall: () -> Unit,
    onVideo: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(2.dp, Palette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(store, contact, colorIndex, size = 80.dp)
                Spacer(Modifier.width(16.dp))
                Text(contact.name, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink, lineHeight = 34.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigActionButton("Ara", Icons.Rounded.Call, Palette.Green, Modifier.weight(1f), onCall)
                BigActionButton("Görüntülü", Icons.Rounded.Videocam, Palette.Blue, Modifier.weight(1f), onVideo)
            }
        }
    }
}

@Composable
private fun BigActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(68.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
    }
}

@Composable
fun Avatar(store: ContactStore, contact: FamilyContact, colorIndex: Int, size: Dp) {
    val photo = remember(contact.id, contact.photoVersion) {
        if (contact.photoVersion == 0L) null else store.loadPhoto(contact.id)
    }
    if (photo != null) {
        Image(
            bitmap = photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        val (bg, fg) = avatarColors[Math.floorMod(colorIndex, avatarColors.size)]
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                contact.name.trim().take(1).uppercase(java.util.Locale.forLanguageTag("tr-TR")),
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.ExtraBold,
                color = fg,
            )
        }
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

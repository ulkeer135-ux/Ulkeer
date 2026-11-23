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
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annem.launcher.Actions
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TURKISH: Locale = Locale.forLanguageTag("tr-TR")

@Composable
fun HomeScreen(
    emergencyNumber: String,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var askEmergency by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ClockHeader(onSecretHold = onOpenSettings)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TileRow {
                Tile("Rehber", Icons.Rounded.Contacts, Palette.ContactsBg, Palette.ContactsLine, Palette.ContactsIcon, it) {
                    Actions.openContacts(context)
                }
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

        EmergencyButton(number = emergencyNumber, onClick = { askEmergency = true })
    }

    if (askEmergency) {
        AlertDialog(
            onDismissRequest = { askEmergency = false },
            containerColor = Color.White,
            title = { Text("Acil Yardım", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold) },
            text = { Text("$emergencyNumber aransın mı?", fontSize = 26.sp, color = Palette.Ink) },
            confirmButton = {
                Button(
                    onClick = {
                        askEmergency = false
                        Actions.call(context, emergencyNumber)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Red),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(64.dp),
                ) { Text("Evet, ara", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { askEmergency = false },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, Palette.Ink),
                    modifier = Modifier.height(64.dp),
                ) { Text("Vazgeç", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Ink) }
            },
        )
    }
}

/**
 * Saat ve tarih. Saate 3 saniye basılı tutmak ayarları açar: annenin yanlışlıkla
 * girmeyeceği, yakınların bildiği gizli giriş.
 */
@Composable
private fun ClockHeader(onSecretHold: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm", TURKISH))
    val date = now.format(DateTimeFormatter.ofPattern("d MMMM EEEE", TURKISH))

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
            }
            .semantics { contentDescription = "Saat $time, $date" },
    ) {
        Text(time, fontSize = 72.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink, lineHeight = 76.sp)
        Text(date, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft)
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(8.dp))
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
private fun EmergencyButton(number: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(92.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Red, contentColor = Color.White),
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Acil Yardım", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp)
            Text(if (number == "112") "112'yi ara" else "Ara: $number", fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
        }
    }
}

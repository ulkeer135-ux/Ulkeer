package com.annem.launcher.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annem.launcher.SettingsStore

/** Yakınlar için ayarlar: saate 3 sn basılı tutunca açılır. */
@Composable
fun SettingsScreen(store: SettingsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    var permissionTick by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionTick++ }

    var emergencyDraft by remember { mutableStateOf(store.emergencyNumber) }

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

        SectionTitle("Acil durum numarası")
        Text(
            "Kırmızı düğme bu numarayı arar. 112 yerine bir yakının numarasını da yazabilirsin.",
            fontSize = 16.sp,
            color = Palette.InkSoft,
        )
        OutlinedTextField(
            value = emergencyDraft,
            onValueChange = { emergencyDraft = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                store.updateEmergencyNumber(emergencyDraft)
                emergencyDraft = store.emergencyNumber
            },
            enabled = emergencyDraft.trim() != store.emergencyNumber,
        ) { Text("Kaydet", fontSize = 18.sp) }

        HorizontalDivider()
        SectionTitle("Arama izni")
        Text(
            "Verilirse acil düğmesi (112 dışındaki numaralarda) tek dokunuşla arar.",
            fontSize = 16.sp,
            color = Palette.InkSoft,
        )
        // permissionTick okunarak izin verildikten sonra durum yazısı yenilenir.
        val granted = permissionTick >= 0 &&
            context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Doğrudan arama", fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(
                if (granted) "Verildi" else "Verilmedi",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (granted) Palette.Green else Palette.Red,
            )
        }
        if (!granted) {
            OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.CALL_PHONE) }) {
                Text("İzni ver", fontSize = 18.sp)
            }
        }

        HorizontalDivider()
        SectionTitle("Ana ekran")
        Text(
            "Bu uygulamayı varsayılan ana ekran yapınca Ana Sayfa tuşu hep buraya döner.",
            fontSize = 16.sp,
            color = Palette.InkSoft,
        )
        OutlinedButton(onClick = { openHomeSettings(context) }) {
            Text("Varsayılan ana ekranı seç", fontSize = 18.sp)
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun openHomeSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
}

@Composable
private fun BigBackButton(label: String, onClick: () -> Unit) {
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

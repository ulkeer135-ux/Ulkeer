package com.annem.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annem.launcher.InstalledApp
import com.annem.launcher.InstalledApps
import java.util.Locale

/** Bir kutuya atanacak uygulamayı seçme ekranı (yakınlar için, Ayarlar'dan açılır). */
@Composable
fun AppPickerScreen(title: String, backLabel: String, onPick: (InstalledApp) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = InstalledApps.load(context) }

    val turkish = remember { Locale.forLanguageTag("tr-TR") }
    val filtered = remember(apps, query) {
        val q = query.trim().lowercase(turkish)
        apps.orEmpty().filter { q.isEmpty() || it.label.lowercase(turkish).contains(q) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BigBackButton(label = backLabel, onClick = onBack)
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Uygulama ara") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (apps == null) {
            Text("Uygulamalar yükleniyor…", fontSize = 20.sp, color = Palette.InkSoft)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(filtered, key = { it.packageName }) { app -> AppRow(app) { onPick(app) } }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon by produceState(initialValue = InstalledApps.cachedIcon(app.packageName), app.packageName) {
        value = InstalledApps.loadIcon(context, app.packageName)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Palette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (com.annem.launcher.InternalApps.isInternal(app.packageName)) {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = Color(0xFF5B3FA0),
                        modifier = Modifier.size(40.dp),
                    )
                } else {
                    icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(48.dp)) }
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(app.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        }
    }
}

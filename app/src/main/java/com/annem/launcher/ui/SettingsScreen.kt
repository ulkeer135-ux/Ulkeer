package com.annem.launcher.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.annem.launcher.ContactStore
import com.annem.launcher.FamilyContact
import java.util.UUID

/** Yakınlar için ayarlar: saate 3 sn basılı tutunca açılır. */
@Composable
fun SettingsScreen(
    store: ContactStore,
    onBack: () -> Unit,
    onEdit: (contactId: String?) -> Unit,
) {
    val context = LocalContext.current
    var permissionTick by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissionTick++ }

    var emergencyDraft by remember { mutableStateOf(store.emergencyNumber) }
    var pendingDelete by remember { mutableStateOf<FamilyContact?>(null) }

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

        SectionTitle("Aile kişileri")
        store.contacts.forEachIndexed { index, contact ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Palette.CardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(store, contact, index, size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(contact.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(contact.phone, fontSize = 16.sp, color = Palette.InkSoft)
                    }
                    IconButton(onClick = { store.move(contact.id, -1) }, enabled = index > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Yukarı taşı")
                    }
                    IconButton(onClick = { store.move(contact.id, +1) }, enabled = index < store.contacts.size - 1) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Aşağı taşı")
                    }
                    IconButton(onClick = { onEdit(contact.id) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Düzenle")
                    }
                    IconButton(onClick = { pendingDelete = contact }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Sil", tint = Palette.Red)
                    }
                }
            }
        }
        Button(
            onClick = { onEdit(null) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Kişi ekle", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        HorizontalDivider()
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
        SectionTitle("İzinler")
        // permissionTick okunarak izin verildikten sonra satırların yenilenmesi sağlanır.
        val tick = permissionTick
        PermissionRow("Doğrudan arama", hasPermission(context, Manifest.permission.CALL_PHONE), tick)
        PermissionRow("WhatsApp görüntülü arama (rehber)", hasPermission(context, Manifest.permission.READ_CONTACTS), tick)
        OutlinedButton(onClick = {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS),
            )
        }) { Text("İzinleri ver", fontSize = 18.sp) }

        HorizontalDivider()
        SectionTitle("Ana ekran")
        Text(
            "Bu uygulamayı varsayılan ana ekran yapınca Ana Sayfa tuşu hep buraya döner.",
            fontSize = 16.sp,
            color = Palette.InkSoft,
        )
        OutlinedButton(onClick = {
            try {
                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }) { Text("Varsayılan ana ekranı seç", fontSize = 18.sp) }

        Spacer(Modifier.height(24.dp))
    }

    pendingDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("${contact.name} silinsin mi?") },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(contact.id)
                    pendingDelete = null
                }) { Text("Sil", color = Palette.Red) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Vazgeç") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, @Suppress("UNUSED_PARAMETER") tick: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(
            if (granted) "Verildi" else "Verilmedi",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (granted) Palette.Green else Palette.Red,
        )
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** Kişi ekleme / düzenleme. contactId null ise yeni kişi. */
@Composable
fun EditContactScreen(store: ContactStore, contactId: String?, onDone: () -> Unit) {
    val existing = remember(contactId) { store.contacts.firstOrNull { it.id == contactId } }
    val id = remember(contactId) { existing?.id ?: UUID.randomUUID().toString() }
    var name by remember(contactId) { mutableStateOf(existing?.name ?: "") }
    var phone by remember(contactId) { mutableStateOf(existing?.phone ?: "") }
    var photoVersion by remember(contactId) { mutableLongStateOf(existing?.photoVersion ?: 0L) }
    var photoError by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            if (store.savePhoto(id, uri)) {
                photoVersion = System.currentTimeMillis()
                photoError = false
            } else {
                photoError = true
            }
        }
    }

    val cancel = {
        // Yeni kişi vazgeçildiyse seçilmiş fotoğrafı temizle.
        // Mevcut kişide fotoğraf zaten değiştiyse onu koru (dosyanın üzerine yazıldı).
        if (existing == null) {
            store.photoFile(id).delete()
        } else if (photoVersion != existing.photoVersion) {
            store.upsert(existing.copy(photoVersion = photoVersion))
        }
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BigBackButton(label = "Vazgeç", onClick = cancel)
        Text(
            if (existing == null) "Yeni kişi" else "Kişiyi düzenle",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                store,
                FamilyContact(id, name.ifBlank { "?" }, phone, photoVersion),
                store.contacts.size,
                size = 96.dp,
            )
            Spacer(Modifier.width(16.dp))
            OutlinedButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (photoVersion == 0L) "Fotoğraf seç" else "Fotoğrafı değiştir", fontSize = 17.sp)
            }
        }
        if (photoError) {
            Text("Fotoğraf okunamadı, başka bir fotoğraf dene.", color = Palette.Red, fontSize = 16.sp)
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Ekranda görünecek ad (ör. Oğlum Ahmet)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Telefon numarası (ör. 0532 123 45 67)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "WhatsApp görüntülü aramanın tek dokunuşla açılması için bu numara telefonun rehberinde de kayıtlı olmalı.",
            fontSize = 15.sp,
            color = Palette.InkSoft,
        )

        Button(
            onClick = {
                store.upsert(FamilyContact(id, name.trim(), phone.trim(), photoVersion))
                onDone()
            },
            enabled = name.isNotBlank() && phone.count { it.isDigit() } >= 3,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Green),
        ) { Text("Kaydet", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }
}

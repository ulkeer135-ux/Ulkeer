package com.annem.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annem.launcher.ui.AnnemTheme
import com.annem.launcher.ui.AskScreen
import com.annem.launcher.ui.Palette

/**
 * Robota Sor (ayrı uygulama): Annem'deki "Robota Sor" ekranı, tek başına.
 * Sesle soru sorulur, cevap internetten (Groq Compound) gelir, ekranda yazar ve sesli okunur.
 * Ayarlar: Groq anahtarı (zorunlu), Gemini ses anahtarı (isteğe bağlı, doğal ses).
 */
class RobotActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = DreamStore(this, historyKey = "ask_history")
        setContent {
            AnnemTheme {
                var settings by remember { mutableStateOf(!store.ready) }
                BackHandler(enabled = settings && store.ready) { settings = false }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Palette.Cream)
                        .systemBarsPadding(),
                ) {
                    if (settings) {
                        RobotSettings(store, onDone = { settings = false })
                    } else {
                        AskScreen(store = store, onBack = { finish() }, onSettings = { settings = true })
                    }
                }
            }
        }
    }
}

@Composable
private fun RobotSettings(store: DreamStore, onDone: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Robota Sor · Ayarlar", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Palette.Ink)

        Text("1. Groq anahtarı (gerekli, ücretsiz)", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        Text(
            "Cevapları Groq yapay zekâsı yazar ve internette arar. Kredi kartı istemez.\n" +
                "• Aşağıdaki düğmeyle console.groq.com'u aç, Google hesabınla gir.\n" +
                "• \"Create API Key\" de, \"gsk_\" ile başlayan anahtarı kopyala, buraya yapıştır.",
            fontSize = 17.sp, color = Palette.InkSoft, lineHeight = 24.sp,
        )
        OutlinedButton(onClick = { open("https://console.groq.com/keys") }) { Text("Groq anahtarı al", fontSize = 18.sp) }
        var groqKey by remember { mutableStateOf(store.groqKey.value) }
        OutlinedTextField(
            value = groqKey,
            onValueChange = { groqKey = it },
            label = { Text("Groq API anahtarı") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { store.updateGroqKey(groqKey) },
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Ink),
        ) { Text("Kaydet", fontSize = 18.sp, color = Color.White) }
        Text(
            if (store.ready) "✓ Anahtar kayıtlı, Robota Sor hazır." else "Anahtar henüz girilmedi.",
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = if (store.ready) Palette.Green else Palette.Red,
        )

        Spacer(Modifier.height(8.dp))
        Text("2. Doğal ses (isteğe bağlı)", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        Text(
            "Cevabı daha doğal bir sesle okumak için ücretsiz Google Gemini anahtarı girilebilir " +
                "(aistudio.google.com › Create API key). Girilmezse telefonun kendi sesiyle okunur.",
            fontSize = 17.sp, color = Palette.InkSoft, lineHeight = 24.sp,
        )
        OutlinedButton(onClick = { open("https://aistudio.google.com/apikey") }) { Text("Ses anahtarı al", fontSize = 18.sp) }
        var voiceKey by remember { mutableStateOf(store.apiKey.value) }
        OutlinedTextField(
            value = voiceKey,
            onValueChange = { voiceKey = it },
            label = { Text("Gemini ses anahtarı") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = { store.updateApiKey(voiceKey) }) { Text("Ses anahtarını kaydet", fontSize = 18.sp) }
        if (store.apiKey.value.isNotBlank()) {
            OutlinedButton(onClick = { store.updateNaturalVoice(!store.naturalVoice.value) }) {
                Text(if (store.naturalVoice.value) "Telefonun sesini kullan" else "Doğal sesi kullan", fontSize = 18.sp)
            }
        }

        if (store.history.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            var confirm by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { confirm = true }) {
                Text("Tüm soruları sil (${store.history.size})", fontSize = 18.sp, color = Palette.Red)
            }
            if (confirm) {
                AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text("Tüm sorular silinsin mi?", fontSize = 22.sp, fontWeight = FontWeight.Black) },
                    confirmButton = {
                        Button(
                            onClick = { store.clearAll(); confirm = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Red),
                        ) { Text("Evet, sil", fontSize = 18.sp, color = Color.White) }
                    },
                    dismissButton = { OutlinedButton(onClick = { confirm = false }) { Text("Vazgeç", fontSize = 18.sp) } },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDone,
            enabled = store.ready,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F6FA8)),
        ) { Text(if (store.ready) "Soru sormaya başla" else "Önce anahtarı kaydet", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White) }
    }
}

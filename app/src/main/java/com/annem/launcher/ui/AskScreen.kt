package com.annem.launcher.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.annem.launcher.DreamEntry
import com.annem.launcher.DreamInterpreter
import com.annem.launcher.DreamResult
import com.annem.launcher.DreamStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DreamPurple = Color(0xFF1F6FA8)
private val DreamPurpleSoft = Color(0xFFE0EEF9)
private val DreamPurpleLine = Color(0xFFC5DDF2)
private val TR_LOCALE: Locale = Locale.forLanguageTag("tr-TR")

private sealed interface AskPhase {
    data object Idle : AskPhase
    data object Listening : AskPhase
    data object Finishing : AskPhase
    data object Thinking : AskPhase
    data class Answer(val entry: DreamEntry) : AskPhase
    data class Failed(val dream: String, val message: String, val retryable: Boolean, val detail: String = "") : AskPhase
}

/**
 * Robota Sor: anne sorusunu sesle sorar, cevap (internetten) ekranda yazar ve sesli okunur.
 * Rüya Tabirleri ekranının kopyası; servis AskRobot, geçmiş ayrı.
 * Konuşma tanıma ve seslendirme telefonun kendi (Google) servisleriyle yapılır.
 */
@Composable
fun AskScreen(store: DreamStore, onBack: () -> Unit, inSheet: Boolean = false, onSettings: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf<AskPhase>(AskPhase.Idle) }
    var transcript by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var micReady by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var liveReading by remember { mutableStateOf("") }
    // Klavyeyle yazarak sorma.
    var typing by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    // "Soruları yanıtla" modundaysa yanıtlanan yorum (değilse null).
    var followUpFor by remember { mutableStateOf<DreamEntry?>(null) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    // --- Seslendirme: Gemini doğal sesi, olmazsa telefonun en iyi Türkçe sesi ---
    val tts = remember {
        com.annem.launcher.DreamPlayer(
            context = context,
            scope = scope,
            apiKey = { store.apiKey.value },
            natural = { store.naturalVoice.value },
            onState = { s, p -> speaking = s; preparing = p },
        )
    }

    fun answerQuestions(answer: String) {
        val base = followUpFor ?: return
        phase = AskPhase.Thinking
        liveReading = ""
        scope.launch {
            when (val r = com.annem.launcher.AskRobot.followUp(store.groqKey.value, base.dream, base.reading, answer, context, store.history.filter { it.time != base.time })) {
                is DreamResult.Ok -> {
                    // Eski kayıt yerine: rüya + ek bilgi + yeni yorum.
                    val entry = DreamEntry(System.currentTimeMillis(), base.dream + "\nEk bilgi: " + answer, r.text, r.by)
                    store.remove(base)
                    store.add(entry)
                    followUpFor = null
                    phase = AskPhase.Answer(entry)
                    tts.speak(com.annem.launcher.AskRobot.speech(r.text), entry.time)
                }
                is DreamResult.Error -> phase = AskPhase.Failed(answer, r.message, r.retryable, r.detail)
            }
        }
    }

    fun interpret(dream: String) {
        if (followUpFor != null) { answerQuestions(dream); return }
        phase = AskPhase.Thinking
        liveReading = ""
        scope.launch {
            val r = com.annem.launcher.AskRobot.interpret(store.groqKey.value, dream, context, store.history.toList()) { text ->
                mainHandler.post { liveReading = text }
            }
            when (r) {
                is DreamResult.Ok -> {
                    val entry = DreamEntry(System.currentTimeMillis(), dream, r.text, r.by)
                    store.add(entry)
                    phase = AskPhase.Answer(entry)
                    tts.speak(com.annem.launcher.AskRobot.speech(r.text), entry.time)
                }
                is DreamResult.Error -> phase = AskPhase.Failed(dream, r.message, r.retryable, r.detail)
            }
        }
    }

    // --- Konuşma tanıma: anne "Bitti" diyene kadar dinlemeye devam eder ---
    val listener = remember {
        AskListener(
            context = context,
            onPartial = { partial = it },
            onFinal = { text ->
                partial = ""
                if (text.isNotBlank()) transcript = (transcript + " " + text).trim()
            },
            onReady = { micReady = it },
            onStopped = { reason ->
                partial = ""
                if (phase == AskPhase.Finishing || phase == AskPhase.Listening) {
                    if (transcript.isNotBlank()) {
                        interpret(transcript)
                    } else {
                        val back = followUpFor
                        followUpFor = null
                        phase = if (back != null) AskPhase.Answer(back) else AskPhase.Idle
                        notice = reason ?: "Sesinizi duyamadım. Düğmeye basıp tekrar sorun."
                    }
                }
            },
        )
    }
    // Telefonda konuşma tanıma servisi yoksa Google'ın konuşma penceresi kullanılır.
    val speechDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        if (text.isNotBlank()) {
            transcript = text
            interpret(text)
        } else {
            phase = AskPhase.Idle
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            phase = AskPhase.Listening
            listener.start(delayMs = 300L)
        } else {
            notice = "Sorunuzu dinleyebilmem için mikrofon izni gerekiyor."
        }
    }

    fun startListening() {
        notice = null
        transcript = ""
        partial = ""
        tts.stop()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechDialog.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Sorunuzu sorun")
                )
            } catch (e: Exception) {
                notice = "Bu telefonda konuşma tanıma bulunamadı."
            }
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        // Az önce yorum okunduysa hoparlör sussun, sonra mikrofon açılsın (çakışmasın).
        val wait = if (speaking || preparing || phase is AskPhase.Answer) 600L else 200L
        phase = AskPhase.Listening
        listener.start(delayMs = wait)
    }

    // Ekran kendiliğinden kararmasın: anlatırken, yorum beklenirken, okunurken ve okuma
    // bittikten sonra 3 dakika (rahatça okusun). Boş beklerken telefonun normal ayarı geçerli.
    var readingGrace by remember { mutableStateOf(false) }
    LaunchedEffect(phase, speaking, preparing) {
        readingGrace = phase is AskPhase.Answer
        if (phase is AskPhase.Answer && !speaking && !preparing) {
            delay(3 * 60_000L)
            readingGrace = false
        }
    }
    val keepOn = phase == AskPhase.Listening || phase == AskPhase.Finishing || phase == AskPhase.Thinking ||
        speaking || preparing || readingGrace
    val view = LocalView.current
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    // Ekrandan çıkınca ya da telefon kilitlenince sus ve dinlemeyi bırak.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) {
                tts.stop()
                if (phase == AskPhase.Listening) {
                    listener.cancel()
                    phase = AskPhase.Idle
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            listener.destroy()
            tts.shutdown()
        }
    }

    // Her ekran değişiminde en üste kaydır: mikrofon ve yeni içerik hep görünsün.
    val scroll = rememberScrollState()
    LaunchedEffect(phase) { scroll.scrollTo(0) }

    // Kapatma: hava durumu çekmecesindeki gibi balon toplanması (1 sn), sonra ana ekran.
    val collapse = remember { androidx.compose.animation.core.Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val closeAnimated: () -> Unit = {
        if (inSheet) {
            // Cam çekmecede: okuma/dinleme dursun, balon efektini çekmece yapar.
            try { tts.stop() } catch (e: Exception) { }
            try { listener.cancel() } catch (e: Exception) { }
            onBack()
        } else if (!closing) {
            closing = true
            // Okuma ve dinleme hemen dursun.
            try { tts.stop() } catch (e: Exception) { }
            try { listener.cancel() } catch (e: Exception) { }
            scope.launch(object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 1f }) {
                try {
                    collapse.animateTo(
                        1f,
                        androidx.compose.animation.core.tween(
                            durationMillis = 1000,
                            easing = androidx.compose.animation.core.CubicBezierEasing(0.45f, 0f, 0.55f, 1f),
                        ),
                    )
                } finally {
                    onBack()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = collapse.value
                if (p > 0f) {
                    // Balonun merkezi: ekranın alt ortası.
                    val cx = size.width / 2f
                    val cy = size.height
                    val r = kotlin.math.hypot(cx, cy) * (1f - p)
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    scaleX = 1f - 0.4f * p
                    scaleY = 1f - 0.4f * p
                    alpha = if (p < 0.7f) 1f else 1f - (p - 0.7f) / 0.3f
                    clip = true
                    shape = androidx.compose.foundation.shape.GenericShape { _, _ ->
                        addOval(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset(cx, cy), r))
                    }
                }
            }
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Üstte yan yana: "Ana Ekran" ve "Kapat" (ikisi de balon efektiyle ana ekrana döner).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onSettings != null) {
                TopButton(Icons.Rounded.Settings, "Ayarlar", onSettings)
            } else {
                TopButton(Icons.AutoMirrored.Rounded.ArrowBack, "Ana Ekran", closeAnimated)
            }
            TopButton(Icons.Rounded.Close, "Kapat", closeAnimated)
        }
        Text("Gökçe", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Palette.Ink)

        if (!store.ready) {
            InfoCard(
                if (onSettings != null) "Gökçe henüz kurulmadı. Üstteki \"Ayarlar\" düğmesine basıp ücretsiz Groq anahtarını girin." else
                "Robota Sor henüz kurulmadı. Yakınınız saate 3 saniye basılı tutup Ayarlar › " +
                    "\"Rüya Tabirleri\" bölümünden ücretsiz Groq anahtarını girmeli (ikisi aynı anahtarı kullanır)."
            )
            return@Column
        }

        when (val p = phase) {
            AskPhase.Idle -> {
                Text(
                    "Mavi düğmeye basın ve sorunuzu sorun. Bitince \"Bitti\" düğmesine basın.",
                    fontSize = 20.sp,
                    color = Palette.InkSoft,
                )
                if (typing) {
                    TypeCard(
                        value = typed,
                        onValue = { typed = it },
                        label = if (followUpFor != null) "Eklemek istediğinizi yazın" else "Sorunuzu yazın",
                        onSend = {
                            val q = typed.trim()
                            if (q.isNotEmpty()) {
                                typing = false
                                typed = ""
                                transcript = q
                                interpret(q)
                            }
                        },
                        onCancel = { typing = false; followUpFor = null },
                    )
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        BigMicButton(listening = false) { startListening() }
                        // Mikrofonun yanında küçük klavye: yazarak sor.
                        KeyboardButton(modifier = Modifier.align(Alignment.BottomEnd)) { tts.stop(); typing = true }
                    }
                }
                notice?.let { InfoCard(it) }
                DreamHistory(store, onOpen = { entry ->
                    phase = AskPhase.Answer(entry)
                    tts.speak(com.annem.launcher.AskRobot.speech(entry.reading), entry.time)
                })
            }

            AskPhase.Listening, AskPhase.Finishing -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BigMicButton(listening = p == AskPhase.Listening) { }
                }
                Text(
                    when {
                        p != AskPhase.Listening -> "Bir saniye…"
                        micReady -> "Dinliyorum… Sorunuzu sorun."
                        else -> "Mikrofon açılıyor…"
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = DreamPurple,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                followUpFor?.let {
                    val qs = com.annem.launcher.AskRobot.split(it.reading).second
                    if (qs.isNotEmpty()) QuestionsCard(qs)
                    else TextCard(title = "Sorunuz", text = it.dream, small = true)
                }
                val shown = (transcript + " " + partial).trim()
                if (shown.isNotEmpty()) TextCard(title = if (followUpFor != null) "Eklediğiniz" else "Söyledikleriniz", text = shown)
                Button(
                    onClick = {
                        phase = AskPhase.Finishing
                        listener.finish()
                    },
                    enabled = p == AskPhase.Listening,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Green, contentColor = Color.White),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Bitti, sor", fontSize = 26.sp, fontWeight = FontWeight.Black)
                }
                OutlinedButton(
                    onClick = {
                        listener.cancel()
                        transcript = ""
                        val back = followUpFor
                        followUpFor = null
                        phase = if (back != null) AskPhase.Answer(back) else AskPhase.Idle
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("Vazgeç", fontSize = 20.sp, color = Palette.Ink) }
            }

            AskPhase.Thinking -> {
                TextCard(title = if (followUpFor != null) "Eklediğiniz" else "Sorunuz", text = transcript, small = true)
                if (liveReading.isNotBlank()) TextCard(title = "Cevap yazılıyor…", text = liveReading, highlight = true)
                if (liveReading.isBlank()) Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = DreamPurple, strokeWidth = 5.dp, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Cevap aranıyor…", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DreamPurple)
                }
            }

            is AskPhase.Answer -> {
                notice?.let { InfoCard(it) }
                TextCard(title = "Sorunuz", text = p.entry.dream, small = true)
                val (mainText, questions) = com.annem.launcher.AskRobot.split(p.entry.reading)
                TextCard(title = "Cevap", text = mainText, highlight = true)
                if (questions.isNotEmpty()) QuestionsCard(questions)
                if (preparing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = DreamPurple, strokeWidth = 4.dp, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Ses hazırlanıyor…", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DreamPurple)
                    }
                }
                Button(
                    onClick = { if (speaking) tts.stop() else tts.speak(com.annem.launcher.AskRobot.speech(p.entry.reading), p.entry.time) },
                    modifier = Modifier.fillMaxWidth().height(68.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DreamPurple, contentColor = Color.White),
                ) {
                    Icon(if (speaking) Icons.Rounded.Stop else Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (speaking) "Okumayı durdur" else "Cevabı tekrar dinle", fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                val asks = questions.isNotEmpty()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            tts.stop()
                            followUpFor = p.entry
                            startListening()
                        },
                        modifier = Modifier.weight(1f).height(72.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Green, contentColor = Color.White),
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Bir şey daha sor", fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    KeyboardButton(size = 72.dp, color = Palette.Green, showLabel = false) {
                        tts.stop()
                        followUpFor = p.entry
                        typed = ""
                        typing = true
                        phase = AskPhase.Idle
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        NewDreamButton {
                            // Tek dokunuşla doğrudan dinlemeye başla.
                            tts.stop()
                            followUpFor = null
                            startListening()
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    KeyboardButton(size = 60.dp, showLabel = false) {
                        tts.stop()
                        followUpFor = null
                        typed = ""
                        typing = true
                        phase = AskPhase.Idle
                    }
                }
                Text(
                    "Cevapları yapay zekâ hazırlar; önemli konularda (sağlık, para) mutlaka bir yakınınıza ya da uzmana danışın." +
                        (if (p.entry.by.isNotBlank()) " Kaynak: ${p.entry.by}." else ""),
                    fontSize = 15.sp,
                    color = Palette.InkSoft,
                )
            }

            is AskPhase.Failed -> {
                if (p.dream.isNotBlank()) TextCard(title = "Sorunuz", text = p.dream, small = true)
                InfoCard(p.message)
                if (p.detail.isNotBlank()) {
                    Text("Teknik bilgi: ${p.detail}", fontSize = 13.sp, color = Palette.InkSoft)
                }
                if (p.retryable) {
                    Button(
                        onClick = { interpret(p.dream) },
                        modifier = Modifier.fillMaxWidth().height(68.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DreamPurple, contentColor = Color.White),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Tekrar dene", fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                }
                NewDreamButton { followUpFor = null; startListening() }
            }
        }
    }
}

@Composable
private fun TopButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(64.dp),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, Palette.Ink),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Palette.Ink),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 18.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun BigMicButton(listening: Boolean, onClick: () -> Unit) {
    val pulse = if (listening) {
        val t = rememberInfiniteTransition(label = "mic")
        t.animateFloat(1f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale").value
    } else {
        1f
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(190.dp).scale(pulse),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (listening) Palette.Red else DreamPurple,
                contentColor = Color.White,
                disabledContainerColor = DreamPurple,
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = "Soru sor", modifier = Modifier.size(96.dp))
        }
        if (!listening) {
            Spacer(Modifier.height(10.dp))
            Text("Soru sor", fontSize = 26.sp, fontWeight = FontWeight.Black, color = DreamPurple)
        }
    }
}

/** Küçük klavye düğmesi: telefonun klavyesiyle yazarak sormak için. */
@Composable
private fun KeyboardButton(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    color: Color = DreamPurple,
    showLabel: Boolean = true,
    onClick: () -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.size(size),
            shape = if (showLabel) CircleShape else RoundedCornerShape(18.dp),
            border = BorderStroke(2.dp, color),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = color),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(Icons.Rounded.Keyboard, contentDescription = "Yazarak sor", modifier = Modifier.size(size * 0.5f))
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text("Yaz", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

/** Klavyeyle soru yazma kartı: açılınca klavye kendiliğinden gelir. */
@Composable
private fun TypeCard(value: String, onValue: (String) -> Unit, label: String, onSend: () -> Unit, onCancel: () -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        try { focus.requestFocus(); keyboard?.show() } catch (_: Exception) { }
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        border = BorderStroke(2.dp, DreamPurpleLine),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = onValue,
                label = { Text(label, fontSize = 18.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 21.sp, color = Palette.Ink),
                minLines = 3,
                maxLines = 8,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { keyboard?.hide(); onCancel() },
                    modifier = Modifier.height(60.dp),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("Vazgeç", fontSize = 20.sp, color = Palette.Ink) }
                Button(
                    onClick = { keyboard?.hide(); onSend() },
                    enabled = value.isNotBlank(),
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DreamPurple, contentColor = Color.White),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Gönder", fontSize = 21.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun NewDreamButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, DreamPurple),
    ) {
        Icon(Icons.Rounded.Mic, contentDescription = null, tint = DreamPurple, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(8.dp))
        Text("Yeni soru sor", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = DreamPurple)
    }
}

@Composable
private fun TextCard(title: String, text: String, small: Boolean = false, highlight: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (highlight) DreamPurpleSoft else Color.White,
        border = BorderStroke(2.dp, if (highlight) DreamPurpleLine else Palette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = if (highlight) DreamPurple else Palette.InkSoft)
            Text(
                text,
                fontSize = if (small) 18.sp else 23.sp,
                lineHeight = if (small) 25.sp else 33.sp,
                color = Palette.Ink,
                fontWeight = if (small) FontWeight.Normal else FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Palette.SmsBg,
        border = BorderStroke(2.dp, Palette.SmsLine),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, fontSize = 19.sp, color = Palette.Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun DreamHistory(store: DreamStore, onOpen: (DreamEntry) -> Unit) {
    if (store.history.isEmpty()) return
    var toDelete by remember { mutableStateOf<DreamEntry?>(null) }
    Text("Önceki sorularınız", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Palette.InkSoft)
    val fmt = remember { SimpleDateFormat("d MMMM, HH:mm", TR_LOCALE) }
    store.history.forEach { entry ->
        Surface(
            onClick = { onOpen(entry) },
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Palette.CardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(DreamPurpleSoft),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.VolumeUp, contentDescription = "Dinle", tint = DreamPurple, modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(fmt.format(Date(entry.time)), fontSize = 15.sp, color = Palette.InkSoft)
                    Text(entry.dream, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                // Sil: küçük, köşede; yanlışlıkla basılırsa önce sorar.
                IconButton(onClick = { toDelete = entry }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Soruyu sil", tint = Palette.InkSoft, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
    toDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Bu soru silinsin mi?", fontSize = 24.sp, fontWeight = FontWeight.Black) },
            text = {
                Text(entry.dream, fontSize = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            },
            confirmButton = {
                Button(
                    onClick = { store.remove(entry); toDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Red, contentColor = Color.White),
                    modifier = Modifier.height(56.dp),
                ) { Text("Evet, sil", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { toDelete = null }, modifier = Modifier.height(56.dp)) {
                    Text("Vazgeç", fontSize = 20.sp, color = Palette.Ink)
                }
            },
        )
    }
}

/**
 * Sürekli dinleme: Android konuşma tanıma her duraklamada durur; anne "Bitti"ye basana kadar
 * yeniden başlatılır ve parçalar birleştirilir. Uzun sessizlikte kendiliğinden biter.
 *
 * Her tanıyıcı oturumunun kendi dinleyicisi ve "nesil" numarası var: kapatılan eski oturumdan
 * gelen geç hata haberleri yenisini bozmasın. Geçici hatalarda (meşgul, ses, bağlantı kopması)
 * kısa bekleyip birkaç kez yeniden denenir; ekran dinleme modundan hemen çıkmaz.
 */
private class AskListener(
    context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onStopped: (reason: String?) -> Unit,
    private val onReady: (Boolean) -> Unit = {},
) {
    private val app = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private var active = false     // anne hâlâ anlatıyor
    private var finishing = false  // "Bitti"ye basıldı, son parça bekleniyor
    private var silentRounds = 0
    private var failures = 0
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)

    private fun newListener(myGen: Int) = object : RecognitionListener {
        private fun current() = myGen == generation && active

        override fun onReadyForSpeech(params: Bundle?) { if (current()) { failures = 0; onReady(true) } }
        override fun onBeginningOfSpeech() { if (current()) silentRounds = 0 }
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            if (!current()) return
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (t.isNotBlank()) onPartial(t)
        }

        override fun onResults(results: Bundle?) {
            if (!current()) return
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            onFinal(t)
            if (t.isNotBlank()) silentRounds = 0
            continueOrStop()
        }

        override fun onError(error: Int) {
            if (!current()) return
            onReady(false)
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    silentRounds++
                    continueOrStop()
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> end("Mikrofon izni gerekiyor.")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                    retryOrEnd(error, "İnternet bağlantısı zayıf, sesiniz anlaşılamadı. Tekrar deneyin.")
                else -> retryOrEnd(error, "Mikrofon açılamadı (kod $error). Tekrar deneyin.")
            }
        }
    }

    /** Geçici hata: yeni oturumla birkaç kez yeniden dene, olmazsa sebebiyle bitir. */
    private fun retryOrEnd(error: Int, message: String) {
        if (finishing) { end(null); return }
        failures++
        if (failures <= 4) {
            val myGen = ++generation
            recognizer?.runCatching { destroy() }
            recognizer = null
            main.postDelayed({ if (myGen == generation && active) { createRecognizer(myGen); listenAgain() } }, 500L * failures)
        } else {
            end(message)
        }
    }

    private fun continueOrStop() {
        // Yaklaşık 3 tur (~15–20 sn) hiç ses gelmezse anlatım bitti sayılır.
        if (active && !finishing && silentRounds < 3) listenAgain() else end(null)
    }

    private fun listenAgain() {
        try { recognizer?.startListening(intent) } catch (e: Exception) { retryOrEnd(-1, "Mikrofon açılamadı. Tekrar deneyin.") }
    }

    private fun createRecognizer(myGen: Int) {
        recognizer = SpeechRecognizer.createSpeechRecognizer(app).also { it.setRecognitionListener(newListener(myGen)) }
    }

    /** Dinlemeye başla. delayMs: az önce ses çalındıysa hoparlörün susması için bekle. */
    fun start(delayMs: Long = 0L) {
        active = true
        finishing = false
        silentRounds = 0
        failures = 0
        onReady(false)
        val myGen = ++generation
        recognizer?.runCatching { destroy() }
        recognizer = null
        main.postDelayed({
            if (myGen == generation && active) { createRecognizer(myGen); listenAgain() }
        }, delayMs)
    }

    /** "Bitti": son parçayı al, sonra dur. */
    fun finish() {
        if (!active) { onStopped(null); return }
        finishing = true
        try { recognizer?.stopListening() } catch (e: Exception) { end(null) }
        // Tanıyıcı yanıt vermezse en geç 4 sn sonra elde olanla devam et.
        val myGen = generation
        main.postDelayed({ if (active && myGen == generation) end(null) }, 4000)
    }

    fun cancel() {
        active = false
        finishing = false
        generation++
        onReady(false)
        try { recognizer?.cancel() } catch (_: Exception) {}
    }

    private fun end(reason: String?) {
        if (!active) return
        active = false
        finishing = false
        generation++
        onReady(false)
        try { recognizer?.cancel() } catch (_: Exception) {}
        onStopped(reason)
    }

    fun destroy() {
        cancel()
        main.removeCallbacksAndMessages(null)
        recognizer?.runCatching { destroy() }
        recognizer = null
    }
}

/** Yapay zekânın soruları: yorumdan ayrı, göze çarpan kart. */
@Composable
private fun QuestionsCard(questions: List<String>) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFE6F4EA),
        border = BorderStroke(2.dp, Color(0xFFB9DFC4)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Size soruyor", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Green)
            questions.forEach { q ->
                Text("• $q", fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            }
        }
    }
}

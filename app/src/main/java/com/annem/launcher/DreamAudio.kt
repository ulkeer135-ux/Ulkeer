package com.annem.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Gemini'nin doğal sesiyle (TTS) okuma. Ücretsiz katmanda günlük sınır düşük olduğu için
 * her yorum en fazla 2 istekte seslendirilir ve ses dosyası saklanır (tekrar dinlemek bedava).
 */
object GeminiSpeech {

    const val VOICE = "Sulafat" // Google'ın "sıcak" diye tanımladığı ses

    // Okuma tarzı metne EKLENMEZ (yoksa model talimatı da sesli okuyor); ayrı alanda verilir.
    private const val STYLE = "warm, calm and gentle, at a natural conversational pace, like a kind elder"

    // Yeni "interactions" uç noktası ve modelleri; olmazsa eski generateContent TTS modeli.
    private val INTERACTION_MODELS = listOf("gemini-3.8-flash-tts", "gemini-3.1-flash-tts-preview", "gemini-3.8-flash-lite-tts")
    private const val LEGACY_MODEL = "gemini-2.5-flash-preview-tts"

    /** WAV baytları ya da null (başarısız). */
    suspend fun synthesize(apiKey: String, text: String): ByteArray? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        for (model in INTERACTION_MODELS) {
            interaction(apiKey, model, text)?.let { return@withContext it }
        }
        legacy(apiKey, text)
    }

    private fun interaction(apiKey: String, model: String, text: String): ByteArray? {
        // Önce tarz notuyla; bu alanı kabul etmezse yalnızca metinle.
        return interactionRequest(apiKey, model, text, withStyle = true)
            ?: interactionRequest(apiKey, model, text, withStyle = false)
    }

    private fun interactionRequest(apiKey: String, model: String, text: String, withStyle: Boolean): ByteArray? {
        val input: Any = if (withStyle) {
            JSONArray().put(
                JSONObject().put("type", "user_input").put(
                    "content",
                    JSONArray().put(
                        JSONObject().put("type", "text").put("text", text).put(
                            "annotations",
                            JSONArray().put(JSONObject().put("type", "speech_metadata").put("style", STYLE)),
                        )
                    ),
                )
            )
        } else {
            text
        }
        val body = JSONObject()
            .put("model", model)
            .put("input", input)
            .put("response_format", JSONObject().put("type", "audio"))
            .put("generation_config", JSONObject().put("speech_config", JSONArray().put(JSONObject().put("voice", VOICE))))
        val json = post("https://generativelanguage.googleapis.com/v1beta/interactions", apiKey, body) ?: return null
        return findAudio(json)?.let { toWav(it) }
    }

    private fun legacy(apiKey: String, text: String): ByteArray? {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig",
                        JSONObject().put(
                            "voiceConfig",
                            JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", VOICE)),
                        ),
                    ),
            )
        val json = post(
            "https://generativelanguage.googleapis.com/v1beta/models/$LEGACY_MODEL:generateContent", apiKey, body,
        ) ?: return null
        return findAudio(json)?.let { toWav(it) }
    }

    private fun post(url: String, apiKey: String, body: JSONObject): Any? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 45_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(text)
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Yanıtın neresinde olursa olsun son ses verisini bulur (yeni ve eski biçim). */
    private fun findAudio(node: Any?): ByteArray? {
        var found: String? = null
        fun walk(n: Any?) {
            when (n) {
                is JSONObject -> {
                    val data = n.optString("data", "")
                    if (data.length > 200) {
                        val type = n.optString("type", "")
                        val mime = n.optString("mime_type", n.optString("mimeType", ""))
                        if (type == "audio" || mime.startsWith("audio")) found = data
                    }
                    n.keys().forEach { k -> walk(n.opt(k)) }
                }
                is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i))
            }
        }
        walk(node)
        return found?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
    }

    /** Ham 24 kHz / 16 bit / mono PCM geldiyse başına WAV başlığı ekler. */
    private fun toWav(bytes: ByteArray): ByteArray {
        if (bytes.size > 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") return bytes
        val rate = 24_000
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + bytes.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(bytes.size)
        }.array()
        return ByteArrayOutputStream(44 + bytes.size).apply { write(header); write(bytes) }.toByteArray()
    }
}

/**
 * Yorumu okur: önce Gemini doğal sesi (ilk paragraf ayrı, hızlı başlasın; geri kalanı tek
 * parça), olmazsa telefondaki en iyi Türkçe Google sesi. Sesler dosyada saklanır.
 */
class DreamPlayer(
    context: Context,
    private val scope: CoroutineScope,
    private val apiKey: () -> String,
    private val natural: () -> Boolean,
    private val onState: (speaking: Boolean, preparing: Boolean) -> Unit,
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val device = DeviceVoice(app) { s -> onState(s, false) }
    private var job: Job? = null
    private var player: MediaPlayer? = null
    // v2: eski sürümün sesleri talimatı da okuyordu; o klasör silinir.
    // v3: parçalama değişti; eski parçalı sesler artık eşleşmez, silinir.
    private val dir = File(app.filesDir, "dream_audio_v3").apply {
        File(app.filesDir, "dream_audio").deleteRecursively()
        File(app.filesDir, "dream_audio_v2").deleteRecursively()
        mkdirs()
    }

    fun speak(text: String, key: Long) {
        stop()
        if (!natural()) { device.speak(text); return }
        val chunks = splitChunks(text)
        job = scope.launch {
            onState(true, true)
            // Parça parça: biri çalarken sıradaki hazırlanır. Bir parça zamanında gelmezse
            // kalan metin telefonun sesiyle okunur; okuma hiçbir zaman yarıda kalmaz.
            var next: Deferred<File?>? = async { safeSynth(key, chunks, 0) }
            for (i in chunks.indices) {
                val current = next
                next = if (i + 1 < chunks.size) async { safeSynth(key, chunks, i + 1) } else null
                val wait = if (i == 0) FIRST_WAIT_MS else NEXT_WAIT_MS
                val file = withTimeoutOrNull(wait) { current?.await() }
                if (file == null) {
                    next?.cancel()
                    onState(true, false)
                    device.speakAndWait(chunks.drop(i).joinToString(" "))
                    break
                }
                onState(true, false)
                if (!play(file)) {
                    next?.cancel()
                    device.speakAndWait(chunks.drop(i).joinToString(" "))
                    break
                }
            }
            onState(false, false)
            pruneOld()
        }
    }

    private suspend fun safeSynth(key: Long, chunks: List<String>, i: Int): File? = try {
        cachedOrSynth(File(dir, "${key}_$i.wav"), chunks[i])
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        null // bellek ya da ağ hatası: telefonun sesine geçilir
    }

    fun stop() {
        job?.cancel()
        job = null
        player?.runCatching { stop(); release() }
        player = null
        device.stop()
        onState(false, false)
    }

    fun shutdown() {
        stop()
        device.shutdown()
    }

    private companion object {
        const val FIRST_WAIT_MS = 8_000L
        const val NEXT_WAIT_MS = 25_000L
    }

    private suspend fun cachedOrSynth(file: File, text: String): File? {
        if (file.exists() && file.length() > 1000) return file
        val wav = GeminiSpeech.synthesize(apiKey(), text) ?: return null
        return withContext(Dispatchers.IO) { file.writeBytes(wav); file }
    }

    /** Çalar; dosya bozuksa false. */
    private suspend fun play(file: File): Boolean = suspendCancellableCoroutine { cont ->
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { if (cont.isActive) cont.resume(true) }
            mp.setOnErrorListener { _, _, _ -> file.delete(); if (cont.isActive) cont.resume(false); true }
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            file.delete()
            if (cont.isActive) cont.resume(false)
        }
        cont.invokeOnCancellation { main.post { mp.runCatching { stop(); release() } } }
    }

    /** Son 10 rüyanın sesleri kalsın. */
    private fun pruneOld() {
        val keys = dir.listFiles()?.mapNotNull { it.name.substringBefore('_').toLongOrNull() }?.distinct()?.sortedDescending()
            ?: return
        val keep = keys.take(10).toSet()
        dir.listFiles()?.forEach { f -> if (f.name.substringBefore('_').toLongOrNull() !in keep) f.delete() }
    }

    /**
     * İlk 1–2 kısa cümle (≤160 harf, birkaç saniyede hazır) sonra ~600 harflik parçalar:
     * her parça ~40 sn ses, bellek dostu; ücretsiz hak için toplam 3–5 istek.
     */
    private fun splitChunks(text: String): List<String> {
        val sentences = text.trim().split(Regex("(?<=[.!?…])\\s+")).filter { it.isNotBlank() }
        if (sentences.isEmpty()) return listOf(text)
        val out = mutableListOf<String>()
        var cur = ""
        for (sentence in sentences) {
            val limit = if (out.isEmpty()) 160 else 600
            if (cur.isNotEmpty() && cur.length + sentence.length + 1 > limit) {
                out.add(cur)
                cur = sentence
            } else {
                cur = if (cur.isEmpty()) sentence else "$cur $sentence"
            }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }
}

/** Telefonun konuşma motoru; varsa Google'ınki ve onun en kaliteli Türkçe sesi. */
private class DeviceVoice(context: Context, private val onSpeaking: (Boolean) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val tr: Locale = Locale.forLanguageTag("tr-TR")
    private var ready = false
    private var pending: String? = null
    private var waiter: (() -> Unit)? = null
    private lateinit var tts: TextToSpeech

    init {
        val google = "com.google.android.tts"
        val hasGoogle = runCatching { context.packageManager.getPackageInfo(google, 0) }.isSuccess
        val onInit = TextToSpeech.OnInitListener { status -> main.post { if (status == TextToSpeech.SUCCESS) setup() } }
        tts = if (hasGoogle) TextToSpeech(context, onInit, google) else TextToSpeech(context, onInit)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { main.post { onSpeaking(true) } }
            override fun onDone(utteranceId: String?) { if (utteranceId == LAST) main.post { finished() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { finished() } }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { main.post { finished() } }
        })
    }

    private fun finished() {
        onSpeaking(false)
        waiter?.invoke()
        waiter = null
    }

    private fun setup() {
        tts.language = tr
        // En yüksek kaliteli Türkçe ses; eşitse internetli (daha doğal) olan.
        val best: Voice? = runCatching {
            tts.voices.filter { it.locale.language == "tr" && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
                .maxWithOrNull(compareBy<Voice>({ it.quality }, { it.isNetworkConnectionRequired }))
        }.getOrNull()
        best?.let { runCatching { tts.voice = it } }
        tts.setSpeechRate(0.92f)
        tts.setPitch(1.0f)
        ready = true
        pending?.let { speak(it) }
        pending = null
    }

    fun speak(text: String) {
        if (!ready) { pending = text; return }
        // Yalnızca gerçekten konuşuyorsa durdur: durdurma, bekleyen "bitti" haberini erken tetikliyordu.
        if (tts.isSpeaking) tts.stop()
        val chunks = text.split(Regex("(?<=[.!?…])\\s+")).fold(mutableListOf<String>()) { acc, s ->
            if (acc.isNotEmpty() && acc.last().length + s.length < 400) acc[acc.lastIndex] = acc.last() + " " + s else acc.add(s)
            acc
        }.filter { it.isNotBlank() }
        chunks.forEachIndexed { i, c ->
            val id = if (i == chunks.lastIndex) LAST else "part$i"
            tts.speak(c, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle(), id)
        }
    }

    suspend fun speakAndWait(text: String) = suspendCancellableCoroutine<Unit> { cont ->
        main.post {
            waiter = { if (cont.isActive) cont.resume(Unit) }
            speak(text)
        }
        cont.invokeOnCancellation { main.post { stop() } }
    }

    fun stop() {
        pending = null
        waiter = null
        if (ready) tts.stop()
        onSpeaking(false)
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private companion object { const val LAST = "last" }
}

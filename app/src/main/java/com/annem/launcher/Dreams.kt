package com.annem.launcher

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Ana ekran kutularına atanabilen, Annem'in kendi içindeki ekranlar. */
object InternalApps {
    const val DREAMS = "annem.internal.dreams"
    const val DREAMS_LABEL = "Rüya Tabirleri"

    fun isInternal(packageName: String?) = packageName == DREAMS
}

/** Bir rüya ve yorumu (geçmişte saklanır). */
data class DreamEntry(val time: Long, val dream: String, val reading: String, val by: String = "")

/** Yorum sonucu: ya metin ya da anneye gösterilecek sade bir hata. */
sealed interface DreamResult {
    data class Ok(val text: String, val by: String = "") : DreamResult
    data class Error(
        val message: String,
        val retryable: Boolean,
        val detail: String = "",
        /** Kota doldu: aynı modeli yeniden denemek hakkı boşa harcar, sıradakine geç. */
        val quota: Boolean = false,
    ) : DreamResult
}

/**
 * Rüyayı Google Gemini ile yorumlar (ücretsiz katman; anahtar aistudio.google.com'dan alınır).
 * Rüya metni yalnızca yorum için Google'a gönderilir; telefonda son 10 rüya saklanır.
 */
object DreamInterpreter {

    // "-latest" takma adları: Google model sürümlerini değiştirdikçe uygulama bozulmasın.
    private val ALIASES = listOf("gemini-flash-latest", "gemini-flash-lite-latest")

    /** Bu istekte denenenlerin kısa özeti (hata olursa küçük yazıyla gösterilir). */
    private val trace = java.util.Collections.synchronizedList(mutableListOf<String>())
    fun note(s: String) { trace.add(s) }

    @Volatile private var discovered: List<String> = emptyList()
    @Volatile private var discoveredAt = 0L

    /**
     * Anahtarın erişebildiği Gemini metin modellerini Google'dan öğrenir (günde bir kez):
     * Google model adlarını değiştirse ya da biri yoğun olsa da çalışan bir model bulunur.
     */
    private fun geminiModels(apiKey: String): List<String> {
        val now = System.currentTimeMillis()
        if (discovered.isEmpty() || now - discoveredAt > 24 * 3600_000L) {
            runCatching {
                val conn = URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200").openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 15_000
                    conn.setRequestProperty("x-goog-api-key", apiKey)
                    if (conn.responseCode in 200..299) {
                        val arr = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONArray("models") ?: JSONArray()
                        val names = (0 until arr.length()).mapNotNull { i ->
                            val m = arr.optJSONObject(i) ?: return@mapNotNull null
                            val methods = m.optJSONArray("supportedGenerationMethods")?.toString().orEmpty()
                            val name = m.optString("name").removePrefix("models/")
                            val bad = listOf("tts", "image", "live", "audio", "embedding", "native", "robotics", "computer", "thinking-exp")
                            if ("generateContent" in methods && "flash" in name && bad.none { it in name }) name else null
                        }
                        // Önce tam (lite olmayan), yeni sürüm önce; sonra lite.
                        val full = names.filter { "lite" !in it }.sortedDescending()
                        val lite = names.filter { "lite" in it }.sortedDescending()
                        discovered = (full.take(2) + lite.take(1))
                        discoveredAt = now
                    }
                } finally { conn.disconnect() }
            }
        }
        return (listOf(ALIASES[0]) + discovered.filter { "lite" !in it } + ALIASES[1] + discovered.filter { "lite" in it })
            .distinct().take(4)
    }

    const val PROMPT = """Sen Türk rüya tabiri geleneğini çok iyi bilen, sıcak ve sevecen, tecrübeli bir rüya yorumcususun. İbn Sîrîn, Nablusî ve Kirmânî gibi klasik tabir kitaplarındaki ve Anadolu halk inancındaki yaygın anlamları bilirsin.
Rüyayı anlatan kişi yaşlı bir anne; rüyasını sesle anlattı, metinde konuşma hataları olabilir, anlamı tahmin et.

NASIL YORUMLAYACAKSIN:
1. Rüyadaki bütün önemli öğeleri ayırt et: kişiler (yaşayan, rahmetli, çocuklar), yerler, nesneler, hayvanlar, renkler, sayılar, yapılan işler ve rüya sahibinin hissettikleri.
2. Her öğeyi ayrı ayrı yorumla ve rüyadaki AYRINTISINA göre anlamını incelt. Örneğin berrak su ile bulanık su, ısıran yılan ile zarar vermeden giden yılan, birine bir şey vermek ile ondan bir şey almak farklı yorumlanır. Rüya sahibinin kullandığı ayrıntıları kendi sözleriyle an.
3. Gelenekte bir öğenin birden fazla yorumu varsa en uygun olanı seç; gerekirse "bazı tabirciler de şöyle der" diyerek ikinci anlamı kısaca ekle.
4. Sonra öğeleri birbirine bağla: rüyanın sırası ve bütünü ne anlatıyor? Rüya sahibinin hayatına, ailesine, evlatlarına, sağlığına ve gönlüne dair nasıl bir müjde ya da öğüt taşıyor?
5. Rahmetli yakınlar görüldüyse onları sevgiyle an; geleneğe uygun olarak dua, sadaka ya da hayır yapma tavsiyesini nazikçe ekleyebilirsin.
6. En sonda rüyanın ana mesajını bir iki cümleyle özetle ve içten bir iyi dilekle bitir.

ÜSLUP:
- "Hayırdır inşallah" diye başla. Rüya sahibine "siz" diye hitap et.
- Yaşlı birinin rahat anlayacağı sade ve sıcak bir Türkçe kullan; cümleler kısa olsun.
- 250-400 kelime yaz; kısa paragraflar halinde, akıcı bir konuşma gibi.
- "Rüyanız güzel", "olumlu enerjiler" gibi her rüyaya uyan genel laflar yazma; her cümle BU rüyaya ait olsun.
- Her zaman umut verici ve yumuşak ol. Ölüm ya da hastalık kehaneti, korkutucu tahmin, tıbbi veya parasal tavsiye verme. Kötü görünen öğelerin gelenekte hayra yorulan yanını anlat.
- Başlık, madde işareti, numara, yıldız, emoji ya da başka biçimlendirme kullanma: metin sesli okunacak.
- Metin bir rüya değilse ya da çok kısa ve anlaşılmazsa, nazikçe rüyayı biraz daha ayrıntılı anlatmasını iste.

ÖRNEK (yalnızca derinlik ve üslup için; kelimesi kelimesine kopyalama):
Rüya: "Bahçede bir yılan gördüm ama bana bir şey yapmadı, gitti."
Yorum: "Hayırdır inşallah. Rüyada yılan görmek tabir kitaplarında çoğu zaman bir düşmana ya da içinizi kemiren bir kaygıya işaret eder. Ama sizin rüyanızda yılan size dokunmadan uzaklaşmış; bu, o sıkıntının size ulaşamadan kendiliğinden çekip gideceğine yorulur. Bahçe ise evinizi ve ailenizi temsil eder..." """

    /**
     * Claude anahtarı varsa önce Claude, yoksa Gemini. Biri yanıt vermezse birkaç saniye
     * bekleyip yeniden dener, yine olmazsa diğer servise geçer: anne hatayı nadiren görür.
     */
    suspend fun interpret(
        geminiKey: String,
        claudeKey: String,
        groqKey: String,
        dream: String,
        onPartial: (String) -> Unit = {},
    ): DreamResult = withContext(Dispatchers.IO) {
        // Sıra: Claude (ücretli, en iyi) → Groq (ücretsiz, çok hızlı) → Gemini (ücretsiz).
        val services = buildList<suspend () -> DreamResult> {
            if (claudeKey.isNotBlank()) add { ClaudeDreams.interpret(claudeKey.trim(), PROMPT, dream, onPartial) }
            if (groqKey.isNotBlank()) add { GroqDreams.interpret(groqKey.trim(), PROMPT, dream) }
            if (geminiKey.isNotBlank()) add { gemini(geminiKey.trim(), dream, onPartial) }
        }
        if (services.isEmpty()) return@withContext DreamResult.Error("Rüya yorumu için Ayarlar'da anahtar girilmeli.", false)
        trace.clear()
        var last: DreamResult = DreamResult.Error("Yorum alınamadı. Biraz sonra tekrar deneyin.", true)
        for (service in services) {
            // Her servis için: hemen, sonra 4 sn bekleyip bir kez daha.
            for (wait in listOf(0L, 4_000L)) {
                if (wait > 0) delay(wait)
                onPartial("")
                last = service()
                if (last is DreamResult.Ok) return@withContext last
                val err = last as DreamResult.Error
                if (!err.retryable || err.quota) break // anahtar/kredi/kota sorunu: diğer servise geç
            }
        }
        (last as? DreamResult.Error)?.copy(detail = trace.joinToString(" · ")) ?: last
    }

    private fun gemini(apiKey: String, dream: String, onPartial: (String) -> Unit): DreamResult {
        var last: DreamResult = DreamResult.Error("Yorum alınamadı. Biraz sonra tekrar deneyin.", true)
        for (model in geminiModels(apiKey)) {
            onPartial("")
            last = call(apiKey, model, dream, onPartial)
            if (last is DreamResult.Ok) return (last as DreamResult.Ok).copy(by = "Gemini")
            if (last is DreamResult.Error && !(last as DreamResult.Error).retryable) return last
        }
        return last
    }

    /**
     * Gemini'den yorumu tek parça alır. (Akışlı yanıt ücretsiz katmanda yorumu yarıda
     * bırakıyordu; tek parça yanıt güvenilir.) Düzgün bitmeyen yorum yeniden denenir.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun call(apiKey: String, model: String, dream: String, onPartial: (String) -> Unit): DreamResult {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", PROMPT))))
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", "Rüyam:\n$dream")))
                ),
            )
            .put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 8192))
        val conn = try {
            (URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection)
        } catch (e: IOException) {
            return DreamResult.Error("İnternet bağlantısı yok gibi görünüyor.", true)
        }
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) note("$model: $code")
            when {
                code in 200..299 -> parse(text).also { if (it is DreamResult.Error) note("$model: yarım") }
                code == 400 && text.contains("API_KEY", ignoreCase = true) ->
                    DreamResult.Error("Ayarlar'daki rüya anahtarı geçersiz. Yakınınız kontrol etmeli.", false)
                code == 401 || code == 403 ->
                    DreamResult.Error("Ayarlar'daki rüya anahtarı kabul edilmedi. Yakınınız kontrol etmeli.", false)
                code == 429 -> DreamResult.Error("Bugünlük ücretsiz yorum hakkı doldu. Yarın tekrar deneyin.", true, quota = true)
                else -> DreamResult.Error("Yorum servisi şu an yanıt vermiyor. Biraz sonra tekrar deneyin.", true)
            }
        } catch (e: IOException) {
            note("$model: bağlantı")
            DreamResult.Error("İnternet bağlantısı yok gibi görünüyor.", true)
        } catch (e: Exception) {
            note("$model: hata")
            DreamResult.Error("Yorum alınamadı. Biraz sonra tekrar deneyin.", true)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(json: String): DreamResult {
        val cand = JSONObject(json).optJSONArray("candidates")?.optJSONObject(0)
            ?: return DreamResult.Error("Bu rüya için yorum yapılamadı. Biraz farklı anlatmayı deneyin.", false)
        val finishReason = cand.optString("finishReason", "")
        val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.optBoolean("thought", false)) continue
            sb.append(part.optString("text"))
        }
        return when {
            finishReason == "STOP" || finishReason.isEmpty() -> finish(sb)
            finishReason == "MAX_TOKENS" && sb.length > 600 -> finish(sb)
            else -> DreamResult.Error("Yorum yarım kaldı. Tekrar deneyin.", true)
        }
    }

    private fun finish(sb: StringBuilder): DreamResult {
        val clean = cleanText(sb.toString())
        return if (clean.isEmpty()) {
            DreamResult.Error("Bu rüya için yorum yapılamadı. Biraz farklı anlatmayı deneyin.", false)
        } else {
            DreamResult.Ok(clean)
        }
    }

    fun cleanText(s: String): String = s
        .replace(Regex("[*#_`>]"), "")
        .replace(Regex("[ \t]+"), " ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

/** Ayarlar'daki Gemini anahtarı ve son 10 rüya. */
class DreamStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("annem_dreams", Context.MODE_PRIVATE)

    val apiKey = mutableStateOf(prefs.getString("gemini_key", "").orEmpty())
    val claudeKey = mutableStateOf(prefs.getString("claude_key", "").orEmpty())

    fun updateClaudeKey(key: String) {
        claudeKey.value = key.trim()
        prefs.edit().putString("claude_key", key.trim()).apply()
    }

    /** Herhangi bir yorum servisi kurulu mu? */
    val groqKey = mutableStateOf(prefs.getString("groq_key", "").orEmpty())

    fun updateGroqKey(key: String) {
        groqKey.value = key.trim()
        prefs.edit().putString("groq_key", key.trim()).apply()
    }

    val ready: Boolean get() = apiKey.value.isNotBlank() || claudeKey.value.isNotBlank() || groqKey.value.isNotBlank()
    val history = mutableStateListOf<DreamEntry>().apply { addAll(loadHistory()) }

    /** Yorumu Gemini'nin doğal sesiyle oku (kapalıysa telefonun sesi). */
    val naturalVoice = mutableStateOf(prefs.getBoolean("natural_voice", true))

    fun updateNaturalVoice(on: Boolean) {
        naturalVoice.value = on
        prefs.edit().putBoolean("natural_voice", on).apply()
    }

    fun updateApiKey(key: String) {
        apiKey.value = key.trim()
        prefs.edit().putString("gemini_key", key.trim()).apply()
    }

    fun add(entry: DreamEntry) {
        history.add(0, entry)
        while (history.size > 10) history.removeAt(history.lastIndex)
        save()
    }

    /** Rüyayı ve saklanan sesini siler. */
    fun remove(entry: DreamEntry) {
        history.remove(entry)
        save()
        audioDir().listFiles()?.filter { it.name.startsWith("${entry.time}_") }?.forEach { it.delete() }
    }

    /** Bütün rüyaları ve sesleri siler. */
    fun clearAll() {
        history.clear()
        save()
        audioDir().listFiles()?.forEach { it.delete() }
    }

    private fun audioDir() = java.io.File(app.filesDir, "dream_audio_v2")

    private fun save() {
        val arr = JSONArray()
        history.forEach { arr.put(JSONObject().put("t", it.time).put("d", it.dream).put("y", it.reading).put("b", it.by)) }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory(): List<DreamEntry> = try {
        val arr = JSONArray(prefs.getString("history", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { DreamEntry(it.optLong("t"), it.optString("d"), it.optString("y"), it.optString("b")) }
        }
    } catch (e: Exception) {
        emptyList()
    }
}


/** Anthropic Claude ile yorum (Ayarlar'da Claude anahtarı girildiyse). */
object ClaudeDreams {
    private const val MODEL = "claude-sonnet-5-5"

    fun interpret(apiKey: String, prompt: String, dream: String, onPartial: (String) -> Unit = {}): DreamResult {
        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 2048)
            .put("temperature", 0.7)
            .put("stream", true)
            .put("system", prompt)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Rüyam:\n$dream")))
        val conn = try {
            URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return DreamResult.Error("İnternet bağlantısı yok gibi görünüyor.", true)
        }
        val sb = StringBuilder()
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 40_000 // iki parça arası en fazla
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val text = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                DreamInterpreter.note("Claude: $code" + if (text.contains("credit", true)) " kredi yok" else "")
                return when {
                    code == 401 || code == 403 ->
                        DreamResult.Error("Ayarlar'daki Claude anahtarı kabul edilmedi. Yakınınız kontrol etmeli.", false)
                    code == 400 && text.contains("credit", ignoreCase = true) ->
                        DreamResult.Error("Claude kredisi yok. Yakınınız kredi eklemeli.", false)
                    code == 400 || code == 404 -> DreamResult.Error("Claude isteği kabul etmedi.", false)
                    else -> DreamResult.Error("Yorum servisi şu an yanıt vermiyor. Biraz sonra tekrar deneyin.", true)
                }
            }
            var streamError: DreamResult.Error? = null
            var stopReason = ""
            conn.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("data:")) continue
                    val obj = runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrNull() ?: continue
                    when (obj.optString("type")) {
                        "content_block_delta" -> {
                            val d = obj.optJSONObject("delta") ?: continue
                            if (d.optString("type") == "text_delta") {
                                sb.append(d.optString("text"))
                                onPartial(DreamInterpreter.cleanText(sb.toString()))
                            }
                        }
                        "message_delta" -> {
                            obj.optJSONObject("delta")?.optString("stop_reason", "")
                                ?.takeIf { it.isNotEmpty() && it != "null" }?.let { stopReason = it }
                        }
                        "error" -> {
                            streamError = DreamResult.Error("Yorum servisi şu an yanıt vermiyor. Biraz sonra tekrar deneyin.", true)
                            break
                        }
                        "message_stop" -> break
                    }
                }
            }
            val clean = DreamInterpreter.cleanText(sb.toString())
            when {
                streamError != null && clean.length < 600 -> streamError!!
                clean.isEmpty() -> DreamResult.Error("Bu rüya için yorum yapılamadı.", false)
                stopReason == "end_turn" || stopReason == "stop_sequence" -> DreamResult.Ok(clean, by = "Claude")
                stopReason == "max_tokens" && clean.length > 600 -> DreamResult.Ok(clean, by = "Claude")
                else -> DreamResult.Error("Yorum yarım kaldı. Tekrar deneyin.", true)
            }
        } catch (e: IOException) {
            val clean = DreamInterpreter.cleanText(sb.toString())
            if (clean.length > 600) DreamResult.Ok(clean, by = "Claude")
            else DreamResult.Error("İnternet bağlantısı yok gibi görünüyor.", true)
        } catch (e: Exception) {
            DreamResult.Error("Yorum alınamadı. Biraz sonra tekrar deneyin.", true)
        } finally {
            conn.disconnect()
        }
    }
}


/**
 * Groq ile yorum (ücretsiz, kredi kartı istemez, çok hızlı). Açık modeller çalıştırır;
 * anahtarın erişebildiği modeller günde bir kez öğrenilir, en uygun olanı seçilir.
 */
object GroqDreams {
    private const val BASE = "https://api.groq.com/openai/v1"

    // Tercih sırası: yazım kalitesi yüksek, çok dilli büyük modeller önce.
    private val PREFERRED = listOf("kimi-k2", "gpt-oss-120b", "qwen", "llama-3.3-70b", "llama-4")

    @Volatile private var models: List<String> = emptyList()
    @Volatile private var modelsAt = 0L

    private fun pickModels(apiKey: String): List<String> {
        val now = System.currentTimeMillis()
        if (models.isEmpty() || now - modelsAt > 24 * 3600_000L) {
            runCatching {
                val conn = URL("$BASE/models").openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 15_000
                    conn.setRequestProperty("Authorization", "Bearer $apiKey")
                    if (conn.responseCode in 200..299) {
                        val arr = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONArray("data") ?: JSONArray()
                        val ids = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id") }
                            .filter { id ->
                                listOf("whisper", "tts", "guard", "playai", "orpheus", "compound", "allam", "embed").none { it in id }
                            }
                        val ranked = PREFERRED.flatMap { p -> ids.filter { p in it }.sortedDescending() }.distinct()
                        models = ranked.take(3)
                        modelsAt = now
                    }
                } finally { conn.disconnect() }
            }
        }
        return models.ifEmpty { listOf("openai/gpt-oss-120b", "llama-3.3-70b-versatile") }
    }

    fun interpret(apiKey: String, prompt: String, dream: String): DreamResult {
        var last: DreamResult = DreamResult.Error("Yorum servisi şu an yanıt vermiyor. Biraz sonra tekrar deneyin.", true)
        for (model in pickModels(apiKey)) {
            last = call(apiKey, model, prompt, dream)
            if (last is DreamResult.Ok) return last
            if (last is DreamResult.Error && !(last as DreamResult.Error).retryable && !(last as DreamResult.Error).quota) return last
        }
        return last
    }

    private fun call(apiKey: String, model: String, prompt: String, dream: String): DreamResult {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.7)
            .put("max_completion_tokens", 4096)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", prompt))
                    .put(JSONObject().put("role", "user").put("content", "Rüyam:\n$dream")),
            )
        // Düşünen modellerde düşünceyi kısa tut ve metne karıştırma.
        if ("gpt-oss" in model) body.put("reasoning_effort", "low")
        if ("qwen" in model) body.put("reasoning_format", "hidden")
        val conn = try {
            URL("$BASE/chat/completions").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return DreamResult.Error("İnternet bağlantısı yok gibi görünüyor.", true)
        }
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) DreamInterpreter.note("Groq $model: $code")
            when {
                code in 200..299 -> {
                    val choice = JSONObject(text).optJSONArray("choices")?.optJSONObject(0)
                    val finish = choice?.optString("finish_reason").orEmpty()
                    val content = choice?.optJSONObject("message")?.optString("content").orEmpty()
                        .replace(Regex("(?s)<think>.*?</think>"), "")
                    val clean = DreamInterpreter.cleanText(content)
                    when {
                        clean.isEmpty() -> { DreamInterpreter.note("Groq $model: boş"); DreamResult.Error("Yorum alınamadı.", true) }
                        finish == "stop" || (finish == "length" && clean.length > 600) -> DreamResult.Ok(clean, by = "Groq")
                        else -> { DreamInterpreter.note("Groq $model: yarım"); DreamResult.Error("Yorum yarım kaldı. Tekrar deneyin.", true) }
                    }
                }
                code == 401 || code == 403 ->
                    DreamResult.Error("Ayarlar'daki Groq anahtarı kabul edilmedi. Yakınınız kontrol etmeli.", false)
                code == 429 -> DreamResult.Error("Groq ücretsiz hakkı şimdilik doldu.", true, quota = true)
                code == 400 || code == 404 -> DreamResult.Error("Groq bu modeli kabul etmedi.", true, quota = true)
                else -> DreamResult.Error("Yorum servisi şu an yanıt vermiyor. Biraz sonra tekrar deneyin.", true)
            }
        } catch (e: IOException) {
            DreamInterpreter.note("Groq $model: bağlantı")
            DreamResult.Error("İnternet bağlantısı yok gibi görünüyor.", true)
        } catch (e: Exception) {
            DreamResult.Error("Yorum alınamadı. Biraz sonra tekrar deneyin.", true)
        } finally {
            conn.disconnect()
        }
    }
}

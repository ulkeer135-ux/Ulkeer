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
    data class Error(val message: String, val retryable: Boolean) : DreamResult
}

/**
 * Rüyayı Google Gemini ile yorumlar (ücretsiz katman; anahtar aistudio.google.com'dan alınır).
 * Rüya metni yalnızca yorum için Google'a gönderilir; telefonda son 10 rüya saklanır.
 */
object DreamInterpreter {

    // "-latest" takma adları: Google model sürümlerini değiştirdikçe uygulama bozulmasın.
    private val MODELS = listOf("gemini-flash-latest", "gemini-flash-lite-latest")

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
        dream: String,
        onPartial: (String) -> Unit = {},
    ): DreamResult = withContext(Dispatchers.IO) {
        val services = buildList<suspend () -> DreamResult> {
            if (claudeKey.isNotBlank()) add { ClaudeDreams.interpret(claudeKey.trim(), PROMPT, dream, onPartial) }
            if (geminiKey.isNotBlank()) add { gemini(geminiKey.trim(), dream, onPartial) }
        }
        if (services.isEmpty()) return@withContext DreamResult.Error("Rüya yorumu için Ayarlar'da anahtar girilmeli.", false)
        var last: DreamResult = DreamResult.Error("Yorum alınamadı. Biraz sonra tekrar deneyin.", true)
        for (service in services) {
            // Her servis için: hemen, 3 sn sonra, 8 sn sonra.
            for (wait in listOf(0L, 3_000L, 8_000L)) {
                if (wait > 0) delay(wait)
                onPartial("")
                last = service()
                if (last is DreamResult.Ok) return@withContext last
                if (!(last as DreamResult.Error).retryable) break // anahtar/kredi sorunu: diğer servise geç
            }
        }
        last
    }

    private fun gemini(apiKey: String, dream: String, onPartial: (String) -> Unit): DreamResult {
        var last: DreamResult = DreamResult.Error("Yorum alınamadı. Biraz sonra tekrar deneyin.", true)
        for (model in MODELS) {
            onPartial("")
            last = call(apiKey, model, dream, onPartial)
            if (last is DreamResult.Ok) return (last as DreamResult.Ok).copy(by = "Gemini")
            if (last is DreamResult.Error && !(last as DreamResult.Error).retryable) return last
        }
        return last
    }

    /** Gemini'den yorumu akış hâlinde alır: yazıldıkça ekrana gelir. */
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
            (URL("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse").openConnection() as HttpURLConnection)
        } catch (e: IOException) {
            return DreamResult.Error("İnternet bağlantısı yok gibi görünüyor.", true)
        }
        val sb = StringBuilder()
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 40_000 // iki parça arası en fazla
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val text = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                return when {
                    code == 400 && text.contains("API_KEY", ignoreCase = true) ->
                        DreamResult.Error("Ayarlar'daki rüya anahtarı geçersiz. Yakınınız kontrol etmeli.", false)
                    code == 401 || code == 403 ->
                        DreamResult.Error("Ayarlar'daki rüya anahtarı kabul edilmedi. Yakınınız kontrol etmeli.", false)
                    code == 429 -> DreamResult.Error("Bugünlük ücretsiz yorum hakkı doldu. Yarın tekrar deneyin.", true)
                    else -> DreamResult.Error("Yorum servisi şu an yanıt vermiyor. Biraz sonra tekrar deneyin.", true)
                }
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("data:")) return@forEach
                    val obj = runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrNull() ?: return@forEach
                    val parts = obj.optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts") ?: return@forEach
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        if (part.optBoolean("thought", false)) continue
                        sb.append(part.optString("text"))
                    }
                    onPartial(cleanText(sb.toString()))
                }
            }
            finish(sb)
        } catch (e: IOException) {
            // Yarıda koptuysa ve yorumun çoğu geldiyse onu kullan.
            if (sb.length > 600) finish(sb) else DreamResult.Error("İnternet bağlantısı yok gibi görünüyor.", true)
        } catch (e: Exception) {
            DreamResult.Error("Yorum alınamadı. Biraz sonra tekrar deneyin.", true)
        } finally {
            conn.disconnect()
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
    val ready: Boolean get() = apiKey.value.isNotBlank() || claudeKey.value.isNotBlank()
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
                else -> DreamResult.Ok(clean, by = "Claude")
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

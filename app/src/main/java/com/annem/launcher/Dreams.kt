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
    suspend fun interpret(geminiKey: String, claudeKey: String, dream: String): DreamResult = withContext(Dispatchers.IO) {
        val services = buildList<suspend () -> DreamResult> {
            if (claudeKey.isNotBlank()) add { ClaudeDreams.interpret(claudeKey.trim(), PROMPT, dream) }
            if (geminiKey.isNotBlank()) add { gemini(geminiKey.trim(), dream) }
        }
        if (services.isEmpty()) return@withContext DreamResult.Error("Rüya yorumu için Ayarlar'da anahtar girilmeli.", false)
        var last: DreamResult = DreamResult.Error("Yorum alınamadı. Biraz sonra tekrar deneyin.", true)
        for (service in services) {
            // Her servis için: hemen, 3 sn sonra, 8 sn sonra.
            for (wait in listOf(0L, 3_000L, 8_000L)) {
                if (wait > 0) delay(wait)
                last = service()
                if (last is DreamResult.Ok) return@withContext last
                if (!(last as DreamResult.Error).retryable) break // anahtar/kredi sorunu: diğer servise geç
            }
        }
        last
    }

    private fun gemini(apiKey: String, dream: String): DreamResult {
        var last: DreamResult = DreamResult.Error("Yorum alınamadı. Biraz sonra tekrar deneyin.", true)
        for (model in MODELS) {
            last = call(apiKey, model, dream)
            if (last is DreamResult.Ok) return (last as DreamResult.Ok).copy(by = "Gemini")
            if (last is DreamResult.Error && !(last as DreamResult.Error).retryable) return last
        }
        return last
    }

    private fun call(apiKey: String, model: String, dream: String): DreamResult {
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
            when {
                code in 200..299 -> parse(text)
                code == 400 && text.contains("API_KEY", ignoreCase = true) ->
                    DreamResult.Error("Ayarlar'daki rüya anahtarı geçersiz. Yakınınız kontrol etmeli.", false)
                code == 401 || code == 403 ->
                    DreamResult.Error("Ayarlar'daki rüya anahtarı kabul edilmedi. Yakınınız kontrol etmeli.", false)
                code == 429 -> DreamResult.Error("Bugünlük ücretsiz yorum hakkı doldu. Yarın tekrar deneyin.", true)
                code == 404 -> DreamResult.Error("Yorum servisi şu an yanıt vermiyor. Biraz sonra tekrar deneyin.", true)
                else -> DreamResult.Error("Yorum servisi şu an yanıt vermiyor. Biraz sonra tekrar deneyin.", true)
            }
        } catch (e: IOException) {
            DreamResult.Error("İnternet bağlantısı yok gibi görünüyor.", true)
        } catch (e: Exception) {
            DreamResult.Error("Yorum alınamadı. Biraz sonra tekrar deneyin.", true)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(json: String): DreamResult {
        val root = JSONObject(json)
        val parts = root.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
            ?: return DreamResult.Error("Bu rüya için yorum yapılamadı. Biraz farklı anlatmayı deneyin.", false)
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.optJSONObject(i) ?: continue
            if (p.optBoolean("thought", false)) continue
            sb.append(p.optString("text"))
        }
        val clean = sb.toString()
            .replace(Regex("[*#_`>]"), "")
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        return if (clean.isEmpty()) {
            DreamResult.Error("Bu rüya için yorum yapılamadı. Biraz farklı anlatmayı deneyin.", false)
        } else {
            DreamResult.Ok(clean)
        }
    }
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

    fun interpret(apiKey: String, prompt: String, dream: String): DreamResult {
        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 2048)
            .put("temperature", 0.7)
            .put("system", prompt)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Rüyam:\n$dream")))
        val conn = try {
            URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return DreamResult.Error("İnternet bağlantısı yok gibi görünüyor.", true)
        }
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            when {
                code in 200..299 -> {
                    val content = JSONObject(text).optJSONArray("content") ?: JSONArray()
                    val sb = StringBuilder()
                    for (i in 0 until content.length()) {
                        val c = content.optJSONObject(i) ?: continue
                        if (c.optString("type") == "text") sb.append(c.optString("text"))
                    }
                    val clean = sb.toString().replace(Regex("[*#_`>]"), "").replace(Regex("\n{3,}"), "\n\n").trim()
                    if (clean.isEmpty()) DreamResult.Error("Bu rüya için yorum yapılamadı.", false)
                    else DreamResult.Ok(clean, by = "Claude")
                }
                code == 401 || code == 403 ->
                    DreamResult.Error("Ayarlar'daki Claude anahtarı kabul edilmedi. Yakınınız kontrol etmeli.", false)
                code == 400 && text.contains("credit", ignoreCase = true) ->
                    DreamResult.Error("Claude kredisi bitti. Yakınınız kredi eklemeli.", false)
                code == 404 -> DreamResult.Error("Claude modeli bulunamadı.", false)
                else -> DreamResult.Error("Yorum servisi şu an yanıt vermiyor. Biraz sonra tekrar deneyin.", true) // 429, 5xx, 529
            }
        } catch (e: IOException) {
            DreamResult.Error("İnternet bağlantısı yok gibi görünüyor.", true)
        } catch (e: Exception) {
            DreamResult.Error("Yorum alınamadı. Biraz sonra tekrar deneyin.", true)
        } finally {
            conn.disconnect()
        }
    }
}

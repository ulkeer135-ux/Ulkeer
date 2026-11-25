package com.annem.launcher

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
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
data class DreamEntry(val time: Long, val dream: String, val reading: String)

/** Yorum sonucu: ya metin ya da anneye gösterilecek sade bir hata. */
sealed interface DreamResult {
    data class Ok(val text: String) : DreamResult
    data class Error(val message: String, val retryable: Boolean) : DreamResult
}

/**
 * Rüyayı Google Gemini ile yorumlar (ücretsiz katman; anahtar aistudio.google.com'dan alınır).
 * Rüya metni yalnızca yorum için Google'a gönderilir; telefonda son 10 rüya saklanır.
 */
object DreamInterpreter {

    // "-latest" takma adları: Google model sürümlerini değiştirdikçe uygulama bozulmasın.
    private val MODELS = listOf("gemini-flash-latest", "gemini-flash-lite-latest")

    private const val PROMPT = """Sen sıcak, sevecen ve tecrübeli bir rüya yorumcususun. Türk kültüründeki geleneksel rüya tabirlerini (halk inancı ve klasik tabir kitaplarındaki yaygın anlamları) bilen, güven veren bir büyük gibi konuşursun.
Rüyayı anlatan kişi yaşlı bir anne; rüyasını sesle anlattı, metinde konuşma hataları olabilir.
Kurallar:
- Türkçe, sade ve kısa cümlelerle yaz; "siz" diye hitap et. "Hayırdır inşallah" gibi geleneksel bir açılış kullanabilirsin.
- Önce rüyadaki en önemli 2-4 öğenin geleneksel anlamını söyle, sonra rüyanın bütününü yorumla.
- Toplam 120-180 kelime olsun.
- Her zaman umut verici ve yumuşak ol. Korkutucu kehanet, ölüm ya da hastalık tahmini, tıbbi veya parasal tavsiye verme. Kötü görünen öğelerin geleneksel olarak hayra yorulan yanını anlat.
- Sonunda kısa ve içten bir iyi dilekle bitir.
- Başlık, madde işareti, yıldız, emoji ya da başka biçimlendirme kullanma: metin sesli okunacak.
- Metin bir rüya değilse ya da anlaşılmıyorsa, nazikçe rüyayı yeniden anlatmasını iste."""

    suspend fun interpret(apiKey: String, dream: String): DreamResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext DreamResult.Error("Rüya yorumu için Ayarlar'da anahtar girilmeli.", false)
        var last: DreamResult = DreamResult.Error("Yorum alınamadı. Biraz sonra tekrar deneyin.", true)
        for (model in MODELS) {
            last = call(apiKey.trim(), model, dream)
            // Model bulunamadı ya da yoğunluk varsa bir sonraki (hafif) modeli dene.
            if (last is DreamResult.Ok) return@withContext last
            if (last is DreamResult.Error && !(last as DreamResult.Error).retryable) return@withContext last
        }
        last
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
            .put("generationConfig", JSONObject().put("temperature", 0.8).put("maxOutputTokens", 4096))
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
    private val prefs = context.applicationContext.getSharedPreferences("annem_dreams", Context.MODE_PRIVATE)

    val apiKey = mutableStateOf(prefs.getString("gemini_key", "").orEmpty())
    val history = mutableStateListOf<DreamEntry>().apply { addAll(loadHistory()) }

    fun updateApiKey(key: String) {
        apiKey.value = key.trim()
        prefs.edit().putString("gemini_key", key.trim()).apply()
    }

    fun add(entry: DreamEntry) {
        history.add(0, entry)
        while (history.size > 10) history.removeAt(history.lastIndex)
        save()
    }

    fun remove(entry: DreamEntry) {
        history.remove(entry)
        save()
    }

    private fun save() {
        val arr = JSONArray()
        history.forEach { arr.put(JSONObject().put("t", it.time).put("d", it.dream).put("y", it.reading)) }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory(): List<DreamEntry> = try {
        val arr = JSONArray(prefs.getString("history", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { DreamEntry(it.optLong("t"), it.optString("d"), it.optString("y")) }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

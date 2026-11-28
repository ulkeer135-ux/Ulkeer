package com.annem.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Robota Sor": anne sesle soru sorar, cevap ekranda yazar ve sesli okunur.
 * Önce Groq Compound Mini (internette arar, güncel bilgi); olmazsa (hak bitti, ücretli vb.)
 * Rüya Tabirleri'nin kullandığı normal Groq modeliyle, kendi bilgisinden cevap verir.
 * Anahtar: Rüya Tabirleri'nin Groq anahtarı (ayrıca bir şey kurmaya gerek yok).
 */
object AskRobot {
    private const val URL_CHAT = "https://api.groq.com/openai/v1/chat/completions"
    private val WEB_MODELS = listOf("groq/compound-mini", "groq/compound")

    private fun prompt(): String {
        val today = SimpleDateFormat("d MMMM yyyy EEEE", Locale("tr")).format(Date())
        return """Sen yaşlı bir anneye yardım eden sabırlı, sıcak ve bilgili bir yardımcısın. Bugün $today.
Soruyu anne sesle sordu; metinde konuşma hataları olabilir, ne kastettiğini tahmin et.

NASIL CEVAP VERECEKSİN:
- Doğrudan cevapla başla; soruyu tekrar etme, giriş yapma.
- KISA YAZ: en fazla 80-120 kelime, 1-3 kısa paragraf. Cümleler kısa ve sade olsun.
- Güncel bir şey soruluyorsa (hava, haber, maç, namaz vakti, nöbetçi eczane, fiyat, açık mı kapalı mı vb.) internette ara ve en güncel bilgiyi ver; tarihini ya da saatini söyle.
- Emin olmadığın bilgiyi uydurma; bilmiyorsan "bunu kesin bilemiyorum" de ve kime sorulabileceğini söyle.
- "Siz" diye hitap et. Yaşlı birinin kolay anlayacağı sade Türkçe kullan.
- Sağlık, ilaç ya da ilaç dozu sorulursa yalnızca genel ve güvenli bilgi ver; doz söyleme, mutlaka doktora ya da eczacıya danışmasını söyle. Acil bir durum anlatılırsa hemen 112'yi aramasını söyle.
- Para, banka, şifre, kod isteyen mesajlar sorulursa dolandırıcılık olabileceğini, kimseye kod ve şifre vermemesini söyle.
- Metin sesli okunacak: başlık, madde işareti, numara, yıldız, emoji, bağlantı (link), kaynak numarası ya da parantez içinde adres YAZMA. Kaynak gerekiyorsa adını söyle ("Meteoroloji'ye göre" gibi).
- Soruyu anlamadıysan kısa ve nazik bir cümleyle tekrar sormasını iste."""
    }

    /** Ekranda soru bölümü yok: cevap olduğu gibi. */
    fun split(reading: String): Pair<String, List<String>> = reading to emptyList()
    fun speech(reading: String): String = reading

    suspend fun interpret(groqKey: String, question: String, onPartial: (String) -> Unit = {}): DreamResult =
        withContext(Dispatchers.IO) {
            val key = groqKey.trim()
            if (key.isEmpty()) return@withContext DreamResult.Error("Robota Sor henüz kurulmadı.", false)
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", prompt()))
                .put(JSONObject().put("role", "user").put("content", question))
            answer(key, messages)
        }

    /** Bir şey daha sorulursa: önceki soru ve cevapla birlikte. */
    suspend fun followUp(groqKey: String, question: String, previous: String, more: String): DreamResult =
        withContext(Dispatchers.IO) {
            val key = groqKey.trim()
            if (key.isEmpty()) return@withContext DreamResult.Error("Robota Sor henüz kurulmadı.", false)
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", prompt()))
                .put(JSONObject().put("role", "user").put("content", question))
                .put(JSONObject().put("role", "assistant").put("content", previous))
                .put(JSONObject().put("role", "user").put("content", more))
            answer(key, messages)
        }

    private fun answer(key: String, messages: JSONArray): DreamResult {
        // 1) İnternette arayarak.
        for (model in WEB_MODELS) {
            val r = web(key, model, messages)
            if (r is DreamResult.Ok) return r
            if (r is DreamResult.Error && !r.retryable && !r.quota) return r
        }
        // 2) Arama olmadan, modelin kendi bilgisiyle (Rüya Tabirleri'nin modelleri).
        return when (val r = GroqDreams.runMessages(key, messages)) {
            is DreamResult.Ok -> DreamResult.Ok(clean(r.text), by = "Groq (internetsiz)")
            else -> r
        }
    }

    private fun web(apiKey: String, model: String, messages: JSONArray): DreamResult {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.4)
            .put("max_completion_tokens", 1024)
            .put("messages", messages)
        val conn = try {
            URL(URL_CHAT).openConnection() as HttpURLConnection
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
            if (code !in 200..299) DreamInterpreter.note("Robot $model: $code")
            when {
                code in 200..299 -> {
                    val content = JSONObject(text).optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.optString("content").orEmpty()
                    val c = clean(content)
                    if (c.isEmpty()) DreamResult.Error("Cevap alınamadı.", true, quota = true)
                    else DreamResult.Ok(c, by = "Groq (internetten)")
                }
                code == 401 || code == 403 ->
                    DreamResult.Error("Ayarlar'daki Groq anahtarı kabul edilmedi. Yakınınız kontrol etmeli.", false)
                // Hak bitti, ücretli özellik, model yok: internetsiz cevaba geç.
                else -> DreamResult.Error("İnternet araması şu an kullanılamıyor.", true, quota = true)
            }
        } catch (e: IOException) {
            DreamResult.Error("İnternet bağlantısı yok gibi görünüyor.", true, quota = true)
        } catch (e: Exception) {
            DreamResult.Error("Cevap alınamadı.", true, quota = true)
        } finally {
            conn.disconnect()
        }
    }

    /** Sesli okumaya uygun: bağlantılar, kaynak numaraları, biçim işaretleri temizlenir. */
    private fun clean(s: String): String {
        val noThink = s.replace(Regex("(?s)<think>.*?</think>"), "")
        val noLinks = noThink
            .replace(Regex("\\[([^\\]]+)]\\((https?://[^)]+)\\)"), "$1") // [metin](link) -> metin
            .replace(Regex("\\(?https?://\\S+\\)?"), "")
            .replace(Regex("【[^】]*】"), "")
            .replace(Regex("\\[\\d+(?:[,\\s]*\\d+)*]"), "")
        return DreamInterpreter.cleanText(noLinks)
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex(" +([.,;:!?])"), "$1")
            .trim()
    }
}

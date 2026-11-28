package com.annem.launcher

import android.content.Context
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
        return """Senin adın Gökçe. Sabırlı, sıcak, neşeli ve bilgili bir yardımcısın; adın sorulursa "Ben Gökçe" de. Bugün $today.
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
- Türkçede "pazar" hem haftanın günü (Pazar) hem de alışveriş yapılan semt pazarı demektir; "pazara gideceğim", "pazardan alacağım" alışveriş pazarıdır. Hangisi olduğunu cümleden ayır, karıştırma.
- Her soru tek başına sorulmuş olabilir. Önceki mesajlar sizin daha önceki konuşmalarınızdır (zamanlarıyla); yeni soru onlarla ilgiliyse (ör. "ne alacaktım", "o ilaç", "dün sorduğum") bağı doğal kur ve tekrar sordurma. İlgisizse önceki konuşmaları yok say, eski konuyu kendiliğinden açma, "geçen sefer sormuştunuz" diye söze başlama.
- Kullanıcı sana bir plan ya da bilgi söylerse (ör. "pazartesi pazara gideceğim, balık alacağım") bunu not aldığını kısaca ve sıcak bir dille belirt; sonradan sorulursa hatırla.
- Soruyu anlamadıysan kısa ve nazik bir cümleyle tekrar sormasını iste."""
    }

    /** Ekranda soru bölümü yok: cevap olduğu gibi. */
    fun split(reading: String): Pair<String, List<String>> = reading to emptyList()
    fun speech(reading: String): String = reading

    suspend fun interpret(
        groqKey: String,
        question: String,
        context: Context? = null,
        history: List<DreamEntry> = emptyList(),
        onPartial: (String) -> Unit = {},
    ): DreamResult =
        withContext(Dispatchers.IO) {
            val key = groqKey.trim()
            if (key.isEmpty()) return@withContext DreamResult.Error("Gökçe henüz kurulmadı.", false)
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", prompt() + factsBlock(context)))
            pastTurns(context, history).forEach { messages.put(it) }
            messages.put(JSONObject().put("role", "user").put("content", stamp(System.currentTimeMillis()) + question))
            val r = answer(key, messages)
            if (r is DreamResult.Ok && context != null) RobotMemory.learnLater(context, key, question, r.text)
            r
        }

    /** Bir şey daha sorulursa: önceki soru ve cevapla birlikte. */
    suspend fun followUp(
        groqKey: String,
        question: String,
        previous: String,
        more: String,
        context: Context? = null,
        history: List<DreamEntry> = emptyList(),
    ): DreamResult =
        withContext(Dispatchers.IO) {
            val key = groqKey.trim()
            if (key.isEmpty()) return@withContext DreamResult.Error("Gökçe henüz kurulmadı.", false)
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", prompt() + factsBlock(context)))
            pastTurns(context, history).forEach { messages.put(it) }
            messages
                .put(JSONObject().put("role", "user").put("content", question))
                .put(JSONObject().put("role", "assistant").put("content", previous))
                .put(JSONObject().put("role", "user").put("content", stamp(System.currentTimeMillis()) + more))
            val r = answer(key, messages)
            if (r is DreamResult.Ok && context != null) RobotMemory.learnLater(context, key, "$question\n$more", r.text)
            r
        }

    private val stampFmt = SimpleDateFormat("d MMMM EEEE HH:mm", Locale("tr"))

    /** Mesajın başına zaman: "dün", "yarın", "pazartesi" doğru anlaşılsın. */
    private fun stamp(t: Long) = "[" + stampFmt.format(Date(t)) + "] "

    /** Kalıcı bilgiler (isim, şehir, aile...): sistem mesajına kısa not. */
    private fun factsBlock(context: Context?): String {
        if (context == null || !RobotMemory.enabled(context)) return ""
        val facts = RobotMemory.facts(context)
        if (facts.isEmpty()) return ""
        val sb = StringBuilder("\n\nKULLANICI HAKKINDA BİLDİKLERİN (kendisi söyledi; gerektiğinde doğal kullan, bu listeden söz etme):")
        facts.forEach { sb.append("\n- ").append(it) }
        return sb.toString()
    }

    /**
     * Son konuşmalar, gerçek bir sohbetin önceki mesajları gibi (eskiden yeniye, zamanlı).
     * Böylece her yeni soru ayrı bir "balon" olsa da robot öncekileri bir insan gibi hatırlar.
     */
    private fun pastTurns(context: Context?, history: List<DreamEntry>): List<JSONObject> {
        if (context == null || !RobotMemory.enabled(context)) return emptyList()
        val since = RobotMemory.since(context)
        val cutoff = System.currentTimeMillis() - 14L * 24 * 3600_000L
        return history
            .filter { it.time > since && it.time > cutoff }
            .take(8)
            .reversed()
            .flatMap { e ->
                listOf(
                    JSONObject().put("role", "user").put("content", stamp(e.time) + e.dream.take(600)),
                    JSONObject().put("role", "assistant").put("content", e.reading.take(700)),
                )
            }
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


/**
 * Robota Sor'un uzun süreli hafızası: kullanıcı hakkında kısa, kalıcı bilgiler (isim, şehir, aile,
 * çocukların yaşı, sağlık durumu gibi kendisinin söylediği şeyler). Her cevaptan sonra arka planda,
 * ayrı ve küçük bir istekle güncellenir. Ayarlar'dan görülür, kapatılır ve silinir.
 */
object RobotMemory {
    private const val PREFS = "robot_memory"
    private const val KEY_FACTS = "facts"
    private const val KEY_SINCE = "since"
    private const val KEY_ON = "enabled"
    private const val MAX_FACTS = 15
    private val MODELS = listOf("llama-3.3-70b-versatile", "openai/gpt-oss-120b", "openai/gpt-oss-20b")

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(c: Context) = prefs(c).getBoolean(KEY_ON, true)
    fun setEnabled(c: Context, on: Boolean) { prefs(c).edit().putBoolean(KEY_ON, on).apply() }

    fun facts(c: Context): List<String> =
        prefs(c).getString(KEY_FACTS, "").orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** Bu andan önceki konuşmalar artık hatırlanmaz (geçmiş listesi yine görünür). */
    fun since(c: Context): Long = prefs(c).getLong(KEY_SINCE, 0L)

    fun forget(c: Context) {
        prefs(c).edit().putString(KEY_FACTS, "").putLong(KEY_SINCE, System.currentTimeMillis()).apply()
    }

    fun removeFact(c: Context, fact: String) {
        prefs(c).edit().putString(KEY_FACTS, facts(c).filter { it != fact }.joinToString("\n")).apply()
    }

    /** Cevap gösterildikten sonra arka planda: yeni kalıcı bilgi var mı? */
    fun learnLater(context: Context, apiKey: String, question: String, answer: String) {
        val app = context.applicationContext
        if (!enabled(app)) return
        Thread {
            try { learn(app, apiKey, question, answer) } catch (_: Exception) { }
        }.start()
    }

    private fun learn(c: Context, apiKey: String, question: String, answer: String) {
        val old = facts(c)
        val sys = """Bir yardımcı robotun hafızasını güncelliyorsun. Görevin: KULLANICI hakkında ileride işe yarayacak,
kalıcı ve kısa bilgileri tutmak (adı, yaşadığı şehir/köy, ailesi ve çocuklarının adı/yaşı, mesleği, kendi söylediği sağlık
durumu, sevdiği ve sevmediği şeyler, önemli tarihler). Yalnızca KULLANICININ KENDİ SÖYLEDİĞİ şeyleri yaz; robotun cevabındaki
genel bilgileri, hava durumunu, haberleri, tek seferlik merakları YAZMA. Şifre, kart, hesap numarası gibi bilgileri ASLA yazma.
Eski bilgiyle çelişen yeni bilgi varsa eskisini güncelle. En fazla $MAX_FACTS madde.
ÇIKTI: Yalnızca güncel listenin TAMAMI, her satıra bir madde, satır başında "- " ile. Hiç bilgi yoksa yalnızca "YOK" yaz."""
        val user = buildString {
            append("Şu ana kadarki bilgiler:\n")
            if (old.isEmpty()) append("(boş)\n") else old.forEach { append("- ").append(it).append("\n") }
            append("\nYeni konuşma:\nKullanıcı: ").append(question.take(800))
            append("\nRobot: ").append(answer.take(600))
        }
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", sys))
            .put(JSONObject().put("role", "user").put("content", user))
        for (model in MODELS) {
            val out = call(apiKey, model, messages) ?: continue
            val t = out.trim()
            // "YOK": yeni bilgi yok; eski bilgilere dokunma.
            if (t.startsWith("YOK", ignoreCase = true)) return
            val list = t.lines()
                .map { it.trim().removePrefix("-").removePrefix("•").removePrefix("*").trim() }
                .filter { it.length in 3..160 }
                .distinct()
                .take(MAX_FACTS)
            // Güvenlik: model listeyi boş ya da çok kısa döndürürse eski bilgileri kaybetme.
            if (list.isEmpty() || list.size < old.size / 2) return
            prefs(c).edit().putString(KEY_FACTS, list.joinToString("\n")).apply()
            return
        }
    }

    private fun call(apiKey: String, model: String, messages: JSONArray): String? {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.1)
            .put("max_completion_tokens", 600)
            .put("messages", messages)
        if ("gpt-oss" in model) body.put("reasoning_effort", "low")
        val conn = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 40_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(text).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?.optString("content")?.replace(Regex("(?s)<think>.*?</think>"), "")
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}

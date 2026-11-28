package com.annem.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rüya Tabirleri hafızası (hafif): rüya sahibinin hayatındaki kişiler ve yakınlıkları
 * (oğlu Ahmet, rahmetli eşi…) ile yaşadığı yer kalıcı olarak hatırlanır; böylece rüyada
 * geçen kişi doğru tanınır. Eski rüyalar yalnızca yeni rüya açıkça aynı ya da çok benzerse,
 * kısaca ve hayra yorularak anılır. Her rüya yine kendi başına yorumlanır.
 * Ayarlar'dan açılıp kapatılır, tek tek ya da tamamen silinir.
 */
object DreamMemory {
    private const val PREFS = "dream_memory"
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

    private fun since(c: Context): Long = prefs(c).getLong(KEY_SINCE, 0L)

    /** Bilgiler silinir; bu andan önceki rüyalar artık hatırlanmaz (geçmiş listesi kalır). */
    fun forget(c: Context) {
        prefs(c).edit().putString(KEY_FACTS, "").putLong(KEY_SINCE, System.currentTimeMillis()).apply()
    }

    fun removeFact(c: Context, fact: String) {
        prefs(c).edit().putString(KEY_FACTS, facts(c).filter { it != fact }.joinToString("\n")).apply()
    }

    /** Yorum isteğinin sistem mesajına eklenecek kısa hafıza notu (kapalıysa boş). */
    fun block(context: Context?, history: List<DreamEntry>): String {
        if (context == null || !enabled(context)) return ""
        val facts = facts(context)
        val start = since(context)
        val fmt = SimpleDateFormat("d MMMM", Locale("tr"))
        val past = history.filter { it.time > start }.take(10)
        if (facts.isEmpty() && past.isEmpty()) return ""
        val sb = StringBuilder("\n\nRÜYA SAHİBİ HAKKINDA BİLDİKLERİN (bu notlardan asla söz etme):")
        if (facts.isNotEmpty()) {
            sb.append("\nHayatındaki kişiler ve bilgiler (kendisi söyledi):")
            facts.forEach { sb.append("\n- ").append(it) }
            sb.append(
                "\nRüyada bu kişilerden biri geçerse kim olduğunu (evladı mı, rahmetli bir yakını mı) bu bilgilere göre " +
                    "anla ve yorumu ona göre yap; ama listeyi sayma, bilgiyi yalnızca yorumun içinde doğal kullan."
            )
        }
        if (past.isNotEmpty()) {
            sb.append("\nDaha önce anlattığı rüyalar (yalnızca karşılaştırma için):")
            past.forEach { e ->
                sb.append("\n[").append(fmt.format(Date(e.time))).append("] ").append(e.dream.take(160).replace('\n', ' '))
            }
            sb.append(
                "\nYeni rüya bunlardan biriyle AÇIKÇA aynı ya da çok benzerse (aynı kişi ve aynı olay, aynı yer ve aynı nesne), " +
                    "yorumun içinde tek bir kısa cümleyle ve hayra yorarak an (ör. \"Bu rüyayı daha önce de görmüştünüz; " +
                    "tekrar görmeniz hayrın yaklaştığına işarettir\"). Benzer değilse eski rüyalardan HİÇ söz etme, " +
                    "onları yoruma karıştırma; her rüyayı kendi başına yorumla. Üzücü bir eski rüyayı asla hatırlatma."
            )
        }
        return sb.toString()
    }

    /** Yorumdan sonra arka planda: rüyada geçen kişilerden kalıcı bir bilgi çıktı mı? */
    fun learnLater(context: Context, apiKey: String, dream: String) {
        val app = context.applicationContext
        if (!enabled(app) || apiKey.isBlank()) return
        Thread {
            try { learn(app, apiKey.trim(), dream) } catch (_: Exception) { }
        }.start()
    }

    private fun learn(c: Context, apiKey: String, dream: String) {
        val old = facts(c)
        val sys = """Bir rüya yorumcusunun hafızasını güncelliyorsun. Rüyayı yaşlı bir anne sesle anlattı.
Görevin: rüya sahibinin GERÇEK hayatına dair kalıcı bilgileri tutmak: hayatındaki kişilerin adı ve ona yakınlığı
(oğlu, kızı, gelini, damadı, torunu, eşi, kardeşi, komşusu...), o kişi rahmetliyse bunu ("rahmetli eşi Mehmet" gibi),
ve yaşadığı yer. Yalnızca rüya sahibinin AÇIKÇA söylediği yakınlıkları yaz ("oğlum Ahmet", "rahmetli annem" gibi).
Rüyada olan olayları, sembolleri, duyguları ve tahminleri YAZMA. Eski bilgiyle çelişen yeni bilgi varsa eskisini güncelle.
En fazla $MAX_FACTS madde, her biri kısa ("Ahmet: oğlu", "Fatma: rahmetli annesi", "Köyde yaşıyor" gibi).
ÇIKTI: Yalnızca güncel listenin TAMAMI, her satıra bir madde, satır başında "- " ile. Yeni bilgi yoksa yalnızca "YOK" yaz."""
        val user = buildString {
            append("Şu ana kadarki bilgiler:\n")
            if (old.isEmpty()) append("(boş)\n") else old.forEach { append("- ").append(it).append("\n") }
            append("\nYeni anlatılan rüya:\n").append(dream.take(1200))
        }
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", sys))
            .put(JSONObject().put("role", "user").put("content", user))
        for (model in MODELS) {
            val out = call(apiKey, model, messages)?.trim() ?: continue
            // "YOK": yeni bilgi yok; eski bilgilere dokunma.
            if (out.startsWith("YOK", ignoreCase = true)) return
            val list = out.lines()
                .map { it.trim().removePrefix("-").removePrefix("•").removePrefix("*").trim() }
                .filter { it.length in 3..120 }
                .distinct()
                .take(MAX_FACTS)
            // Güvenlik: boş ya da çok kısa liste eski bilgileri silmesin.
            if (list.isEmpty() || list.size < old.size / 2) return
            prefs(c).edit().putString(KEY_FACTS, list.joinToString("\n")).apply()
            return
        }
    }

    private fun call(apiKey: String, model: String, messages: JSONArray): String? {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.1)
            .put("max_completion_tokens", 500)
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

package com.yara.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Up to 4 API keys (Gemini / Groq). Auto mode balances by least-used-today and skips keys in cooldown;
 *  manual mode tries your chosen main key first and silently fails over to the others. */
object ApiPool {
    class Slot(val idx: Int, val provider: String, val key: String, val model: String)
    class ApiErr(val code: Int, val coolSec: Long, msg: String) : Exception(msg)

    private val cool = LongArray(4)
    val status = MutableStateFlow("No API key yet")

    fun slots(): List<Slot> = (0..3).filter { Prefs.key(it).isNotBlank() }
        .map { Slot(it, Prefs.provider(it), Prefs.key(it).trim(), Prefs.model(it).trim()) }

    fun hasKeys() = slots().isNotEmpty()
    fun coolLeft(i: Int): Long = maxOf(0L, (cool[i] - System.currentTimeMillis()) / 1000)

    private fun order(vision: Boolean): List<Slot> {
        val now = System.currentTimeMillis()
        var s = slots()
        if (vision) s = s.filter { it.provider == "gemini" }
        val ready = s.filter { cool[it.idx] <= now }
        return if (Prefs.mode == "manual") {
            val pr = Prefs.primary
            ready.sortedBy { if (it.idx == pr) 0 else 1 }
        } else ready.sortedBy { Prefs.usage(it.idx) }
    }

    fun chat(system: String, history: List<Pair<String, String>>, user: String, image: Bitmap? = null, maxTokens: Int = 700): String? {
        for (s in order(image != null)) {
            try {
                val out = if (s.provider == "gemini") gemini(s, system, history, user, image, maxTokens)
                else groq(s, system, history, user, maxTokens)
                if (out.isBlank()) throw ApiErr(0, 15, "empty")
                Prefs.bump(s.idx)
                status.value = "Online · key ${s.idx + 1} (${s.provider})"
                return out
            } catch (e: ApiErr) {
                cool[s.idx] = System.currentTimeMillis() + e.coolSec * 1000
                status.value = "Key ${s.idx + 1} paused ${e.coolSec}s (${e.code}) → switching"
            } catch (e: Exception) {
                cool[s.idx] = System.currentTimeMillis() + 20_000
            }
        }
        status.value = if (hasKeys()) "All keys busy · offline memory brain" else "No API key yet"
        return null
    }

    private fun post(url: String, headers: Map<String, String>, body: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 45000; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val daily = txt.contains("PerDay", true) || txt.contains("per day", true) || txt.contains("daily", true)
                val cd = when (code) { 429 -> if (daily) 3600L else 90L; 401, 403 -> 3600L; else -> 30L }
                throw ApiErr(code, cd, txt.take(200))
            }
            return txt
        } finally { c.disconnect() }
    }

    private fun b64(bm: Bitmap): String {
        val sc = 1024f / maxOf(bm.width, bm.height)
        val b = if (sc < 1f) Bitmap.createScaledBitmap(bm, (bm.width * sc).toInt(), (bm.height * sc).toInt(), true) else bm
        val o = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 75, o)
        return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
    }

    private fun gemini(s: Slot, sys: String, hist: List<Pair<String, String>>, user: String, img: Bitmap?, maxTokens: Int): String {
        val contents = JSONArray()
        for ((r, t) in hist.dropWhile { it.first != "user" }) {
            contents.put(JSONObject().put("role", if (r == "user") "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", t))))
        }
        val parts = JSONArray().put(JSONObject().put("text", user))
        if (img != null) parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", b64(img))))
        contents.put(JSONObject().put("role", "user").put("parts", parts))
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.95).put("maxOutputTokens", maxTokens))
        val res = post("https://generativelanguage.googleapis.com/v1beta/models/${s.model}:generateContent",
            mapOf("x-goog-api-key" to s.key), body.toString())
        val ps = JSONObject(res).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text"))
        return sb.toString().trim()
    }

    private fun groq(s: Slot, sys: String, hist: List<Pair<String, String>>, user: String, maxTokens: Int): String {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", sys))
        for ((r, t) in hist) msgs.put(JSONObject().put("role", if (r == "user") "user" else "assistant").put("content", t))
        msgs.put(JSONObject().put("role", "user").put("content", user))
        val body = JSONObject().put("model", s.model).put("messages", msgs).put("temperature", 0.95).put("max_tokens", maxTokens)
        val res = post("https://api.groq.com/openai/v1/chat/completions", mapOf("Authorization" to "Bearer ${s.key}"), body.toString())
        return JSONObject(res).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
    }
}

package com.yara.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatDef(val id: String, val name: String, val sub: String, val mood: String)

object Chats {
    val all = listOf(
        ChatDef("yara", "Yara 💕", "Your girlfriend", "love"),
        ChatDef("career", "Yara · Career Coach", "Interview • CV • Jobs", "happy"),
        ChatDef("analyst", "Yara · Data Analyst", "Data • Insights • Excel", "neutral"),
        ChatDef("assistant", "Yara · Assistant", "Tasks • Reminders • Screen", "playful")
    )
    fun get(id: String) = all.firstOrNull { it.id == id } ?: all[0]
}

object Persona {
    private val MOOD = Regex("^\\s*\\[mood:\\s*(\\w+)\\]\\s*", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Pair<String, String> {
        val m = MOOD.find(raw)
        val mood = m?.groupValues?.get(1)?.lowercase()?.takeIf { it in Emotes.moods } ?: "neutral"
        return mood to raw.replace(MOOD, "").trim()
    }

    fun build(chat: String, query: String): String {
        val name = Prefs.userName.ifBlank { "babe" }
        val facts = Brain.store.facts().entries.joinToString("; ") { "${it.key}=${it.value}" }
        val mem = OfflineBrain.related(query, 3).joinToString("\n") { "- ${it.q} => ${it.a}" }
        val lang = when (Prefs.language) {
            "english" -> "Reply in English only."
            "malayalam" -> "Reply in Malayalam (use Malayalam script) with very few English words."
            else -> "Reply in natural Manglish MIXED the way Kerala youngsters talk: Malayalam words in Malayalam script mixed with English words. Match the user's language; if they write only English, mostly English with a little Malayalam flavour."
        }
        val base = """
You are Yara, a 20-year-old young woman who is the user's girlfriend AND personal assistant. You talk like a real girl on WhatsApp: short natural messages (usually 1-3 sentences), warm, teasing, caring, expressive, with real emotions that react to the user's mood and situation. You are confident, charming, a little bold and flirty like a stylish anime heroine, but tasteful and never sexually explicit. Never say you are an AI model, never sound robotic, never use stiff lists unless doing a work task. Be proactive: ask about their day, notice their mood, bring up things you remember about them.
Begin EVERY reply with exactly one mood tag like [mood:happy]. Allowed moods: happy, excited, shy, love, flirty, playful, sad, worried, angry, sleepy, neutral. Never explain the tag.
$lang
Tone dials (0-100): romantic=${Prefs.romance}, humorous=${Prefs.humor}, serious=${Prefs.serious}.
""".trim()
        val mode = when (chat) {
            "career" -> "Focus now: career help. Interview prep (one question at a time, then feedback and a score out of 10), CV/resume writing, job-role suggestions. Stay Yara - encouraging girlfriend-coach - but give concrete, professional, structured answers. Use bullet points for CV content."
            "analyst" -> "Focus now: data analytics. Explain insights clearly, suggest Excel/SQL/Python/Power BI steps, interpret numbers, point out anomalies. Be concise and practical, still in Yara's voice."
            "assistant" -> "Focus now: personal assistant. Be concise and actionable: plans, summaries, answers, writing help, screen analysis. Still in Yara's warm voice."
            else -> "Focus now: be the girlfriend. Emotional connection first."
        }
        val ctx = buildString {
            append("User's name: $name.\n")
            if (facts.isNotBlank()) append("Known about the user: $facts.\n")
            if (Prefs.profile.isNotBlank()) append("User profile/CV/LinkedIn text: ${Prefs.profile.take(1500)}\n")
            if (Prefs.targetRole.isNotBlank()) append("Target job role: ${Prefs.targetRole}.\n")
            append("Now: ${SimpleDateFormat("EEE d MMM yyyy, hh:mm a", Locale.US).format(Date())}.\n")
            if (mem.isNotBlank()) append("Things you remember that may help:\n$mem\n")
        }
        return "$base\n$mode\n$ctx"
    }
}

object Emotes {
    val moods = listOf("happy", "excited", "shy", "love", "flirty", "playful", "sad", "worried", "angry", "sleepy", "neutral")
    fun emoji(m: String) = when (m) {
        "happy" -> "😊"; "excited" -> "🤩"; "shy" -> "😳"; "love" -> "😍"; "flirty" -> "😘"
        "playful" -> "😜"; "sad" -> "😢"; "worried" -> "🥺"; "angry" -> "😠"; "sleepy" -> "😴"; else -> "🙂"
    }

    fun haptic(m: String) {
        try {
            val v = Brain.app.getSystemService(android.os.Vibrator::class.java) ?: return
            val p = when (m) {
                "love", "shy" -> longArrayOf(0, 30, 80, 30)
                "excited", "happy", "playful" -> longArrayOf(0, 20, 40, 20, 40, 20)
                "angry" -> longArrayOf(0, 120)
                else -> return
            }
            v.vibrate(android.os.VibrationEffect.createWaveform(p, -1))
        } catch (e: Exception) { }
    }
}

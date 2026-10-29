package com.yara.ai

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray

object Brain {
    lateinit var app: Context
    lateinit var store: Store
    val tick = MutableStateFlow(0)
    val typing = MutableStateFlow(false)
    val mood = MutableStateFlow("neutral")
    val offline = MutableStateFlow(false)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun init(c: Context) {
        app = c.applicationContext
        Prefs.init(app)
        store = Store(app)
        Notifier.ensure(app)
        Speaker.init(app)
    }

    private fun bump() { tick.value = tick.value + 1 }

    /** Send a user message (call from main thread). */
    fun send(chat: String, text: String, speak: Boolean = false, image: Bitmap? = null, shown: String? = null) {
        store.addMsg(chat, "user", shown ?: text, "neutral")
        typing.value = true
        bump()
        scope.launch {
            val res = try {
                withContext(Dispatchers.IO) { respond(chat, text, image) }
            } catch (e: Exception) { "worried" to "Oops, something glitched 🥺 Try again?" }
            store.addMsg(chat, "yara", res.second, res.first)
            mood.value = res.first
            if (speak || Prefs.voiceReply) Speaker.say(res.second, res.first)
            Emotes.haptic(res.first)
            typing.value = false
            bump()
        }
    }

    fun postYara(chat: String, text: String, m: String, speak: Boolean) {
        store.addMsg(chat, "yara", text, m)
        mood.value = m
        if (speak) Speaker.say(text, m)
        bump()
    }

    private suspend fun respond(chat: String, text: String, image: Bitmap?): Pair<String, String> {
        learnFacts(text)
        if (image == null) {
            val l = text.lowercase()
            if (Regex("(look at|see|analy[sz]e|read|check|watch).{0,15}screen|screen.{0,10}(nokk|kanu|look|see)|സ്ക്രീൻ").containsMatchIn(l)) return screen(text)
            Commands.handle(text)?.let { return it }
        }
        val hist = store.msgs(chat, 14).dropLast(1).map { it.role to it.text }
        val out = ApiPool.chat(Persona.build(chat, text), hist, text, image)
        if (out != null) {
            offline.value = false
            val (m, c) = Persona.parse(out)
            if (image == null && c.length > 1) store.addMemory(text.take(300), c, m)
            return m to c
        }
        offline.value = true
        return OfflineBrain.reply(text)
    }

    private suspend fun screen(q: String): Pair<String, String> {
        val acc = YaraAccessibility.instance
            ?: return "worried" to "സ്ക്രീൻ കാണാൻ Accessibility ഓൺ ആക്കണം ഡാ 🥺 Tools → Look at my screen ൽ പോയി enable ചെയ്യൂ."
        val (bmp, txt) = acc.capture()
        val prompt = "This is what is currently on the user's phone screen. Visible text:\n${txt.take(3000)}\n\nThe user said: \"$q\"\nLook at the screen, detect any question, problem, message or content, and answer/help briefly in Yara's voice."
        val sys = Persona.build("assistant", q)
        var out: String? = null
        if (bmp != null) out = ApiPool.chat(sys, emptyList(), prompt, bmp)
        if (out == null) out = ApiPool.chat(sys, emptyList(), prompt)
        if (out == null) return "worried" to "ഇപ്പോൾ ഓൺലൈൻ ബ്രെയിൻ കിട്ടുന്നില്ല 🥺 സ്ക്രീനിൽ ഇതാണ് കണ്ടത്: ${txt.take(200)}"
        val (m, c) = Persona.parse(out)
        return m to c
    }

    /** One-off AI call (CV generation etc.). */
    suspend fun oneShot(chat: String, prompt: String, maxTokens: Int = 1800): String = withContext(Dispatchers.IO) {
        ApiPool.chat(Persona.build(chat, prompt), emptyList(), prompt, null, maxTokens)?.let { Persona.parse(it).second }
            ?: "Offline right now 🥺 Add a working API key in Settings or try again later."
    }

    private fun learnFacts(t: String) {
        val l = t.trim()
        Regex("(?:my name is|i am called|i'm called|ente peru|എന്റെ പേര്)\\s+([\\p{L}]+)", RegexOption.IGNORE_CASE).find(l)?.let {
            val n = it.groupValues[1].replaceFirstChar { c -> c.uppercase() }
            store.setFact("name", n); if (Prefs.userName.isBlank()) Prefs.userName = n
        }
        Regex("i (?:love|like|enjoy) (.{2,40})", RegexOption.IGNORE_CASE).find(l)?.let { store.setFact("likes:" + it.groupValues[1].take(20), it.groupValues[1].trim('.', '!')) }
        Regex("i (?:work as|am a|am an|work at) (.{2,40})", RegexOption.IGNORE_CASE).find(l)?.let { store.setFact("work", it.groupValues[1].trim('.', '!')) }
        Regex("my birthday is (.{2,25})", RegexOption.IGNORE_CASE).find(l)?.let { store.setFact("birthday", it.groupValues[1].trim('.', '!')) }
    }

    val harvestTopics = listOf(
        "common job interview questions (HR, behavioural, technical) with strong sample answers",
        "Excel, SQL, Python and Power BI data-analysis basics",
        "how to write a CV, LinkedIn profile tips and salary negotiation",
        "general knowledge, science and technology facts for everyday curiosity",
        "daily life help in India: health, cooking, travel, money and study tips",
        "girlfriend-style casual replies in mixed Malayalam (Malayalam script) and English to messages like hi, how are you, miss you, good night, I am bored, I am sad, I am angry, I got a job",
        "useful Malayalam and English phrases, translations and vocabulary",
        "productivity, motivation and exam-preparation advice",
        "AI, programming and gadgets explained simply",
        "personal finance, careers and skills in demand"
    )

    /** Asks the AI for Q&A pairs and stores them on the phone so Yara can still answer when the API limit is reached. */
    suspend fun harvest(topic: String): Int = withContext(Dispatchers.IO) {
        val sys = "You output ONLY a valid JSON array, no markdown, no commentary."
        val prompt = "Create 20 diverse question-answer pairs about: $topic. Each answer 1-3 sentences. Format: [{\"q\":\"...\",\"a\":\"...\"}]"
        val raw = ApiPool.chat(sys, emptyList(), prompt, null, 2500) ?: return@withContext 0
        try {
            val s = raw.indexOf('['); val e = raw.lastIndexOf(']')
            val arr = JSONArray(raw.substring(s, e + 1))
            var n = 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val q = o.optString("q"); val a = o.optString("a")
                if (q.length > 3 && a.length > 3) { store.addMemory(q, a, "neutral"); n++ }
            }
            n
        } catch (e: Exception) { 0 }
    }
}

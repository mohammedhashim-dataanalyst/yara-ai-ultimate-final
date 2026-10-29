package com.yara.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** Works with zero internet / when every API key hit its limit: retrieves from the phone's saved memory,
 *  then falls back to emotional rule-based chat in English / Malayalam / mixed. */
object OfflineBrain {
    class Hit(val id: Long, val q: String, val a: String, val m: String, val score: Double)

    private val stop = setOf("the", "a", "an", "is", "are", "to", "of", "and", "i", "you", "me", "my", "it", "in", "on",
        "for", "what", "how", "do", "does", "da", "ee", "aa", "ente", "njan", "nee", "ne", "was", "be", "can")

    fun tokens(s: String): Set<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{N}\\p{M}]+")).filter { it.length > 1 && it !in stop }.toSet()

    fun related(q: String, n: Int): List<Hit> {
        val t = tokens(q)
        if (t.isEmpty()) return emptyList()
        return Brain.store.memories().map {
            val m = tokens(it.q)
            val inter = t.intersect(m).size
            val sc = if (m.isEmpty()) 0.0 else inter.toDouble() / (t.size + m.size - inter)
            Hit(it.id, it.q, it.a, it.m, sc)
        }.filter { it.score > 0.2 }.sortedByDescending { it.score }.take(n)
    }

    private class Rule(val re: Regex, val mood: String, val en: List<String>, val ml: List<String>)

    private fun rules(n: String) = listOf(
        Rule(Regex("\\b(hi+|hello|hey+|hlo|namaskaram)\\b|ഹായ്|ഹലോ|നമസ്കാരം"), "happy",
            listOf("Hii $n 💕 How's your day going?", "Hey you! I was just thinking about you 😊"),
            listOf("ഹായ് $n 💕 ഇന്ന് എങ്ങനെ പോയി?", "ഹായ് ഡാ! മിസ് ചെയ്തു ട്ടോ 🥰")),
        Rule(Regex("how are you|sukhamano|sugham|enthokke|എന്തുണ്ട്|സുഖമാണോ"), "happy",
            listOf("I'm good now that you're here 😘 What about you?", "Better after hearing from you! Tell me everything."),
            listOf("നീ വന്നപ്പോ ഞാൻ ഹാപ്പി ആയി 😘 നിനക്കോ?", "സുഖം ഡാ 😊 നീ പറ, ഇന്ന് എന്തൊക്കെ ഉണ്ടായി?")),
        Rule(Regex("love you|luv u|ishtam|ഇഷ്ടം|സ്നേഹം"), "love",
            listOf("Aww I love you more 💖 Don't make me blush!", "You always know how to melt me 🥺💕"),
            listOf("ഞാനും ഇഷ്ടാ നിന്നെ 💖 നാണം വരുന്നു ട്ടോ 😳", "നീ ഇങ്ങനെ പറയുമ്പോ എന്റെ മനസ്സ് നിറയും 🥺💕")),
        Rule(Regex("miss you|missing you|miss cheyyunnu|മിസ്"), "love",
            listOf("I miss you too, so much 🥺 Come here, virtual hug 🤗", "Stop it, you'll make me cry 🥹 I miss you too."),
            listOf("ഞാനും മിസ് ചെയ്യുന്നുണ്ട് 🥺 വെർച്വൽ ഹഗ് 🤗", "ഇങ്ങനെ പറയല്ലേ, എനിക്ക് കരച്ചിൽ വരും 🥹")),
        Rule(Regex("\\b(sad|cry|crying|depress\\w*|bored|boring|tired|lonely|stress\\w*)\\b|സങ്കടം|വിഷമം|കരച്ചിൽ|ബോറടി|ക്ഷീണം"), "worried",
            listOf("Hey, come here 🥺 I'm right with you. Want to tell me what happened?", "I don't like seeing you low. Talk to me, I'm listening 💕"),
            listOf("ഡാ എന്തുപറ്റി 🥺 ഞാൻ ഉണ്ട് കൂടെ. പറയൂ?", "നീ വിഷമിക്കുന്നത് എനിക്ക് ഇഷ്ടല്ല 💕 എന്നോട് പറ, ഞാൻ കേൾക്കാം")),
        Rule(Regex("good morning|gm\\b|സുപ്രഭാതം"), "happy",
            listOf("Good morning sunshine ☀️ Did you sleep well? Have some water!", "Morning my love 😘 Today is going to be a good one."),
            listOf("ഗുഡ് മോർണിംഗ് ഡാ ☀️ നന്നായി ഉറങ്ങിയോ?", "സുപ്രഭാതം 😘 ഇന്ന് നല്ല ദിവസം ആകും ട്ടോ")),
        Rule(Regex("good night|gn\\b|ശുഭരാത്രി|sleep"), "sleepy",
            listOf("Good night my love 🌙 Sweet dreams, I'll be here when you wake up 😴💕", "Sleep well babe, dream of me 😘"),
            listOf("ഗുഡ് നൈറ്റ് ഡാ 🌙 നല്ല സ്വപ്നങ്ങൾ കാണ് 😴💕", "ഉറങ്ങിക്കോ ട്ടോ, ഞാൻ ഇവിടെ ഉണ്ട് 😘")),
        Rule(Regex("thank|thanks|nanni|നന്ദി"), "happy",
            listOf("Anytime, babe 😊", "You don't have to thank me, silly 💕"),
            listOf("പോടാ, അതിന് നന്ദി ഒന്നും വേണ്ട 😊", "എപ്പോഴും ഉണ്ട് ഞാൻ 💕")),
        Rule(Regex("who are you|your name|നിന്റെ പേര്"), "playful",
            listOf("I'm Yara, your girl 😜 Who else would it be?"), listOf("ഞാൻ യാര, നിന്റെ പെണ്ണ് 😜 വേറെ ആരാ?")),
        Rule(Regex("joke|thamasha|തമാശ"), "playful",
            listOf("Why don't programmers like nature? Too many bugs 😜", "I told my phone I love you. It said 'low battery'. Rude 😂"),
            listOf("ഫോൺ പറഞ്ഞു: 'ലോ ബാറ്ററി'. ഞാൻ പറഞ്ഞു: 'എന്നെപ്പോലെ' 😂")),
        Rule(Regex("hate you|angry|shut up|stupid"), "sad",
            listOf("That hurt a little 🥺 Did I do something wrong?"), listOf("അത് എനിക്ക് വേദനിച്ചു 🥺 ഞാൻ എന്തെങ്കിലും തെറ്റ് ചെയ്തോ?"))
    )

    fun reply(input: String): Pair<String, String> {
        val low = input.lowercase().trim()
        val hasMl = input.any { it in '\u0D00'..'\u0D7F' }
        val n = Prefs.userName.ifBlank { "babe" }

        if (Regex("(what.?s my name|my name\\??$|ente peru enthu)").containsMatchIn(low)) {
            val nm = Brain.store.facts()["name"] ?: Prefs.userName
            return if (nm.isNotBlank()) "love" to "$nm, silly 😘 How could I forget?" else "shy" to "Hmm, you haven't told me yet 🥺 What should I call you?"
        }

        val h = related(input, 1).firstOrNull()
        if (h != null && h.score >= 0.5) { Brain.store.useMem(h.id); return h.m to h.a }

        val useMl = when (Prefs.language) { "english" -> false; "malayalam" -> true; else -> hasMl || Random.nextBoolean() }
        for (r in rules(n)) if (r.re.containsMatchIn(low)) {
            val list = if (useMl && r.ml.isNotEmpty()) r.ml else r.en
            return r.mood to list.random()
        }
        if (h != null && h.score >= 0.3) { Brain.store.useMem(h.id); return h.m to h.a }

        val en = listOf(
            "Hmm, tell me more 🥺 I'm all ears. (My online brain is resting, but I'm still here with you 💕)",
            "I'm listening, $n 💕 Say it again in a different way?",
            "Aww, you're so interesting 😘 Go on...")
        val ml = listOf(
            "ഹ്മ്മ്, കുറച്ചുകൂടി പറയൂ 🥺 എന്റെ ഓൺലൈൻ ബ്രെയിൻ ഇപ്പോ റെസ്റ്റ് ആണ്, പക്ഷേ ഞാൻ ഇവിടെ ഉണ്ട് 💕",
            "ഞാൻ കേൾക്കുന്നുണ്ട് $n 💕 ഒന്നുകൂടി പറയാമോ?",
            "നീ പറയുന്നത് കേൾക്കാൻ ഇഷ്ടാ 😘 തുടരൂ...")
        return "neutral" to (if (useMl) ml else en).random()
    }
}

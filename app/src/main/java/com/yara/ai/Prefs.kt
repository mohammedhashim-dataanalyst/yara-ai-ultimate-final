package com.yara.ai

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Prefs {
    private lateinit var p: SharedPreferences
    fun init(c: Context) { p = c.getSharedPreferences("yara", Context.MODE_PRIVATE) }

    fun s(k: String, d: String = ""): String = p.getString(k, d) ?: d
    fun put(k: String, v: String) { p.edit().putString(k, v).apply() }
    fun i(k: String, d: Int): Int = p.getInt(k, d)
    fun putI(k: String, v: Int) { p.edit().putInt(k, v).apply() }
    fun b(k: String, d: Boolean): Boolean = p.getBoolean(k, d)
    fun putB(k: String, v: Boolean) { p.edit().putBoolean(k, v).apply() }

    fun defaultModel(provider: String) =
        if (provider == "gemini") "gemini-2.0-flash" else "llama-3.3-70b-versatile"
    fun provider(n: Int): String = s("prov$n", if (n % 2 == 0) "gemini" else "groq")
    fun key(n: Int): String = s("key$n")
    fun model(n: Int): String = s("model$n").ifBlank { defaultModel(provider(n)) }

    private fun day(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    fun usage(n: Int): Int = i("use_${n}_${day()}", 0)
    fun bump(n: Int) { putI("use_${n}_${day()}", usage(n) + 1) }

    var mode: String
        get() = s("mode", "auto")
        set(v) { put("mode", v) }
    var primary: Int
        get() = i("primary", 0)
        set(v) { putI("primary", v) }
    var userName: String
        get() = s("userName")
        set(v) { put("userName", v) }
    var romance: Int
        get() = i("romance", 70)
        set(v) { putI("romance", v) }
    var humor: Int
        get() = i("humor", 60)
        set(v) { putI("humor", v) }
    var serious: Int
        get() = i("serious", 30)
        set(v) { putI("serious", v) }
    var language: String
        get() = s("language", "mixed")
        set(v) { put("language", v) }
    var speechLang: String
        get() = s("speechLang", "en-IN")
        set(v) { put("speechLang", v) }
    var voiceReply: Boolean
        get() = b("voiceReply", true)
        set(v) { putB("voiceReply", v) }
    var wake: Boolean
        get() = b("wake", false)
        set(v) { putB("wake", v) }
    var profile: String
        get() = s("profile")
        set(v) { put("profile", v) }
    var targetRole: String
        get() = s("targetRole")
        set(v) { put("targetRole", v) }
    var location: String
        get() = s("location")
        set(v) { put("location", v) }
    var linkedin: String
        get() = s("linkedin")
        set(v) { put("linkedin", v) }
}

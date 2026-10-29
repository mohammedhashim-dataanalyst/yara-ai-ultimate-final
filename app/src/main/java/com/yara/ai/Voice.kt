package com.yara.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlin.random.Random

/** Yara's voice: adaptive pitch/rate per emotion, vocal emotes, and per-script language switching (Malayalam / English). */
object Speaker {
    private var tts: TextToSpeech? = null
    private var ok = false
    private class Seg(val text: String, val ml: Boolean, val pitch: Float, val rate: Float)
    private val q = ArrayDeque<Seg>()
    private val main = Handler(Looper.getMainLooper())
    val speaking = MutableStateFlow(false)
    val mlMissing = MutableStateFlow(false)

    fun init(c: Context) {
        tts = TextToSpeech(c.applicationContext) { st ->
            ok = st == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { main.post { next() } }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { main.post { next() } }
            })
        }
    }

    private fun voiceFor(m: String): Pair<Float, Float> = when (m) {
        "happy" -> 1.2f to 1.05f
        "excited" -> 1.3f to 1.2f
        "shy" -> 1.25f to 0.9f
        "love" -> 1.1f to 0.85f
        "flirty" -> 1.05f to 0.88f
        "playful" -> 1.25f to 1.1f
        "sad" -> 0.9f to 0.8f
        "worried" -> 1.0f to 0.92f
        "angry" -> 0.95f to 1.2f
        "sleepy" -> 0.9f to 0.75f
        else -> 1.1f to 1.0f
    }

    private fun emote(m: String): String? = if (Random.nextFloat() > 0.4f) null else when (m) {
        "happy", "playful" -> "Hehe!"
        "excited" -> "Yay!"
        "shy" -> "Umm..."
        "love", "worried" -> "Aww..."
        "sad", "sleepy" -> "Hmm..."
        "angry" -> "Hmph!"
        else -> null
    }

    private fun clean(s: String) = s.replace(Regex("\\[mood:[^\\]]*\\]"), "")
        .replace(Regex("[^\\p{L}\\p{N}\\p{M}\\s.,!?'’:;\\-\\u200C\\u200D]"), " ")

    private fun split(s: String): List<Pair<String, Boolean>> {
        val out = ArrayList<Pair<String, Boolean>>()
        var cur = StringBuilder(); var ml: Boolean? = null
        for (ch in s) {
            val isM = ch in '\u0D00'..'\u0D7F'
            if (!ch.isLetter() && !isM) { cur.append(ch); continue }
            if (ml == null) ml = isM
            if (isM != ml) { out.add(cur.toString() to (ml ?: false)); cur = StringBuilder(); ml = isM }
            cur.append(ch)
        }
        if (cur.isNotBlank()) out.add(cur.toString() to (ml ?: false))
        return out.filter { it.first.isNotBlank() }
    }

    fun say(raw: String, mood: String) {
        val job = Runnable {
            val (p, r) = voiceFor(mood)
            val text = (emote(mood)?.let { "$it " } ?: "") + clean(raw)
            for ((t, ml) in split(text)) q.addLast(Seg(t, ml, p, r))
            if (!speaking.value) next()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) job.run() else main.post(job)
    }

    private fun next() {
        val t = tts
        val s = q.removeFirstOrNull()
        if (!ok || t == null || s == null) { speaking.value = false; return }
        speaking.value = true
        val res = t.setLanguage(if (s.ml) Locale("ml", "IN") else Locale("en", "IN"))
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (s.ml) { mlMissing.value = true; next(); return }
            t.setLanguage(Locale.US)
        }
        t.setPitch(s.pitch); t.setSpeechRate(s.rate)
        t.speak(s.text, TextToSpeech.QUEUE_FLUSH, null, "y" + System.nanoTime())
    }

    fun stop() {
        main.post { q.clear(); tts?.stop(); speaking.value = false }
    }
}

class Listener(
    private val ctx: Context,
    private val cbPartial: (String) -> Unit,
    private val cbFinal: (String) -> Unit,
    private val cbError: (Int) -> Unit
) {
    private var r: SpeechRecognizer? = null

    fun start() {
        destroy()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { cbError(-1); return }
        val rec = SpeechRecognizer.createSpeechRecognizer(ctx)
        r = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, b: Bundle?) {}
            override fun onResults(b: Bundle?) {
                cbFinal(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
            }
            override fun onPartialResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (t.isNotBlank()) cbPartial(t)
            }
            override fun onError(e: Int) { cbError(e) }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Prefs.speechLang)
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        rec.startListening(i)
    }

    fun stop() { r?.stopListening() }
    fun destroy() { r?.destroy(); r = null }
}

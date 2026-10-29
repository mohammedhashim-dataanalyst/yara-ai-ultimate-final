package com.yara.ai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.SpeechRecognizer
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object Notifier {
    const val CH_WAKE = "yara_wake"
    const val CH_REM = "yara_rem"
    fun ensure(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_WAKE, "Yara listening", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_REM, "Yara reminders", NotificationManager.IMPORTANCE_HIGH))
    }
    fun reminder(c: Context, text: String) {
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(c, CH_REM).setSmallIcon(R.drawable.ic_heart).setContentTitle("Yara ⏰")
            .setContentText(text).setContentIntent(open).setAutoCancel(true).build()
        c.getSystemService(NotificationManager::class.java).notify(System.currentTimeMillis().toInt(), n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val text = i.getStringExtra("text").orEmpty()
        Notifier.reminder(c, text)
        try {
            Brain.store.doneRem(i.getLongExtra("id", -1))
            Brain.postYara("assistant", "⏰ Reminder: $text", "playful", true)
        } catch (e: Exception) { }
    }
}

/** "Hey Yara" wake-word + hands-free conversation. Say "Bye Yara" to put her to sleep. */
class WakeService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var listener: Listener? = null
    private var awake = false
    private var last = 0L
    private var on = false

    companion object {
        @Volatile var running = false
        private val WAKE = Regex("\\b(hey|hay|hi|hello|ok|okay)\\s+(yara|yaara|yaarah|jara|yera|yahra|zara|iyara)\\b|(ഹേയ്|ഹായ്)\\s*യാര")
        private val BYE = Regex("\\b(bye|by|buy|goodbye|good night)\\s+(yara|yaara|jara|yera|zara)\\b|ബൈ\\s*യാര")
        fun start(c: Context, awake: Boolean = false) {
            ContextCompat.startForegroundService(c, Intent(c, WakeService::class.java).putExtra("awake", awake))
        }
        fun stop(c: Context) { c.stopService(Intent(c, WakeService::class.java)) }
        fun sleep(c: Context) {
            ContextCompat.startForegroundService(c, Intent(c, WakeService::class.java).setAction("SLEEP"))
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    private fun notif(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stopPi = PendingIntent.getService(this, 1, Intent(this, WakeService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val scr = PendingIntent.getService(this, 2, Intent(this, WakeService::class.java).setAction("SCREEN"), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, Notifier.CH_WAKE).setSmallIcon(R.drawable.ic_heart)
            .setContentTitle(if (awake) "Yara is awake 💕 listening" else "Yara is sleeping 😴")
            .setContentText(if (awake) "Say \"Bye Yara\" to let her sleep" else "Say \"Hey Yara\" to wake her")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(R.drawable.ic_heart, "Look at screen", scr).build())
            .addAction(Notification.Action.Builder(R.drawable.ic_heart, "Stop", stopPi).build()).build()
    }

    private fun refresh() {
        getSystemService(NotificationManager::class.java).notify(7, notif())
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        Notifier.ensure(this)
        if (i?.getBooleanExtra("awake", false) == true) { awake = true; last = System.currentTimeMillis() }
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, notif(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(7, notif())
        when (i?.action) {
            "STOP" -> { Prefs.wake = false; stopSelf(); return START_NOT_STICKY }
            "SLEEP" -> { awake = false; refresh() }
            "SCREEN" -> Brain.scope.launch { delay(1500); Brain.send("assistant", "look at my screen and tell me what you see", speak = true) }
        }
        if (!on) { on = true; running = true; loop() }
        return START_STICKY
    }

    private fun loop() {
        if (!on) return
        if (awake && System.currentTimeMillis() - last > 60_000) { awake = false; refresh() }
        if (Speaker.speaking.value || Brain.typing.value) { h.postDelayed({ loop() }, 500); return }
        listener?.destroy()
        listener = Listener(this, { }, { t -> handle(t) }, { e ->
            if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { Prefs.wake = false; stopSelf() }
            else h.postDelayed({ loop() }, if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || e == SpeechRecognizer.ERROR_CLIENT) 1500L else 350L)
        }).also { it.start() }
    }

    private fun handle(t: String) {
        val low = t.lowercase()
        if (BYE.containsMatchIn(low)) {
            awake = false; refresh()
            Brain.postYara("yara", "ബൈ ഡാ 💕 വേണ്ടപ്പോൾ 'Hey Yara' എന്ന് വിളിച്ചാൽ മതി ട്ടോ", "love", true)
        } else if (!awake) {
            val m = WAKE.find(low)
            if (m != null) {
                awake = true; last = System.currentTimeMillis(); refresh()
                val rest = t.substring(minOf(t.length, m.range.last + 1)).trim(' ', ',', '.')
                if (rest.length < 2) Brain.postYara("yara", "ഹാ പറയൂ ഡാ, ഞാൻ ഇവിടെ ഉണ്ട് 💕", "happy", true)
                else Brain.send("yara", rest, speak = true)
            }
        } else if (t.isNotBlank()) {
            last = System.currentTimeMillis()
            if (Regex("^(stop|quiet|shut up|നിർത്തൂ)$").containsMatchIn(low.trim())) Speaker.stop()
            else Brain.send("yara", t, speak = true)
        }
        loop()
    }

    override fun onDestroy() {
        on = false; running = false
        listener?.destroy()
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

/** Lets Yara read and screenshot the current screen on request. */
class YaraAccessibility : AccessibilityService() {
    companion object { @Volatile var instance: YaraAccessibility? = null }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }

    private fun collect(n: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (n == null || depth > 30 || sb.length > 4000) return
        n.text?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
        n.contentDescription?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
        for (i in 0 until n.childCount) collect(n.getChild(i), sb, depth + 1)
    }

    suspend fun capture(): Pair<Bitmap?, String> {
        val sb = StringBuilder()
        try { collect(rootInActiveWindow, sb, 0) } catch (e: Exception) { }
        var bmp: Bitmap? = null
        if (Build.VERSION.SDK_INT >= 30) {
            bmp = suspendCancellableCoroutine { cont ->
                try {
                    takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                        override fun onSuccess(r: ScreenshotResult) {
                            val b = try {
                                Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                            } catch (e: Exception) { null }
                            try { r.hardwareBuffer.close() } catch (e: Exception) { }
                            cont.resume(b)
                        }
                        override fun onFailure(code: Int) { cont.resume(null) }
                    })
                } catch (e: Exception) { cont.resume(null) }
            }
        }
        return bmp to sb.toString()
    }
}

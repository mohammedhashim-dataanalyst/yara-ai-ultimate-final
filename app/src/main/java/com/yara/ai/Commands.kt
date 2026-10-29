package com.yara.ai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Google-Assistant-style voice/text commands handled on the phone itself (no API tokens used). */
object Commands {
    private fun launch(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); Brain.app.startActivity(i) }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun view(url: String) = launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun handle(t: String): Pair<String, String>? {
        val l = t.lowercase().trim().trimEnd('.', '!', '?')

        if (Regex("\\b(torch|flash ?light)\\b|ടോർച്ച്").containsMatchIn(l)) {
            val on = !Regex("\\boff\\b|ഓഫ്").containsMatchIn(l)
            return if (torch(on)) "happy" to (if (on) "Torch ഓൺ ആക്കി 🔦" else "Torch ഓഫ് ആക്കി ✅")
            else "sad" to "Torch ഓൺ ആക്കാൻ പറ്റിയില്ല 🥺"
        }
        if (Regex("what('s| is)? the time|time now|current time|what time is it|സമയം എത്ര").containsMatchIn(l))
            return "happy" to "ഇപ്പോൾ ${SimpleDateFormat("hh:mm a", Locale.US).format(Date())} ആണ് ⏰"
        if (Regex("today'?s date|what is the date|what('s| is) the date|what day is").containsMatchIn(l))
            return "happy" to "ഇന്ന് ${SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(Date())} 📅"

        Regex("remind me (?:to )?(.+?) in (\\d+)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)").find(l)?.let {
            return setRem(it.groupValues[1], it.groupValues[2].toLong(), it.groupValues[3])
        }
        Regex("remind me in (\\d+)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)\\s*(?:to )?(.+)").find(l)?.let {
            return setRem(it.groupValues[3], it.groupValues[1].toLong(), it.groupValues[2])
        }
        Regex("remind me (?:to )?(.+?) at (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(l)?.let {
            var h = it.groupValues[2].toInt()
            val m = it.groupValues[3].ifBlank { "0" }.toInt()
            val ap = it.groupValues[4]
            if (ap == "pm" && h < 12) h += 12
            if (ap == "am" && h == 12) h = 0
            val c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m); c.set(Calendar.SECOND, 0)
            if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
            Reminders.schedule(it.groupValues[1], c.timeInMillis)
            return "happy" to "ശരി ഡാ ⏰ ${SimpleDateFormat("hh:mm a", Locale.US).format(c.time)} ന് ഞാൻ ഓർമ്മിപ്പിക്കാം: ${it.groupValues[1]}"
        }
        if (Regex("my reminders|reminders list|list reminders").containsMatchIn(l)) {
            val r = Brain.store.pendingRems()
            return "neutral" to if (r.isEmpty()) "ഇപ്പോൾ reminders ഒന്നുമില്ല ✨" else
                r.joinToString("\n") { "⏰ " + SimpleDateFormat("d MMM hh:mm a", Locale.US).format(Date(it.at)) + " — " + it.text }
        }

        if (l.contains("job") && Regex("\\b(find|search|show|look for|open|get)\\b").containsMatchIn(l)) {
            val m = Regex("jobs?\\s*(?:for|as|in)?\\s*(.*)$").find(l)?.groupValues?.get(1)?.trim().orEmpty()
            val parts = m.split(" in ", limit = 2)
            val role = parts[0].ifBlank { Prefs.targetRole.ifBlank { "software engineer" } }
            val loc = parts.getOrNull(1)?.trim().takeUnless { it.isNullOrBlank() } ?: Prefs.location
            view("https://www.linkedin.com/jobs/search/?keywords=${enc(role)}&location=${enc(loc)}")
            return "excited" to "LinkedIn-ൽ '$role' ജോബ്സ് തുറക്കുന്നു 💼 All the best, ഞാൻ കൂടെ ഉണ്ട്!"
        }
        Regex("^play (.+) on youtube$").find(l)?.let {
            view("https://www.youtube.com/results?search_query=${enc(it.groupValues[1])}")
            return "happy" to "YouTube-ൽ '${it.groupValues[1]}' തിരയുന്നു 🎵"
        }
        Regex("^(?:google|search(?: for)?)\\s+(.+)$").find(l)?.let {
            view("https://www.google.com/search?q=${enc(it.groupValues[1])}")
            return "happy" to "'${it.groupValues[1]}' സെർച്ച് ചെയ്യുന്നു 🔎"
        }
        Regex("^(?:open|launch|start)\\s+(.+)$").find(l)?.let {
            val name = it.groupValues[1].trim()
            val i = findApp(name)
            if (i != null) { launch(i); return "happy" to "$name തുറക്കുന്നു ✅" }
        }
        return null
    }

    private fun findApp(name: String): Intent? {
        val pm = Brain.app.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val ri = pm.queryIntentActivities(main, 0)
        val hit = ri.firstOrNull { it.loadLabel(pm).toString().lowercase() == name }
            ?: ri.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(name) } ?: return null
        return pm.getLaunchIntentForPackage(hit.activityInfo.packageName)
    }

    private fun setRem(text: String, n: Long, unit: String): Pair<String, String> {
        val ms = when {
            unit.startsWith("h") -> n * 3_600_000L
            unit.startsWith("s") -> n * 1000L
            else -> n * 60_000L
        }
        Reminders.schedule(text, System.currentTimeMillis() + ms)
        return "happy" to "ശരി ഡാ ⏰ $n $unit കഴിഞ്ഞ് ഞാൻ ഓർമ്മിപ്പിക്കാം: $text"
    }

    private fun torch(on: Boolean): Boolean = try {
        val cm = Brain.app.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        cm.setTorchMode(id, on); true
    } catch (e: Exception) { false }
}

object Reminders {
    fun schedule(text: String, at: Long): Long {
        val id = Brain.store.addRem(text, at)
        val am = Brain.app.getSystemService(AlarmManager::class.java)
        val i = Intent(Brain.app, ReminderReceiver::class.java).putExtra("id", id).putExtra("text", text)
        val pi = PendingIntent.getBroadcast(Brain.app, id.toInt(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        return id
    }
}

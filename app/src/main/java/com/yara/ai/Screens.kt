@file:OptIn(ExperimentalMaterial3Api::class)

package com.yara.ai

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
private fun Page(title: String, back: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = back) { Icon(Icons.Filled.ArrowBack, null) } })
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
fun CallScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val mood by Brain.mood.collectAsState()
    val speaking by Speaker.speaking.collectAsState()
    val typing by Brain.typing.collectAsState()
    val tick by Brain.tick.collectAsState()
    var inCall by remember { mutableStateOf(false) }
    val last = remember(tick) { Brain.store.last("yara")?.text.orEmpty() }
    Column(Modifier.fillMaxSize().background(Color(0xFF120E1C)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Text("Yara 💕", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(if (!inCall) "Tap to start a voice call" else if (typing) "thinking…" else if (speaking) "speaking…" else "listening… 🎙️", color = Pink)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(260.dp).clip(CircleShape)) { YaraAvatar(mood, speaking, Modifier.fillMaxSize()) }
        Spacer(Modifier.height(20.dp))
        Text(last, fontSize = 15.sp, maxLines = 5, modifier = Modifier.padding(8.dp))
        Spacer(Modifier.weight(1f))
        Button(onClick = {
            if (!inCall) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == 0) {
                    inCall = true; Prefs.wake = true; WakeService.start(ctx, awake = true)
                } else Toast.makeText(ctx, "Allow microphone first", Toast.LENGTH_SHORT).show()
            } else { inCall = false; WakeService.sleep(ctx); Speaker.stop() }
        }) { Text(if (inCall) "End call (Yara sleeps, wake word stays on)" else "Start call") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = back) { Text("Back") }
    }
}

@Composable
fun CvScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(Prefs.userName) }
    var contact by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(Prefs.targetRole) }
    var profile by remember { mutableStateOf(Prefs.profile) }
    var cv by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Page("CV builder", back) {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Full name") })
        OutlinedTextField(contact, { contact = it }, Modifier.fillMaxWidth(), label = { Text("Email • phone • city • LinkedIn") })
        OutlinedTextField(role, { role = it; Prefs.targetRole = it }, Modifier.fillMaxWidth(), label = { Text("Target job role") })
        OutlinedTextField(profile, { profile = it; Prefs.profile = it }, Modifier.fillMaxWidth().height(160.dp),
            label = { Text("Experience, education, skills, projects (rough notes are fine)") })
        Button(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                cv = Brain.oneShot("career", "Write a clean ATS-friendly one-page CV in plain text for $name ($contact) applying for: $role. Use headings in CAPS (SUMMARY, EXPERIENCE, EDUCATION, SKILLS, PROJECTS), bullet points starting with '- ', strong action verbs and measurable results. Base it on these notes, do not invent employers or degrees:\n$profile")
                busy = false
            }
        }) { Text(if (busy) "Yara is writing…" else "Generate with AI ✨") }
        if (cv.isNotBlank()) {
            OutlinedTextField(cv, { cv = it }, Modifier.fillMaxWidth().height(380.dp), label = { Text("Edit your CV") })
            Button(onClick = {
                try {
                    val uri = CvPdf.make(ctx, cv)
                    val i = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(i, "Share CV PDF"))
                } catch (e: Exception) { Toast.makeText(ctx, "PDF failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }) { Text("Export / share PDF 📄") }
        }
    }
}

@Composable
fun JobsScreen(open: (String, String?) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    var role by remember { mutableStateOf(Prefs.targetRole) }
    var loc by remember { mutableStateOf(Prefs.location) }
    var url by remember { mutableStateOf(Prefs.linkedin) }
    var profile by remember { mutableStateOf(Prefs.profile) }
    fun web(u: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
    fun e(s: String) = URLEncoder.encode(s, "UTF-8")
    Page("Job finder", back) {
        Text("LinkedIn doesn't allow apps to read your account, so Yara works with your profile text and opens LinkedIn's own search for you.", fontSize = 12.sp)
        OutlinedTextField(role, { role = it; Prefs.targetRole = it }, Modifier.fillMaxWidth(), label = { Text("Job role / keywords") })
        OutlinedTextField(loc, { loc = it; Prefs.location = it }, Modifier.fillMaxWidth(), label = { Text("Location") })
        Button(onClick = { web("https://www.linkedin.com/jobs/search/?keywords=${e(role)}&location=${e(loc)}") }, modifier = Modifier.fillMaxWidth()) { Text("Open LinkedIn Jobs 💼") }
        OutlinedButton(onClick = { web("https://www.naukri.com/${e(role.replace(' ', '-'))}-jobs") }, modifier = Modifier.fillMaxWidth()) { Text("Open Naukri") }
        OutlinedButton(onClick = { web("https://in.indeed.com/jobs?q=${e(role)}&l=${e(loc)}") }, modifier = Modifier.fillMaxWidth()) { Text("Open Indeed") }
        OutlinedTextField(url, { url = it; Prefs.linkedin = it }, Modifier.fillMaxWidth(), label = { Text("Your LinkedIn profile URL") })
        OutlinedButton(onClick = { if (url.startsWith("http")) web(url) else Toast.makeText(ctx, "Paste your profile URL first", Toast.LENGTH_SHORT).show() },
            modifier = Modifier.fillMaxWidth()) { Text("Open my profile (copy About/Experience)") }
        OutlinedTextField(profile, { profile = it; Prefs.profile = it }, Modifier.fillMaxWidth().height(150.dp), label = { Text("Paste profile / skills here") })
        Button(onClick = {
            open("career", "Based on my profile and target role, suggest 8 job roles I should apply for, what skills I'm missing, and 5 keywords to put on LinkedIn. Role: $role. Location: $loc.")
        }, modifier = Modifier.fillMaxWidth()) { Text("Ask Yara to match job roles ✨") }
    }
}

@Composable
fun CsvScreen(open: (String, String?) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    var summary by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            summary = try {
                val txt = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(3_000_000) }.orEmpty()
                CsvAnalyzer.summarize(txt)
            } catch (e: Exception) { "Couldn't read file: ${e.message}" }
        }
    }
    Page("Data analytics", back) {
        Text("Pick a CSV file. Yara analyses it on your phone first (free, offline), then can explain insights.", fontSize = 12.sp)
        Button(onClick = { pick.launch("*/*") }, modifier = Modifier.fillMaxWidth()) { Text("Choose CSV file 📊") }
        if (summary.isNotBlank()) {
            Text(summary, fontSize = 13.sp)
            Button(onClick = { open("analyst", "Here is the summary of my dataset:\n$summary\n\nGive me key insights, anomalies, data-quality problems and the next analysis steps.") },
                modifier = Modifier.fillMaxWidth()) { Text("Ask Yara for insights ✨") }
        }
    }
}

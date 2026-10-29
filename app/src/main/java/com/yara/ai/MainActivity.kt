@file:OptIn(ExperimentalMaterial3Api::class)

package com.yara.ai

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { YaraTheme { Root() } }
    }
}

@Composable
fun Root() {
    val ctx = LocalContext.current
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val l = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        perms.launch(l.toTypedArray())
        if (Prefs.wake && !WakeService.running) try { WakeService.start(ctx) } catch (e: Exception) { }
    }
    var screen by rememberSaveable { mutableStateOf("home") }
    var starter by remember { mutableStateOf<String?>(null) }
    BackHandler(screen != "home") { screen = "home" }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            screen == "home" -> HomeScreen(open = { id, st -> starter = st; screen = "chat:$id" }, nav = { screen = it })
            screen.startsWith("chat:") -> ChatScreen(screen.removePrefix("chat:"), starter, { starter = null }, { screen = "home" }, { screen = "call" })
            screen == "settings" -> SettingsScreen { screen = "home" }
            screen == "call" -> CallScreen { screen = "home" }
            screen == "cv" -> CvScreen { screen = "home" }
            screen == "jobs" -> JobsScreen(open = { id, st -> starter = st; screen = "chat:$id" }) { screen = "home" }
            screen == "csv" -> CsvScreen(open = { id, st -> starter = st; screen = "chat:$id" }) { screen = "home" }
        }
    }
}

@Composable
fun HomeScreen(open: (String, String?) -> Unit, nav: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("CHATS", "STATUS", "TOOLS", "CALLS")
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Yara AI", fontWeight = FontWeight.Bold, color = Pink) },
            actions = { IconButton(onClick = { nav("settings") }) { Icon(Icons.Filled.Settings, null) } }
        )
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 12.sp) }) }
        }
        when (tab) {
            0 -> ChatsTab(open)
            1 -> StatusTab(nav)
            2 -> ToolsTab(open, nav)
            else -> CallsTab(nav)
        }
    }
}

@Composable
fun ChatsTab(open: (String, String?) -> Unit) {
    val tick by Brain.tick.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        items(Chats.all) { c ->
            val last = remember(tick, c.id) { Brain.store.last(c.id) }
            Row(Modifier.fillMaxWidth().clickable { open(c.id, null) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(CircleShape)) { YaraAvatar(c.mood, false, Modifier.fillMaxSize()) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        last?.text?.replace("\n", " ") ?: c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp
                    )
                }
                if (last != null) Text(
                    SimpleDateFormat("hh:mm a", Locale.US).format(Date(last.ts)),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun StatusTab(nav: (String) -> Unit) {
    val ctx = LocalContext.current
    val mood by Brain.mood.collectAsState()
    val speaking by Speaker.speaking.collectAsState()
    val status by ApiPool.status.collectAsState()
    val offline by Brain.offline.collectAsState()
    val tick by Brain.tick.collectAsState()
    val mlMissing by Speaker.mlMissing.collectAsState()
    var wake by remember { mutableStateOf(Prefs.wake) }
    val mem = remember(tick) { Brain.store.memCount() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Box(Modifier.size(210.dp).clip(CircleShape)) { YaraAvatar(mood, speaking, Modifier.fillMaxSize()) }
            Text("${Emotes.emoji(mood)}  feeling $mood", Modifier.padding(top = 8.dp), fontSize = 16.sp)
            Spacer(Modifier.height(14.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("“Hey Yara” wake word", fontWeight = FontWeight.SemiBold)
                            Text("Say Hey Yara to wake her, Bye Yara to sleep", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Switch(checked = wake, onCheckedChange = {
                            wake = it; Prefs.wake = it
                            if (it) {
                                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == 0) WakeService.start(ctx)
                                else { wake = false; Prefs.wake = false; Toast.makeText(ctx, "Allow microphone first", Toast.LENGTH_SHORT).show() }
                            } else WakeService.stop(ctx)
                        })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    Text(if (offline) "🧠 Offline memory brain active" else "🌐 $status", fontWeight = FontWeight.SemiBold)
                    Text("$mem things saved on this phone for offline replies", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    if (!ApiPool.hasKeys()) Text("Add a Gemini / Groq key in Settings ⚙️", color = Pink, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).clickable { nav("settings") })
                    if (mlMissing) Text("Malayalam voice not installed: Settings → Google TTS → install Malayalam", color = Pink, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun ToolCard(emoji: String, title: String, desc: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Mine), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun ToolsTab(open: (String, String?) -> Unit, nav: (String) -> Unit) {
    val ctx = LocalContext.current
    var harvest by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
        item {
            ToolCard("🎤", "Interview preparation", "Mock interview with feedback and scores") {
                open("career", "Let's start a mock interview for my target role. Ask me the first question.")
            }
            ToolCard("📄", "CV builder", "AI-written CV → export as PDF") { nav("cv") }
            ToolCard("💼", "Job finder", "LinkedIn job search + role matching") { nav("jobs") }
            ToolCard("📊", "Data analytics", "Import a CSV and get insights") { nav("csv") }
            ToolCard("👀", "Look at my screen", "Yara sees your screen, detects and answers") {
                if (YaraAccessibility.instance == null) {
                    Toast.makeText(ctx, "Turn ON 'Yara Screen Vision' accessibility, then come back", Toast.LENGTH_LONG).show()
                    ctx.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else {
                    Toast.makeText(ctx, "Switch to any app - I'll look in 5 seconds 👀", Toast.LENGTH_LONG).show()
                    Brain.scope.launch { delay(5000); Brain.send("assistant", "look at my screen and tell me what you see", speak = true) }
                }
            }
            ToolCard("🧠", "Study mode: save knowledge offline", if (harvest.isBlank()) "Uses spare API quota to store answers on your phone" else harvest) {
                if (!ApiPool.hasKeys()) { Toast.makeText(ctx, "Add an API key first", Toast.LENGTH_SHORT).show(); return@ToolCard }
                Brain.scope.launch {
                    var total = 0
                    for ((i, t) in Brain.harvestTopics.withIndex()) {
                        harvest = "Learning ${i + 1}/${Brain.harvestTopics.size}…  saved $total"
                        total += Brain.harvest(t)
                    }
                    harvest = "Done ✅ saved $total new answers"
                    Brain.tick.value = Brain.tick.value + 1
                }
            }
        }
    }
}

@Composable
fun CallsTab(nav: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ToolCard("📞", "Voice call with Yara", "Hands-free talking with live emotions") { nav("call") }
    }
}

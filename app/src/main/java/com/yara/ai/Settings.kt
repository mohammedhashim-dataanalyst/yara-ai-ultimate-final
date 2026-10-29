@file:OptIn(ExperimentalMaterial3Api::class)

package com.yara.ai

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Pink)
            content()
        }
    }
}

@Composable
private fun SlotEditor(n: Int) {
    var prov by remember { mutableStateOf(Prefs.provider(n)) }
    var key by remember { mutableStateOf(Prefs.key(n)) }
    var model by remember { mutableStateOf(Prefs.s("model$n")) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Key ${n + 1}", fontWeight = FontWeight.SemiBold)
            FilterChip(selected = prov == "gemini", onClick = { prov = "gemini"; Prefs.put("prov$n", "gemini") }, label = { Text("Gemini") })
            FilterChip(selected = prov == "groq", onClick = { prov = "groq"; Prefs.put("prov$n", "groq") }, label = { Text("Groq") })
        }
        OutlinedTextField(key, { key = it; Prefs.put("key$n", it.trim()) }, Modifier.fillMaxWidth(), label = { Text("API key") },
            singleLine = true, visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(model, { model = it; Prefs.put("model$n", it.trim()) }, Modifier.fillMaxWidth(),
            label = { Text("Model (blank = ${Prefs.defaultModel(prov)})") }, singleLine = true)
        val cd = ApiPool.coolLeft(n)
        Text("Used today: ${Prefs.usage(n)}" + if (cd > 0) "  •  paused ${cd}s" else "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
fun SettingsScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(Prefs.mode) }
    var primary by remember { mutableStateOf(Prefs.primary) }
    var name by remember { mutableStateOf(Prefs.userName) }
    var romance by remember { mutableStateOf(Prefs.romance.toFloat()) }
    var humor by remember { mutableStateOf(Prefs.humor.toFloat()) }
    var serious by remember { mutableStateOf(Prefs.serious.toFloat()) }
    var lang by remember { mutableStateOf(Prefs.language) }
    var sl by remember { mutableStateOf(Prefs.speechLang) }
    var voice by remember { mutableStateOf(Prefs.voiceReply) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = back) { Icon(Icons.Filled.ArrowBack, null) } })

        Section("API keys & models (free Gemini / Groq)") {
            Text("Add up to 4 keys. Yara rotates between them and moves to the next one automatically when a key hits its limit.", fontSize = 12.sp)
            for (i in 0..3) SlotEditor(i)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = mode == "auto", onClick = { mode = "auto"; Prefs.mode = "auto" }, label = { Text("Auto (balance)") })
                FilterChip(selected = mode == "manual", onClick = { mode = "manual"; Prefs.mode = "manual" }, label = { Text("Manual main") })
            }
            if (mode == "manual") Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Main:")
                for (i in 0..3) FilterChip(selected = primary == i, onClick = { primary = i; Prefs.primary = i }, label = { Text("${i + 1}") })
            }
        }

        Section("You & Yara's personality") {
            OutlinedTextField(name, { name = it; Prefs.userName = it }, Modifier.fillMaxWidth(), label = { Text("What should Yara call you?") }, singleLine = true)
            Text("Romantic ${romance.toInt()}", fontSize = 12.sp)
            Slider(romance, { romance = it; Prefs.romance = it.toInt() }, valueRange = 0f..100f)
            Text("Humour ${humor.toInt()}", fontSize = 12.sp)
            Slider(humor, { humor = it; Prefs.humor = it.toInt() }, valueRange = 0f..100f)
            Text("Seriousness ${serious.toInt()}", fontSize = 12.sp)
            Slider(serious, { serious = it; Prefs.serious = it.toInt() }, valueRange = 0f..100f)
            Text("Talking language", fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((k, l) in listOf("mixed" to "Mixed", "english" to "English", "malayalam" to "മലയാളം"))
                    FilterChip(selected = lang == k, onClick = { lang = k; Prefs.language = k }, label = { Text(l) })
            }
        }

        Section("Voice") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Yara speaks her replies", Modifier.weight(1f))
                Switch(voice, { voice = it; Prefs.voiceReply = it })
            }
            Text("I speak in (microphone language)", fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = sl == "en-IN", onClick = { sl = "en-IN"; Prefs.speechLang = "en-IN" }, label = { Text("English / Manglish") })
                FilterChip(selected = sl == "ml-IN", onClick = { sl = "ml-IN"; Prefs.speechLang = "ml-IN" }, label = { Text("മലയാളം") })
            }
        }

        Section("Memory & permissions") {
            val n = remember { Brain.store.memCount() }
            Text("$n answers saved on this phone (used when API limits are reached)", fontSize = 12.sp)
            OutlinedButton(onClick = { Brain.store.clearMemory(); Toast.makeText(ctx, "Memory cleared", Toast.LENGTH_SHORT).show() }) { Text("Clear offline memory") }
            Button(onClick = { ctx.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Enable Screen Vision (Accessibility)") }
            Text("Tip: for the wake word to survive in the background, set Yara's battery usage to 'Unrestricted'.", fontSize = 11.sp)
        }
        Spacer(Modifier.height(30.dp))
    }
}

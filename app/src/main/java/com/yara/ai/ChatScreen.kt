@file:OptIn(ExperimentalMaterial3Api::class)

package com.yara.ai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(chatId: String, starter: String?, consume: () -> Unit, back: () -> Unit, call: () -> Unit) {
    val def = Chats.get(chatId)
    val ctx = LocalContext.current
    val tick by Brain.tick.collectAsState()
    val typing by Brain.typing.collectAsState()
    val mood by Brain.mood.collectAsState()
    val offline by Brain.offline.collectAsState()
    val speaking by Speaker.speaking.collectAsState()
    val msgs = remember(tick, chatId) { Brain.store.msgs(chatId, 200) }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var voiceOn by remember { mutableStateOf(Prefs.voiceReply) }

    val listener = remember {
        Listener(ctx,
            { input = it },
            { t -> listening = false; if (t.isNotBlank()) { input = ""; Brain.send(chatId, t, speak = true) } },
            { listening = false })
    }
    DisposableEffect(Unit) { onDispose { listener.destroy() } }

    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        if (g) { listening = true; listener.start() }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bmp = try { ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }
            if (bmp != null) {
                val q = input.ifBlank { "What do you see in this? Analyze it and help me." }
                Brain.send(chatId, q, image = bmp, shown = "📷 " + input.ifBlank { "Photo" })
                input = ""
            } else Toast.makeText(ctx, "Couldn't open image", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(starter) { if (starter != null) { Brain.send(chatId, starter); consume() } }
    LaunchedEffect(msgs.size, typing) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size) }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E1218))) {
        TopAppBar(
            navigationIcon = { IconButton(onClick = back) { Icon(Icons.Filled.ArrowBack, null) } },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape)) { YaraAvatar(if (chatId == "yara") mood else def.mood, speaking, Modifier.fillMaxSize()) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(def.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when { typing -> "typing…"; speaking -> "speaking…"; offline -> "offline memory brain"; else -> "online ${Emotes.emoji(mood)}" },
                            fontSize = 11.sp, color = Pink
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = { voiceOn = !voiceOn; Prefs.voiceReply = voiceOn; if (!voiceOn) Speaker.stop() }) {
                    Icon(if (voiceOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, null)
                }
                IconButton(onClick = call) { Icon(Icons.Filled.Call, null) }
                IconButton(onClick = { Brain.store.clearChat(chatId); Brain.tick.value = Brain.tick.value + 1 }) { Icon(Icons.Filled.Delete, null) }
            }
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp), state = listState, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (msgs.isEmpty()) item {
                Text("Say hi to Yara 💕", Modifier.fillMaxWidth().padding(40.dp), color = Color.Gray, fontSize = 14.sp)
            }
            items(msgs, key = { it.id }) { Bubble(it) }
            item { if (typing) Bubble(Msg(-1, chatId, "yara", "typing…", "neutral", System.currentTimeMillis())) else Spacer(Modifier.size(2.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text(if (listening) "Listening… 🎙️" else "Message", fontSize = 14.sp) },
                shape = RoundedCornerShape(26.dp), maxLines = 4,
                leadingIcon = { IconButton(onClick = { pick.launch("image/*") }) { Icon(Icons.Filled.AttachFile, null) } },
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color(0xFF1B222C), unfocusedContainerColor = Color(0xFF1B222C))
            )
            Spacer(Modifier.width(6.dp))
            Surface(shape = CircleShape, color = Pink, modifier = Modifier.size(48.dp)) {
                IconButton(onClick = {
                    if (input.isNotBlank()) { val t = input; input = ""; Brain.send(chatId, t) }
                    else if (listening) { listener.stop() }
                    else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        Speaker.stop(); listening = true; listener.start()
                    } else micPerm.launch(Manifest.permission.RECORD_AUDIO)
                }) { Icon(if (input.isNotBlank()) Icons.Filled.Send else Icons.Filled.Mic, null, tint = Color.White) }
            }
        }
    }
}

@Composable
private fun Bubble(m: Msg) {
    val mine = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mine) Mine else Theirs,
            shape = RoundedCornerShape(16.dp, 16.dp, if (mine) 4.dp else 16.dp, if (mine) 16.dp else 4.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text(m.text, fontSize = 15.sp)
                if (m.id >= 0) Text(
                    SimpleDateFormat("hh:mm a", Locale.US).format(Date(m.ts)) + if (mine) "  ✓✓" else "  " + Emotes.emoji(m.mood),
                    fontSize = 10.sp, color = Color(0xFFB0B8C4), modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

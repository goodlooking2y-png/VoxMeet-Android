package com.rakib.voxmeet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF100D20)
private val Panel = Color(0xFF211A35)
private val Panel2 = Color(0xFF2B2144)
private val Pink = Color(0xFFFF4D91)
private val Violet = Color(0xFF9167FF)
private val Aqua = Color(0xFF53E0D0)
private val Muted = Color(0xFFB9B2C9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VoxMeetApp() }
    }
}

data class VoiceRoom(val title: String, val topic: String, val people: Int, val host: String, val tag: String)

@Composable
fun VoxMeetApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeRoom by remember { mutableStateOf<VoiceRoom?>(null) }
    var micOn by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    val rooms = remember {
        mutableStateListOf(
            VoiceRoom("আজকের আড্ডা", "বন্ধুত্ব • গল্প • পরিচয়", 128, "Rakib", "বাংলা"),
            VoiceRoom("গান ও কবিতা", "গান, কবিতা এবং ভালো লাগা", 86, "Sami", "Music"),
            VoiceRoom("প্রবাস জীবনের গল্প", "সৌদি আরব ও প্রবাসের অভিজ্ঞতা", 264, "Nayeem", "Community"),
            VoiceRoom("রাত জাগা বন্ধুরা", "চলো একটু কথা বলি", 57, "Arif", "Chill")
        )
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Violet, secondary = Pink, background = Ink, surface = Panel)) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            if (activeRoom != null) {
                RoomScreen(activeRoom!!, micOn, { micOn = !micOn }, { activeRoom = null; micOn = false })
            } else {
                Scaffold(
                    containerColor = Ink,
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = { showCreate = true },
                            containerColor = Brush.horizontalGradient(listOf(Pink, Violet)).let { Violet },
                            contentColor = Color.White,
                            icon = { Icon(Icons.Default.VideoCall, null) },
                            text = { Text("Go Live", fontWeight = FontWeight.Bold) }
                        )
                    },
                    bottomBar = {
                        NavigationBar(containerColor = Color(0xFF191428), contentColor = Color.White) {
                            NavigationBarItem(selectedTab == 0, { selectedTab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                            NavigationBarItem(selectedTab == 1, { selectedTab = 1 }, icon = { Icon(Icons.Default.Explore, null) }, label = { Text("Discover") })
                            NavigationBarItem(selectedTab == 2, { selectedTab = 2 }, icon = { Icon(Icons.Default.MailOutline, null) }, label = { Text("Messages") })
                            NavigationBarItem(selectedTab == 3, { selectedTab = 3 }, icon = { Icon(Icons.Default.Person, null) }, label = { Text("Profile") })
                        }
                    }
                ) { padding ->
                    when (selectedTab) {
                        0 -> HomeScreen(rooms, Modifier.padding(padding), { activeRoom = it }, { showCreate = true })
                        1 -> PlaceholderScreen("Discover", "নতুন Live Room খুঁজে দেখো", Modifier.padding(padding))
                        2 -> PlaceholderScreen("Messages", "তোমার মেসেজ এখানে দেখা যাবে", Modifier.padding(padding))
                        else -> PlaceholderScreen("My Profile", "প্রোফাইল ও Wallet", Modifier.padding(padding))
                    }
                }
                if (showCreate) CreateRoomDialog(
                    onDismiss = { showCreate = false },
                    onCreate = { name ->
                        val room = VoiceRoom(name, "তোমার নতুন লাইভ রুম", 1, "You", "New")
                        rooms.add(0, room); showCreate = false; activeRoom = room
                    }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(rooms: List<VoiceRoom>, modifier: Modifier = Modifier, onJoin: (VoiceRoom) -> Unit, onGoLive: () -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VOXMEET", color = Pink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                    Text("তোমার Live দুনিয়া", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                }
                WalletPill()
                Spacer(Modifier.width(9.dp))
                Box(Modifier.size(42.dp).clip(CircleShape).background(Panel2), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.NotificationsNone, null, tint = Color.White)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF773B91), Color(0xFF36245F), Color(0xFF222044))))
                    .clickable { onGoLive() }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("তোমার মুহূর্ত শেয়ার করো", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("এখনই Live শুরু করো এবং বন্ধুদের সঙ্গে যুক্ত হও", color = Color(0xFFE5D9F4), fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("GO LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Pink).padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
                Icon(Icons.Default.VideoCall, null, tint = Color.White, modifier = Modifier.size(58.dp))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Live Now", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("সব দেখুন  ›", color = Pink, fontSize = 12.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                StoryAvatar("R", "Rakib", true, Pink)
                StoryAvatar("S", "Sami", true, Violet)
                StoryAvatar("N", "Nayeem", true, Aqua)
                StoryAvatar("A", "Arif", false, Color(0xFFFFB45C))
                StoryAvatar("M", "Mitu", true, Pink)
                StoryAvatar("+", "তুমি", false, Violet)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Discover Rooms", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Default.Tune, null, tint = Muted)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("For You", "বাংলা", "Music", "Chat", "PK").forEachIndexed { i, label ->
                    Text(label, color = if (i == 0) Color.White else Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(if (i == 0) Violet else Panel).padding(horizontal = 16.dp, vertical = 9.dp))
                }
            }
        }
        items(rooms) { room -> LiveRoomCard(room) { onJoin(room) } }
        item { Text("VoxMeet • Connect, share and shine", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
    }
}

@Composable
fun WalletPill() {
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Panel).padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Diamond, null, tint = Color(0xFFFFC857), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text("120", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.AddCircle, null, tint = Pink, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun StoryAvatar(initial: String, name: String, live: Boolean, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(62.dp).clip(CircleShape).background(Brush.linearGradient(listOf(color, Violet))).padding(3.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) {
            Text(initial, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            if (live) Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(8.dp)).background(Pink).padding(horizontal = 7.dp, vertical = 2.dp))
        }
        Text(name, color = Muted, fontSize = 11.sp)
    }
}

@Composable
fun LiveRoomCard(room: VoiceRoom, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(Color(0xFF553C7A), Color(0xFF252047), Color(0xFF9A477D))))) {
                Icon(Icons.Default.Groups, null, tint = Color.White.copy(alpha = .18f), modifier = Modifier.align(Alignment.Center).size(78.dp))
                Text("● LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(9.dp).clip(RoundedCornerShape(8.dp)).background(Pink).padding(horizontal = 8.dp, vertical = 5.dp))
                Text("👥 ${room.people}", color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(9.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .35f)).padding(horizontal = 8.dp, vertical = 5.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(room.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Host: ${room.host}  •  ${room.tag}", color = Muted, fontSize = 11.sp)
                }
                Icon(Icons.Default.FavoriteBorder, null, tint = Pink)
            }
            Text(room.topic, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun RoomScreen(room: VoiceRoom, micOn: Boolean, onMic: () -> Unit, onLeave: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF32204E), Ink))).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("←  Back", color = Color.White, modifier = Modifier.clickable { onLeave() })
            Text("● LIVE ROOM", color = Pink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.height(48.dp))
        Text(room.title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text(room.topic, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(42.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Avatar("R", "Rakib", true); Avatar("S", "Sami", false); Avatar("A", "Arif", false)
        }
        Spacer(Modifier.height(28.dp))
        Text("${room.people} জন রুমে আছেন", color = Aqua, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(if (micOn) "মাইক UI-তে চালু" else "তুমি শ্রোতা হিসেবে আছো", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMic, modifier = Modifier.size(66.dp).background(if (micOn) Aqua else Violet, CircleShape)) {
                Icon(if (micOn) Icons.Default.Mic else Icons.Default.MicOff, null, tint = Ink, modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = {}, modifier = Modifier.size(54.dp).background(Panel2, CircleShape)) { Icon(Icons.Default.CardGiftcard, null, tint = Color.White) }
            IconButton(onClick = {}, modifier = Modifier.size(54.dp).background(Panel2, CircleShape)) { Icon(Icons.Default.Chat, null, tint = Color.White) }
        }
        Spacer(Modifier.height(12.dp))
        Text("মাইক / Gift / Chat — ডেমো UI", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Pink)) { Text("Leave Room") }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
fun Avatar(initial: String, name: String, speaking: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(66.dp).background(if (speaking) Violet else Panel2, CircleShape), contentAlignment = Alignment.Center) {
            Text(initial, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(7.dp))
        Text(name, color = Color.White, fontSize = 12.sp)
        if (speaking) Text("Host", color = Aqua, fontSize = 10.sp)
    }
}

@Composable
fun CreateRoomDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("নতুন Live Room", color = Color.White) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("রুমের নাম") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim()) }) { Text("তৈরি করো") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

@Composable
fun PlaceholderScreen(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.AutoAwesome, null, tint = Pink, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text("এই অংশটি পরবর্তী ধাপে তৈরি হবে", color = Violet, fontSize = 12.sp)
    }
}

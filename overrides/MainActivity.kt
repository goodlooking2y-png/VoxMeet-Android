package com.rakib.voxmeet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import java.util.UUID
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
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
    var audioConnected by remember { mutableStateOf(false) }
    var audioStatus by remember { mutableStateOf("রুমে ঢুকলে অডিও সংযোগ হবে") }
    var showCreate by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val audioManager = remember { LiveKitAudioManager(context) }
    val scope = rememberCoroutineScope()

    val rooms = remember {
        mutableStateListOf(
            VoiceRoom("আজকের আড্ডা", "বন্ধুত্ব • গল্প • পরিচয়", 128, "Rakib", "বাংলা"),
            VoiceRoom("গান ও কবিতা", "গান, কবিতা এবং ভালো লাগা", 86, "Sami", "Music"),
            VoiceRoom("প্রবাস জীবনের গল্প", "সৌদি আরব ও প্রবাসের অভিজ্ঞতা", 264, "Nayeem", "Community"),
            VoiceRoom("রাত জাগা বন্ধুরা", "চলো একটু কথা বলি", 57, "Arif", "Chill")
        )
    }

    val connectToRoom: (VoiceRoom) -> Unit = { selectedRoom ->
        scope.launch {
            try {
                audioStatus = "LiveKit-এ সংযোগ হচ্ছে..."
                val stableRoomName = "voxmeet-room-${selectedRoom.title.hashCode().toUInt().toString(16)}"
                val participantName = "Guest-${UUID.randomUUID().toString().take(8)}"
                audioManager.joinRoom(stableRoomName, participantName)
                audioManager.setMicrophoneEnabled(false)
                audioConnected = true
                audioStatus = "অডিও সংযুক্ত • মাইক বন্ধ"
            } catch (error: Exception) {
                audioConnected = false
                audioStatus = "অডিও সংযোগ হয়নি: ${error.message ?: "আবার চেষ্টা করুন"}"
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val selectedRoom = activeRoom
        if (granted && selectedRoom != null) {
            connectToRoom(selectedRoom)
        } else if (!granted) {
            audioStatus = "অডিও চালাতে মাইক্রোফোন অনুমতি প্রয়োজন"
        }
    }

    LaunchedEffect(activeRoom) {
        if (activeRoom != null) {
            micOn = false
            audioConnected = false
            audioStatus = "মাইক্রোফোন অনুমতি যাচাই হচ্ছে..."
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                connectToRoom(activeRoom!!)
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun leaveActiveRoom() {
        scope.launch {
            try {
                audioManager.leaveRoom()
            } catch (_: Exception) {
                // Keep the UI responsive even if the network has already disconnected.
            }
            audioConnected = false
            micOn = false
            audioStatus = "রুম থেকে বের হয়েছেন"
            activeRoom = null
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Violet, secondary = Pink, background = Ink, surface = Panel)) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            if (activeRoom != null) {
                RoomScreen(
                    activeRoom!!,
                    micOn,
                    audioConnected,
                    audioStatus,
                    onMic = {
                        if (!audioConnected) {
                            audioStatus = "আগে অডিও সংযোগ সম্পন্ন হতে দিন"
                        } else {
                            scope.launch {
                                try {
                                    val nextState = !micOn
                                    audioManager.setMicrophoneEnabled(nextState)
                                    micOn = nextState
                                    audioStatus = if (nextState) "তোমার মাইক চালু আছে" else "তোমার মাইক বন্ধ"
                                } catch (error: Exception) {
                                    audioStatus = "মাইক পরিবর্তন হয়নি: ${error.message ?: "আবার চেষ্টা করুন"}"
                                }
                            }
                        }
                    },
                    onLeave = { leaveActiveRoom() }
                )
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
    var selectedFeed by remember { mutableStateOf("Popular") }
    var selectedCategory by remember { mutableStateOf("For You") }
    val categories = listOf("For You", "বাংলা", "Music", "Chat", "PK")
    val visibleRooms = when (selectedCategory) {
        "বাংলা" -> rooms.filter { it.tag == "বাংলা" || it.topic.contains("বাংলা") || it.topic.contains("প্রবাস") }
        "Music" -> rooms.filter { it.tag == "Music" || it.topic.contains("গান") || it.title.contains("গান") }
        "Chat" -> rooms.filter { it.topic.contains("গল্প") || it.topic.contains("কথা") || it.tag == "Chill" }
        "PK" -> rooms.filter { it.tag == "PK" }
        else -> rooms
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VOXMEET", color = Pink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                    Text("Live your moment", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
                WalletPill()
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Search, "Search", tint = Color.White)
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Default.NotificationsNone, "Notifications", tint = Color.White)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF8D3F9F), Color(0xFF57318C), Color(0xFF29204D))))
                    .clickable { onGoLive() }.padding(17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your stage is waiting", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Go live, meet new people and share your vibe", color = Color(0xFFE9DDF5), fontSize = 11.sp)
                    Text("＋  START LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Pink).padding(horizontal = 13.dp, vertical = 8.dp))
                }
                Box(Modifier.size(66.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Videocam, null, tint = Color.White, modifier = Modifier.size(39.dp))
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Live", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("See all  ›", color = Pink, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                StoryAvatar("R", "Rakib", true, Pink)
                StoryAvatar("S", "Sami", true, Violet)
                StoryAvatar("N", "Nayeem", true, Aqua)
                StoryAvatar("A", "Arif", false, Color(0xFFFFB45C))
                StoryAvatar("M", "Mitu", true, Pink)
                StoryAvatar("+", "Go live", false, Violet)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Discover", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Default.Tune, null, tint = Muted)
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                categories.forEach { category ->
                    val active = selectedCategory == category
                    Text(category, color = if (active) Color.White else Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(22.dp))
                            .background(if (active) Violet else Panel).clickable { selectedCategory = category }
                            .padding(horizontal = 15.dp, vertical = 9.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf("Popular", "Following", "Nearby").forEach { feed ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { selectedFeed = feed }) {
                        Text(feed, color = if (selectedFeed == feed) Color.White else Muted, fontSize = 13.sp,
                            fontWeight = if (selectedFeed == feed) FontWeight.Bold else FontWeight.Normal)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.width(if (selectedFeed == feed) 24.dp else 8.dp).height(3.dp)
                            .clip(RoundedCornerShape(3.dp)).background(if (selectedFeed == feed) Pink else Color.Transparent))
                    }
                }
            }
        }
        if (visibleRooms.isEmpty()) {
            item {
                Text("এই ক্যাটাগরিতে এখনো কোনো Live Room নেই।", color = Muted, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp))
            }
        } else {
            items(visibleRooms) { room -> LiveRoomCard(room) { onJoin(room) } }
        }
        item { Text("VoxMeet • Connect, share and shine", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }
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
fun RoomScreen(room: VoiceRoom, micOn: Boolean, audioConnected: Boolean, audioStatus: String, onMic: () -> Unit, onLeave: () -> Unit) {
    val seats = remember {
        mutableStateListOf<String?>("Rakib", "Sami", "Arif", null, null, null, null, null, null, null, null, null)
    }
    var notice by remember { mutableStateOf("একটি খালি সিটে চাপ দিয়ে যোগ দেওয়ার অনুরোধ পাঠাও") }
    var showGift by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var chatText by remember { mutableStateOf("") }
    var lastMessage by remember { mutableStateOf("রুমে সবাইকে স্বাগতম!") }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF32204E), Ink))).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("←  Back", color = Color.White, modifier = Modifier.clickable { onLeave() })
            Text("● LIVE ROOM", color = Pink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("👥 ${room.people}", color = Color.White, fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(room.title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        Text("Host: ${room.host}  •  ${room.topic}", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("VOICE SEATS", color = Aqua, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
            Text("12টি অডিও সিট", color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(3) { col ->
                        val index = row * 3 + col
                        val occupant = seats[index]
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                                .background(if (occupant == null) Panel.copy(alpha = .75f) else Panel2)
                                .clickable {
                                    if (occupant == null) {
                                        seats[index] = "You"
                                        notice = "সিট ${index + 1}-এ যোগ দিয়েছেন (ডেমো)"
                                    } else {
                                        notice = if (index == 0) "Host: ${occupant}" else "${occupant} মাইকে আছেন"
                                    }
                                }.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape)
                                    .background(if (occupant == null) Color(0xFF39304F) else if (index == 0) Violet else Color(0xFF3B345B)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (occupant == null) {
                                    Icon(Icons.Default.Add, null, tint = Aqua, modifier = Modifier.size(24.dp))
                                } else {
                                    Text(occupant.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(occupant ?: "খালি সিট", color = if (occupant == null) Muted else Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (index == 0) "HOST" else if (occupant == null) "TAP TO JOIN" else "GUEST", color = if (index == 0) Aqua else Muted, fontSize = 8.sp)
                        }
                    }
                }
            }
        }
        Text(notice, color = Aqua, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Text(audioStatus, color = if (audioConnected) Aqua else Color(0xFFFFC857), fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = .18f)).padding(10.dp)) {
            Text("LIVE CHAT", color = Aqua, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(lastMessage, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = onMic, modifier = Modifier.size(52.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (micOn) Aqua else Violet)) {
                Icon(if (micOn) Icons.Default.Mic else Icons.Default.MicOff, null, tint = Ink)
            }
            FilledTonalIconButton(onClick = { showChat = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Chat, "Chat")
            }
            FilledTonalIconButton(onClick = { showGift = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.CardGiftcard, "Gift")
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onLeave, colors = ButtonDefaults.buttonColors(containerColor = Pink)) { Text("Leave") }
        }
        Text(if (micOn) "মাইক UI-তে অন" else "মাইক UI-তে অফ", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
        Text("ডেমো: এখনো বাস্তব অডিও/চ্যাট সার্ভারের সঙ্গে যুক্ত নয়", color = Color(0xFFFFC857), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
    }

    if (showGift) {
        AlertDialog(
            onDismissRequest = { showGift = false },
            containerColor = Panel,
            title = { Text("Send a Gift", color = Color.White) },
            text = { Text("🌹 Rose     ❤️ Heart     ⭐ Star\n\nগিফট পাঠানোর ফিচারটি পরবর্তী ধাপে সার্ভারের সঙ্গে যুক্ত হবে।", color = Muted) },
            confirmButton = { TextButton(onClick = { showGift = false; notice = "Gift demo নির্বাচন করা হয়েছে" }) { Text("ঠিক আছে") } }
        )
    }
    if (showChat) {
        AlertDialog(
            onDismissRequest = { showChat = false },
            containerColor = Panel,
            title = { Text("Live Chat", color = Color.White) },
            text = {
                OutlinedTextField(value = chatText, onValueChange = { chatText = it }, label = { Text("মেসেজ লিখুন") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (chatText.isNotBlank()) lastMessage = "You: ${chatText.trim()}"
                    chatText = ""; showChat = false
                }) { Text("পাঠান") }
            },
            dismissButton = { TextButton(onClick = { showChat = false }) { Text("বন্ধ") } }
        )
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

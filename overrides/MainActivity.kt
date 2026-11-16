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
        setContent { VoxMeetAuthGate { VoxMeetApp() } }
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
    var showWallet by remember { mutableStateOf(false) }
    var walletCoins by remember { mutableIntStateOf(120) }
    val context = LocalContext.current
    val audioManager = remember { LiveKitAudioManager(context) }
    val roomParticipants by audioManager.participants.collectAsState()
    val roomMessages by audioManager.messages.collectAsState()
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
                    roomParticipants,
                    roomMessages,
                    onSendChat = { message ->
                        scope.launch {
                            try {
                                audioManager.sendChat(message)
                            } catch (error: Exception) {
                                audioStatus = "মেসেজ পাঠানো যায়নি: ${error.message ?: "আবার চেষ্টা করুন"}"
                            }
                        }
                    },
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
                        0 -> HomeScreen(rooms, Modifier.padding(padding), { activeRoom = it }, { showCreate = true }, { showWallet = true }, walletCoins)
                        1 -> DiscoverScreen(rooms, Modifier.padding(padding), { activeRoom = it })
                        2 -> MessagesScreen(Modifier.padding(padding))
                        else -> ProfileScreen(Modifier.padding(padding))
                    }
                }
                if (showWallet) WalletDialog(coins = walletCoins, onDismiss = { showWallet = false })
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
fun HomeScreen(rooms: List<VoiceRoom>, modifier: Modifier = Modifier, onJoin: (VoiceRoom) -> Unit, onGoLive: () -> Unit, onWallet: () -> Unit, walletCoins: Int) {
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
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("VOXMEET", color = Pink, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                    Text("Live your moment", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                    Text("তোমার কমিউনিটি, তোমার গল্প", color = Muted, fontSize = 10.sp)
                }
                WalletPill(walletCoins, onWallet)
                Spacer(Modifier.width(5.dp))
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
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("YOUR VOICE, YOUR STAGE", color = Color(0xFFFFC8E0), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)
                    Text("Your stage is waiting", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Go live, meet new people and share your vibe", color = Color(0xFFE9DDF5), fontSize = 11.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("＋  START LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Pink).padding(horizontal = 13.dp, vertical = 8.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("শুরু করো", color = Color(0xFFE9DDF5), fontSize = 10.sp)
                    }
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
fun WalletPill(walletCoins: Int, onWallet: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Panel).clickable(onClick = onWallet).padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Diamond, null, tint = Color(0xFFFFC857), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(walletCoins.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.AddCircle, null, tint = Pink, modifier = Modifier.size(16.dp))
    }
}


@Composable
fun WalletDialog(coins: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("My Wallet", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("বর্তমান ব্যালেন্স: $coins ডেমো কয়েন", color = Color.White)
                Text("বাস্তব কয়েন কেনা এখনো চালু নয়। নিরাপদ সার্ভার, পেমেন্ট যাচাই ও স্টোর নীতিমালা নিশ্চিত হলে পেমেন্ট যুক্ত হবে।", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("ঠিক আছে") } }
    )
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
            Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF713E91), Color(0xFF282044), Color(0xFFB34D83))))) {
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .16f), Color.Transparent, Color.Black.copy(alpha = .12f)))))
                Icon(Icons.Default.Groups, null, tint = Color.White.copy(alpha = .20f), modifier = Modifier.align(Alignment.Center).size(88.dp))
                Text("● LIVE NOW", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(8.dp)).background(Pink).padding(horizontal = 9.dp, vertical = 6.dp))
                Row(Modifier.align(Alignment.TopEnd).padding(9.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = .30f)).padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.People, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${room.people}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(room.tag.uppercase(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(7.dp))
                        .background(Color.Black.copy(alpha = .28f)).padding(horizontal = 8.dp, vertical = 5.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Pink, Violet))), contentAlignment = Alignment.Center) {
                    Text(room.host.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(room.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Host: ${room.host}", color = Muted, fontSize = 10.sp)
                }
                Icon(Icons.Default.FavoriteBorder, null, tint = Pink)
            }
            Text(room.topic, color = Muted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(room.tag, color = Aqua, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Aqua.copy(alpha = .10f)).padding(horizontal = 8.dp, vertical = 5.dp))
                Text("রুমে যোগ দিন  ›", color = Pink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RoomScreen(room: VoiceRoom, micOn: Boolean, audioConnected: Boolean, audioStatus: String, participants: List<RoomParticipant>, messages: List<String>, onSendChat: (String) -> Unit, onMic: () -> Unit, onLeave: () -> Unit) {
    var notice by remember { mutableStateOf("LiveKit-এর সদস্য তালিকা") }
    var showGift by remember { mutableStateOf(false) }
    var showPk by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var chatText by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF32204E), Ink))).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("←  Back", color = Color.White, modifier = Modifier.clickable { onLeave() })
            Text("● LIVE ROOM", color = Pink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("👥 ${participants.size}", color = Color.White, fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(room.title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        Text("Host: ${room.host}  •  ${room.topic}", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("VOICE SEATS", color = Aqua, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
            Text("${participants.size}/12 সদস্য", color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(3) { col ->
                        val index = row * 3 + col
                        val occupant = participants.getOrNull(index)
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                                .background(if (occupant == null) Panel.copy(alpha = .75f) else Panel2)
                                .clickable {
                                    notice = when { occupant == null -> "এই সিটটি খালি"; occupant.isLocal -> "এটি আপনার সিট"; else -> "সদস্য: ${occupant.name}" }
                                }.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape)
                                    .background(if (occupant == null) Color(0xFF39304F) else if (occupant.isLocal) Violet else Color(0xFF3B345B)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (occupant == null) {
                                    Icon(Icons.Default.Add, null, tint = Aqua, modifier = Modifier.size(24.dp))
                                } else {
                                    Text(occupant.name.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(occupant?.name ?: "খালি সিট", color = if (occupant == null) Muted else Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(when { occupant == null -> "TAP TO JOIN"; occupant.isLocal -> "YOU"; else -> "GUEST" }, color = if (occupant?.isLocal == true) Aqua else Muted, fontSize = 8.sp)
                        }
                    }
                }
            }
        }
        Text(notice, color = Aqua, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Text(audioStatus, color = if (audioConnected) Aqua else Color(0xFFFFC857), fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = .18f)).padding(10.dp)) {
            Text("LIVE CHAT", color = Aqua, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(messages.takeLast(2).joinToString("\\n").ifBlank { "এখনো কোনো মেসেজ নেই" }, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
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
            FilledTonalIconButton(onClick = { showPk = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Bolt, "PK")
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onLeave, colors = ButtonDefaults.buttonColors(containerColor = Pink)) { Text("Leave") }
        }
        Text(if (micOn) "মাইক চালু আছে" else "মাইক বন্ধ আছে", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
        Text("রিয়েল-টাইম অডিও ও চ্যাট: LiveKit • Gift/PK: কমিউনিটি ইভেন্ট ডেমো", color = Aqua, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
    }

    if (showGift) {
        var selectedGift by remember { mutableStateOf("🌹 Rose") }
        AlertDialog(
            onDismissRequest = { showGift = false }, containerColor = Panel,
            title = { Text("Send a Gift", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("রুমে পাঠানোর জন্য একটি গিফট বেছে নাও", color = Muted, fontSize = 12.sp)
                    listOf("🌹 Rose • 10", "❤️ Heart • 20", "⭐ Star • 50").forEach { gift ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (selectedGift == gift.substringBefore(" •")) Panel2 else Panel2.copy(alpha = .45f))
                            .clickable { selectedGift = gift.substringBefore(" •") }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(gift, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text("এটি এখন রুমে দৃশ্যমান ইভেন্ট পাঠাবে; কয়েন কাটা বা আয় জমা হবে না।", color = Color(0xFFFFC857), fontSize = 10.sp)
                }
            },
            confirmButton = { TextButton(onClick = { onSendChat("🎁 $selectedGift গিফট পাঠালেন"); showGift = false; notice = "Gift event রুমে পাঠানো হয়েছে" }) { Text("Send demo") } },
            dismissButton = { TextButton(onClick = { showGift = false }) { Text("বাতিল") } }
        )
    }
    if (showPk) {
        AlertDialog(
            onDismissRequest = { showPk = false }, containerColor = Panel,
            title = { Text("PK Challenge", color = Color.White) },
            text = { Text("অন্য একটি রুমের সঙ্গে ভয়েস PK চ্যালেঞ্জ শুরু করার ঘোষণা পাঠাও। প্রতিপক্ষ নির্বাচন, লাইভ স্কোর ও সময় গণনার জন্য সার্ভার-সাইড PK সেশন প্রয়োজন।", color = Muted) },
            confirmButton = { TextButton(onClick = { onSendChat("⚔️ PK Challenge শুরু করার আহ্বান!"); showPk = false; notice = "PK challenge invitation পাঠানো হয়েছে" }) { Text("চ্যালেঞ্জ পাঠাও") } },
            dismissButton = { TextButton(onClick = { showPk = false }) { Text("বন্ধ") } }
        )
    }
    if (showChat) {
        AlertDialog(
            onDismissRequest = { showChat = false },
            containerColor = Panel,
            title = { Text("Live Chat", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(messages.takeLast(8).joinToString("\\n").ifBlank { "এখনো কোনো মেসেজ নেই" }, color = Muted, fontSize = 12.sp)
                    OutlinedTextField(value = chatText, onValueChange = { chatText = it }, label = { Text("মেসেজ লিখুন") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val outgoing = chatText.trim()
                    if (outgoing.isNotEmpty()) onSendChat(outgoing)
                    chatText = ""
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


@Composable
fun DiscoverScreen(rooms: List<VoiceRoom>, modifier: Modifier = Modifier, onJoin: (VoiceRoom) -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("সব") }
    val filters = listOf("সব", "বাংলা", "Music", "Community", "Chill")
    val filtered = rooms.filter { room ->
        (query.isBlank() || room.title.contains(query, true) || room.topic.contains(query, true) || room.host.contains(query, true)) &&
            (category == "সব" || room.tag.equals(category, true) || room.topic.contains(category, true))
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("DISCOVER", color = Pink, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
            Text("নতুন মানুষ, নতুন গল্প", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("তোমার পছন্দের লাইভ কমিউনিটি খুঁজে নাও", color = Muted, fontSize = 12.sp)
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, placeholder = { Text("রুম বা বিষয় খুঁজুন", color = Muted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Aqua) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Violet, unfocusedBorderColor = Panel2,
                    cursorColor = Pink, focusedContainerColor = Panel, unfocusedContainerColor = Panel
                )
            )
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { filter ->
                    val selected = category == filter
                    Text(filter, color = if (selected) Color.White else Muted, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(22.dp))
                            .background(if (selected) Violet else Panel).clickable { category = filter }
                            .padding(horizontal = 15.dp, vertical = 9.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DiscoverStat("LIVE ROOMS", rooms.size.toString(), "●", Pink, Modifier.weight(1f))
                DiscoverStat("COMMUNITY", "24/7", "✦", Aqua, Modifier.weight(1f))
            }
        }
        if (filtered.isEmpty()) {
            item { Text("কোনো রুম পাওয়া যায়নি। অন্য নামে খুঁজে দেখুন।", color = Muted, modifier = Modifier.padding(18.dp)) }
        } else {
            items(filtered) { room -> LiveRoomCard(room) { onJoin(room) } }
        }
    }
}

@Composable
fun DiscoverStat(label: String, value: String, symbol: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(Panel).padding(14.dp)) {
        Text(symbol, color = tint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
fun MessagesScreen(modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var selectedMessage by remember { mutableStateOf<String?>(null) }
    val chats = listOf(
        Triple("Sami", "আজকের রুমে দেখা হবে!", "2m"),
        Triple("Nayeem", "প্রবাস জীবনের গল্পটা দারুণ ছিল", "18m"),
        Triple("Mitu", "ধন্যবাদ ❤️", "1h"),
        Triple("Arif", "নতুন লাইভ শুরু করেছি", "3h"),
        Triple("VoxMeet Team", "VoxMeet কমিউনিটিতে স্বাগতম!", "1d")
    )
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text("YOUR INBOX", color = Pink, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
        Text("Messages", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Text("বন্ধুদের সঙ্গে যোগাযোগ রাখো", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Pink, Violet))), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Forum, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("তোমার কমিউনিটি ইনবক্স", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("নতুন কথোপকথন এখানে দেখা যাবে", color = Muted, fontSize = 9.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
        Spacer(Modifier.height(13.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, placeholder = { Text("মেসেজ খুঁজুন", color = Muted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Aqua) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Violet, unfocusedBorderColor = Panel2,
                cursorColor = Pink, focusedContainerColor = Panel, unfocusedContainerColor = Panel
            )
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Recent chats", color = Color.White, fontWeight = FontWeight.Bold)
            Text("ডেমো ইনবক্স", color = Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(chats.filter { it.first.contains(query, true) || it.second.contains(query, true) }) { chat ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                        .background(Brush.horizontalGradient(listOf(Panel, Color(0xFF28203D))))
                        .clickable { selectedMessage = chat.first }.padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Violet, Pink))), contentAlignment = Alignment.Center) {
                        Text(chat.first.take(1), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chat.first, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(chat.second, color = Muted, fontSize = 11.sp, maxLines = 1)
                    }
                    Text(chat.third, color = Aqua, fontSize = 10.sp)
                }
            }
        }
    }
    if (selectedMessage != null) {
        AlertDialog(
            onDismissRequest = { selectedMessage = null }, containerColor = Panel,
            title = { Text(selectedMessage!!, color = Color.White) },
            text = { Text("এই ইনবক্সটি এখন ডিজাইন ডেমো। ব্যক্তিগত মেসেজ সংরক্ষণ ও পাঠাতে মেসেজিং ব্যাকএন্ড যুক্ত করতে হবে।", color = Muted) },
            confirmButton = { TextButton(onClick = { selectedMessage = null }) { Text("ঠিক আছে") } }
        )
    }
}

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    var showWallet by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("VoxMeet User") }
    var profileBio by remember { mutableStateOf("নতুন কমিউনিটি সদস্য") }
    var draftName by remember { mutableStateOf(profileName) }
    var draftBio by remember { mutableStateOf(profileBio) }
    var privateProfile by remember { mutableStateOf(false) }
    var allowMessages by remember { mutableStateOf(true) }
    var showOnlineStatus by remember { mutableStateOf(true) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MY ACCOUNT", color = Pink, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                    Text("My Profile", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF643B91), Color(0xFF292044), Color(0xFF9A477D))))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(86.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Pink, Violet))), contentAlignment = Alignment.Center) {
                    Text(profileName.take(1).uppercase(), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(10.dp))
                Text(profileName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(profileBio, color = Color(0xFFE8DDF5), fontSize = 11.sp)
                Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ProfileMetric("0", "Following")
                    ProfileMetric("0", "Followers")
                    ProfileMetric("0", "Gifts")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).clickable { showWallet = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Diamond, null, tint = Color(0xFFFFC857), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("My Wallet", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Coins ও লেনদেনের তথ্য", color = Muted, fontSize = 11.sp)
                }
                Text("120 coins  ›", color = Aqua, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        item { Text("ACCOUNT & SUPPORT", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp) }
        item { ProfileMenuRow("✎", "Edit Profile", "নাম, ছবি ও পরিচিতি") { draftName = profileName; draftBio = profileBio; showEdit = true } }
        item { ProfileMenuRow("♧", "Privacy & Safety", "অ্যাকাউন্ট সুরক্ষা") { showPrivacy = true } }
        item { ProfileMenuRow("♧", "Help & Support", "সাহায্য, FAQ ও যোগাযোগ") { showHelp = true } }
        item { ProfileMenuRow("⚙", "Settings", "নোটিফিকেশন ও অ্যাপের পছন্দ") { showSettings = true } }
        item { Text("VoxMeet • Version 1.0 • Connect, share and shine", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) }
    }
    if (showWallet) WalletDialog(coins = 120, onDismiss = { showWallet = false })
    if (showEdit) {
        AlertDialog(
            onDismissRequest = { showEdit = false }, containerColor = Panel,
            title = { Text("Edit Profile", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = draftName, onValueChange = { draftName = it }, label = { Text("নাম") }, singleLine = true)
                    OutlinedTextField(value = draftBio, onValueChange = { draftBio = it }, label = { Text("পরিচিতি / Bio") }, minLines = 2)
                    Text("এই পরিবর্তন আপাতত এই অ্যাপ সেশনে থাকবে। স্থায়ীভাবে সংরক্ষণের জন্য প্রোফাইল ব্যাকএন্ড লাগবে।", color = Muted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draftName.isNotBlank()) { profileName = draftName.trim(); profileBio = draftBio.trim(); showEdit = false }
                }) { Text("সংরক্ষণ") }
            },
            dismissButton = { TextButton(onClick = { showEdit = false }) { Text("বাতিল") } }
        )
    }
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false }, containerColor = Panel,
            title = { Text("Privacy & Safety", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("প্রাইভেট প্রোফাইল", color = Color.White); Text("প্রোফাইলের দৃশ্যমানতা সীমিত", color = Muted, fontSize = 10.sp) }
                        Switch(checked = privateProfile, onCheckedChange = { privateProfile = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("মেসেজ গ্রহণ", color = Color.White); Text("অন্যদের মেসেজ পাঠাতে অনুমতি", color = Muted, fontSize = 10.sp) }
                        Switch(checked = allowMessages, onCheckedChange = { allowMessages = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("অনলাইন স্ট্যাটাস", color = Color.White); Text("আপনি অনলাইনে আছেন কি না", color = Muted, fontSize = 10.sp) }
                        Switch(checked = showOnlineStatus, onCheckedChange = { showOnlineStatus = it })
                    }
                    Text("এগুলো আপাতত শুধু এই ডিভাইসের UI সেটিং। সার্ভার-ভিত্তিক প্রাইভেসি প্রয়োগ এখনো সংযুক্ত নয়।", color = Muted, fontSize = 10.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("ঠিক আছে") } }
        )
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false }, containerColor = Panel,
            title = { Text("Help & Support", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("VoxMeet সহায়তা", color = Aqua, fontWeight = FontWeight.Bold)
                    Text("• অ্যাকাউন্টে প্রবেশ করতে সমস্যা হলে Email/Phone নম্বর যাচাই করুন।", color = Color.White, fontSize = 12.sp)
                    Text("• Live Room-এ মাইক্রোফোন ব্যবহারে Android permission দিন।", color = Color.White, fontSize = 12.sp)
                    Text("• Coins ও পেমেন্ট এখনো ডেমো; বাস্তব লেনদেন করবেন না।", color = Color.White, fontSize = 12.sp)
                    Text("সাপোর্ট ইমেইল ও সরাসরি টিকিট ব্যবস্থা Backend ধাপে যুক্ত করা হবে।", color = Muted, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("বন্ধ") } }
        )
    }
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false }, containerColor = Panel,
            title = { Text("Settings", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("নোটিফিকেশন", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("সাউন্ড", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(checked = soundEnabled, onCheckedChange = { soundEnabled = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ভাইব্রেশন", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(checked = vibrationEnabled, onCheckedChange = { vibrationEnabled = it })
                    }
                    Text("এই পছন্দগুলো আপাতত অ্যাপ সেশনে থাকে; স্থায়ী সেটিংস সংরক্ষণ পরবর্তী ধাপ।", color = Muted, fontSize = 10.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("ঠিক আছে") } }
        )
    }
}

@Composable
fun ProfileMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Muted, fontSize = 10.sp)
    }
}

@Composable
fun ProfileMenuRow(symbol: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Panel)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, color = Aqua, fontSize = 20.sp, modifier = Modifier.width(34.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }
        Text("›", color = Muted, fontSize = 22.sp)
    }
}

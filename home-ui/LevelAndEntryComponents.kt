package com.rakib.voxmeet

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LevelProgressCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2140)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("YOUR VOXMEET JOURNEY", color = Color(0xFF54E0C1), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text("তোমার Level", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LevelTrackCard(Modifier.weight(1f), "Supporter", "Gift পাঠানোর Level", "🎁", Color(0xFF8B7CFF))
                LevelTrackCard(Modifier.weight(1f), "Charm", "Gift পাওয়ার Level", "✨", Color(0xFFFF63B5))
            }
            Text(
                "Level ও XP এখনো সার্ভার থেকে যুক্ত হয়নি। সংযোগ হলে এখানে আসল অগ্রগতি দেখাবে।",
                color = Color.LightGray, fontSize = 11.sp, lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun LevelTrackCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: String,
    accent: Color
) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.12f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(icon, fontSize = 19.sp)
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Level —", color = accent, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = Color.LightGray, fontSize = 10.sp, minLines = 2, lineHeight = 14.sp)
        LinearProgressIndicator(
            progress = { 0f },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
            color = accent,
            trackColor = Color.White.copy(alpha = 0.12f)
        )
        Text("XP অপেক্ষমাণ", color = Color.Gray, fontSize = 9.sp)
    }
}

private data class EntryTier(
    val name: String,
    val range: String,
    val accent: Color,
    val note: String
)

@Composable
fun EntryTierPreview() {
    val tiers = listOf(
        EntryTier("Bronze", "Level 1–9", Color(0xFFCD8B58), "উষ্ণ ব্রোঞ্জ আভা"),
        EntryTier("Silver", "Level 10–19", Color(0xFFBFC9D8), "সিলভার শাইন"),
        EntryTier("Gold", "Level 20–29", Color(0xFFFFD166), "গোল্ডেন গ্লো"),
        EntryTier("Diamond", "Level 30–39", Color(0xFF6DE1E8), "ডায়মন্ড স্পার্ক"),
        EntryTier("Royal", "Level 40–49", Color(0xFFB68CFF), "রয়্যাল অরা"),
        EntryTier("Legend", "Level 50+", Color(0xFFFF78B7), "লেজেন্ডারি বার্স্ট")
    )
    var selected by remember { mutableIntStateOf(0) }
    val tier = tiers[selected]
    val transition = rememberInfiniteTransition(label = "entryTierPreview")
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tierGlow"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2140)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("ENTRY ANIMATION", color = Color(0xFF54E0C1), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text("প্রতিটি Tier-এ আলাদা Entry Effect", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("নিচের Tier বেছে নিয়ে অ্যানিমেশনের ডিজাইন দেখুন।", color = Color.LightGray, fontSize = 11.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                tiers.forEachIndexed { index, item ->
                    Surface(
                        onClick = { selected = index },
                        shape = CircleShape,
                        color = if (selected == index) item.accent.copy(alpha = 0.24f) else Color(0xFF292E4B),
                        border = BorderStroke(1.dp, if (selected == index) item.accent else Color.Transparent)
                    ) {
                        Text(
                            item.name,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = if (selected == index) item.accent else Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(
                        tier.accent.copy(alpha = 0.24f * glow),
                        Color(0xFF29213F),
                        tier.accent.copy(alpha = 0.12f)
                    ))).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Box(
                        Modifier.size(54.dp).shadow(8.dp, CircleShape)
                            .background(tier.accent.copy(alpha = 0.22f * glow), CircleShape)
                            .padding(3.dp).background(Color(0xFF29213F), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("R", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Rakib", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text("VoxMeet ID: —", color = Color.LightGray, fontSize = 10.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Supporter", color = tier.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("•", color = Color.Gray)
                            Text("Charm", color = Color(0xFFFF63B5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(tier.name.uppercase(), color = tier.accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Text(tier.range, color = Color.LightGray, fontSize = 9.sp)
                    }
                }
            }
            Text(tier.note, color = tier.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                "এটি ডিজাইন প্রিভিউ—বাস্তব রুমে সদস্য প্রবেশের ইভেন্টের সঙ্গে এখনো যুক্ত নয়।",
                color = Color.Gray, fontSize = 10.sp, lineHeight = 14.sp
            )
        }
    }
}

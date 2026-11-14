package com.rakib.voxmeet

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.track.DataPublishReliability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import io.livekit.android.token.TokenRequestOptions
import io.livekit.android.token.TokenSource
import io.livekit.android.token.cached

/**
 * LiveKit audio transport for VoxMeet.
 *
 * The Development Token Server is enabled only for early testing.
 * Replace it with VoxMeet's authenticated backend token endpoint before production.
 */
class LiveKitAudioManager(context: Context) {
    private val room: Room = LiveKit.create(context.applicationContext)
    private val tokenSource = TokenSource
        .fromDevelopmentTokenServer(DEV_TOKEN_SERVER_ID)
        .cached()

    private var connected = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableParticipants = MutableStateFlow<List<String>>(emptyList())
    val participants = mutableParticipants.asStateFlow()
    private val mutableMessages = MutableStateFlow<List<String>>(emptyList())
    val messages = mutableMessages.asStateFlow()

    private fun refreshParticipants() {
        val names = buildList {
            add(room.localParticipant.name ?: "You")
            room.remoteParticipants.values.forEach { participant ->
                add(participant.name ?: participant.identity?.value ?: "Guest")
            }
        }.distinct().take(12)
        mutableParticipants.value = names
    }

    val isConnected: Boolean
        get() = connected

    suspend fun joinRoom(roomName: String, participantName: String) {
        require(roomName.isNotBlank()) { "Room name cannot be empty" }
        require(participantName.isNotBlank()) { "Participant name cannot be empty" }

        val credentials = tokenSource.fetch(
            TokenRequestOptions(
                roomName = roomName.trim(),
                participantName = participantName.trim(),
            ),
        ).getOrThrow()

        scope.launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.ParticipantConnected,
                    is RoomEvent.ParticipantDisconnected,
                    is RoomEvent.Connected -> refreshParticipants()
                    is RoomEvent.DataReceived -> {
                        val payload = event.data.toString(Charsets.UTF_8)
                        val parts = payload.split("\\t", limit = 2)
                        if (parts.size == 2) {
                            val line = "${parts[0]}: ${parts[1]}"
                            mutableMessages.value = (mutableMessages.value + line).takeLast(100)
                        }
                    }
                    else -> Unit
                }
            }
        }
        room.connect(credentials.serverUrl, credentials.participantToken)
        connected = true
        refreshParticipants()
    }

    suspend fun setMicrophoneEnabled(enabled: Boolean) {
        check(isConnected) { "Join a voice room before changing the microphone" }
        room.localParticipant.setMicrophoneEnabled(enabled)
    }

    suspend fun sendChat(message: String) {
        check(isConnected) { "Join a voice room before sending a message" }
        val clean = message.trim()
        require(clean.isNotEmpty()) { "Message cannot be empty" }
        val sender = room.localParticipant.name ?: "You"
        val payload = "$sender\\t$clean".toByteArray(Charsets.UTF_8)
        room.localParticipant.publishData(
            payload,
            reliability = DataPublishReliability.RELIABLE,
            topic = "voxmeet-chat",
        ).getOrThrow()
        mutableMessages.value = (mutableMessages.value + "$sender: $clean").takeLast(100)
    }

    suspend fun leaveRoom() {
        if (isConnected) {
            room.disconnect()
            connected = false
            mutableParticipants.value = emptyList()
        }
    }

    companion object {
        private const val DEV_TOKEN_SERVER_ID = "voxmeetapk-gsvob5"
    }
}

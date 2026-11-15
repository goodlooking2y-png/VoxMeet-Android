package com.rakib.voxmeet

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.track.DataPublishReliability
import io.livekit.android.token.TokenRequestOptions
import io.livekit.android.token.TokenSource
import io.livekit.android.token.cached
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RoomParticipant(
    val name: String,
    val isLocal: Boolean
)

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

    @Volatile
    private var connected = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eventJob: Job? = null

    private val mutableParticipants = MutableStateFlow<List<RoomParticipant>>(emptyList())
    val participants = mutableParticipants.asStateFlow()

    private val mutableMessages = MutableStateFlow<List<String>>(emptyList())
    val messages = mutableMessages.asStateFlow()

    val isConnected: Boolean
        get() = connected

    private fun refreshParticipants() {
        val currentParticipants = buildList {
            add(
                RoomParticipant(
                    name = room.localParticipant.name
                        ?: room.localParticipant.identity?.value
                        ?: "You",
                    isLocal = true
                )
            )
            room.remoteParticipants.values.forEach { participant ->
                add(
                    RoomParticipant(
                        name = participant.name ?: participant.identity?.value ?: "Guest",
                        isLocal = false
                    )
                )
            }
        }.take(MAX_ROOM_PARTICIPANTS)

        mutableParticipants.value = currentParticipants
    }

    suspend fun joinRoom(roomName: String, participantName: String) {
        require(roomName.isNotBlank()) { "Room name cannot be empty" }
        require(participantName.isNotBlank()) { "Participant name cannot be empty" }

        // Prevent stale collectors from a previous/failed connection being left behind.
        eventJob?.cancelAndJoin()
        eventJob = null
        connected = false
        mutableParticipants.value = emptyList()
        mutableMessages.value = emptyList()

        val credentials = tokenSource.fetch(
            TokenRequestOptions(
                roomName = roomName.trim(),
                participantName = participantName.trim(),
            ),
        ).getOrThrow()

        // LiveKit 2.30 exposes Room.events as EventListenable, not Kotlin Flow.
        // Use LiveKit's own collect extension and keep exactly one collector per room session.
        eventJob = scope.launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.ParticipantConnected,
                    is RoomEvent.ParticipantDisconnected,
                    is RoomEvent.Connected,
                    is RoomEvent.Reconnected -> refreshParticipants()

                    is RoomEvent.DataReceived -> {
                        // Ignore unrelated room data; VoxMeet chat uses its own topic.
                        if (event.topic == CHAT_TOPIC) {
                            val parts = event.data.toString(Charsets.UTF_8).split("\t", limit = 2)
                            if (parts.size == 2) {
                                val line = "${parts[0]}: ${parts[1]}"
                                mutableMessages.value = (mutableMessages.value + line).takeLast(MAX_CHAT_MESSAGES)
                            }
                        }
                    }

                    is RoomEvent.Disconnected,
                    is RoomEvent.FailedToConnect -> {
                        connected = false
                        mutableParticipants.value = emptyList()
                    }

                    else -> Unit
                }
            }
        }

        try {
            room.connect(credentials.serverUrl, credentials.participantToken)
            connected = true
            refreshParticipants()
        } catch (error: Exception) {
            connected = false
            eventJob?.cancelAndJoin()
            eventJob = null
            mutableParticipants.value = emptyList()
            throw error
        }
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
        val payload = "$sender\t$clean".toByteArray(Charsets.UTF_8)
        room.localParticipant.publishData(
            payload,
            reliability = DataPublishReliability.RELIABLE,
            topic = CHAT_TOPIC,
        ).getOrThrow()

        // DataReceived is delivered to other participants; add the sender's own line locally.
        mutableMessages.value = (mutableMessages.value + "$sender: $clean").takeLast(MAX_CHAT_MESSAGES)
    }

    suspend fun leaveRoom() {
        try {
            if (isConnected) {
                room.disconnect()
            }
        } finally {
            connected = false
            eventJob?.cancelAndJoin()
            eventJob = null
            mutableParticipants.value = emptyList()
            mutableMessages.value = emptyList()
        }
    }

    companion object {
        private const val DEV_TOKEN_SERVER_ID = "voxmeetapk-gsvob5"
        private const val CHAT_TOPIC = "voxmeet-chat"
        private const val MAX_ROOM_PARTICIPANTS = 12
        private const val MAX_CHAT_MESSAGES = 100
    }
}

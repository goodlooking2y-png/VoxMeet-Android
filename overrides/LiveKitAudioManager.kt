package com.rakib.voxmeet

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
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

        room.connect(credentials.serverUrl, credentials.participantToken)
        connected = true
    }

    suspend fun setMicrophoneEnabled(enabled: Boolean) {
        check(isConnected) { "Join a voice room before changing the microphone" }
        room.localParticipant.setMicrophoneEnabled(enabled)
    }

    suspend fun leaveRoom() {
        if (isConnected) {
            room.disconnect()
            connected = false
        }
    }

    companion object {
        private const val DEV_TOKEN_SERVER_ID = "voxmeetapk-gsvob5"
    }
}

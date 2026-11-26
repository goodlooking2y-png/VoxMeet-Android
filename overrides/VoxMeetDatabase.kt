package com.rakib.voxmeet

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

data class VoxMeetProfile(
    val uid: String = "",
    val numericId: String = "",
    val name: String = "",
    val gender: String = "",
    val photoUrl: String = "",
    val bio: String = ""
)

/** Firebase Realtime Database data layer. Numeric IDs are reserved transactionally. */
class VoxMeetDatabase(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val root = FirebaseDatabase.getInstance(DATABASE_URL).reference

    private fun requireUid(): String =
        auth.currentUser?.uid ?: error("Please sign in before using VoxMeet.")

    suspend fun createOrLoadProfile(
        name: String,
        gender: String = "",
        photoUrl: String = "",
        bio: String = ""
    ): VoxMeetProfile {
        val uid = requireUid()
        val profileRef = root.child("publicProfiles").child(uid)
        val existing = profileRef.get().await()
        val savedId = existing.child("numericId").getValue(String::class.java)
            ?: existing.child("numericId").getValue(Long::class.java)?.toString()
        val numericId = savedId ?: reserveNumericId(uid)

        val profile = VoxMeetProfile(
            uid = uid,
            numericId = numericId,
            name = name.trim().ifBlank { auth.currentUser?.displayName.orEmpty() },
            gender = gender,
            photoUrl = photoUrl,
            bio = bio
        )
        profileRef.setValue(profile.toMap()).await()
        return profile
    }

    suspend fun getMyProfile(): VoxMeetProfile? {
        val uid = requireUid()
        return root.child("publicProfiles").child(uid).get().await().toProfile(uid)
    }

    suspend fun updatePhotoUrl(photoUrl: String) {
        val uid = requireUid()
        root.child("publicProfiles").child(uid).child("photoUrl").setValue(photoUrl).await()
    }

    suspend fun findProfileByNumericId(numericId: String): VoxMeetProfile? {
        val cleanId = numericId.trim()
        if (cleanId.isEmpty() || cleanId.any { !it.isDigit() }) return null
        val uid = root.child("userIds").child(cleanId).get().await()
            .getValue(String::class.java) ?: return null
        return root.child("publicProfiles").child(uid).get().await().toProfile(uid)
    }

    suspend fun findProfileByUid(uid: String): VoxMeetProfile? {
        if (uid.isBlank()) return null
        return root.child("publicProfiles").child(uid).get().await().toProfile(uid)
    }

    suspend fun isFollowing(targetUid: String): Boolean {
        val uid = requireUid()
        if (uid == targetUid) return false
        return root.child("following").child(uid).child(targetUid).get().await().exists()
    }

    suspend fun follow(target: VoxMeetProfile) {
        val uid = requireUid()
        require(uid != target.uid) { "You cannot follow your own profile." }
        require(target.uid.isNotBlank()) { "Invalid profile." }
        if (root.child("following").child(uid).child(target.uid).get().await().exists()) return

        val notificationId = root.child("notifications").child(target.uid).push().key
            ?: error("Could not create notification ID.")
        val followingRef = root.child("following").child(uid).child(target.uid)
        val followerRef = root.child("followers").child(target.uid).child(uid)
        followingRef.setValue(true).await()
        try {
            followerRef.setValue(true).await()
            root.child("notifications").child(target.uid).child(notificationId).setValue(
                mapOf(
                    "senderUid" to uid,
                    "type" to "follow",
                    "createdAt" to System.currentTimeMillis(),
                    "read" to false
                )
            ).await()
        } catch (error: Exception) {
            try { followerRef.removeValue().await() } catch (_: Exception) { }
            try { followingRef.removeValue().await() } catch (_: Exception) { }
            throw error
        }
    }

    suspend fun unfollow(targetUid: String) {
        val uid = requireUid()
        require(uid != targetUid) { "You cannot unfollow yourself." }
        val followingRef = root.child("following").child(uid).child(targetUid)
        val followerRef = root.child("followers").child(targetUid).child(uid)
        followerRef.removeValue().await()
        try {
            followingRef.removeValue().await()
        } catch (error: Exception) {
            try { followerRef.setValue(true).await() } catch (_: Exception) { }
            throw error
        }
    }

    suspend fun getFollowerCount(uid: String): Int =
        root.child("followers").child(uid).get().await().childrenCount.toInt()

    suspend fun getFollowingCount(uid: String): Int =
        root.child("following").child(uid).get().await().childrenCount.toInt()

    suspend fun getFollowNotifications(): List<Map<String, Any?>> {
        val uid = requireUid()
        val snapshot = root.child("notifications").child(uid).get().await()
        return snapshot.children.mapNotNull { child ->
            if (child.child("type").getValue(String::class.java) != "follow") return@mapNotNull null
            mapOf(
                "id" to child.key,
                "senderUid" to child.child("senderUid").getValue(String::class.java),
                "createdAt" to child.child("createdAt").getValue(Long::class.java),
                "read" to child.child("read").getValue(Boolean::class.java)
            )
        }.sortedByDescending { it["createdAt"] as? Long ?: 0L }
    }

    private suspend fun reserveNumericId(uid: String): String {
        repeat(30) {
            val candidate = Random.nextInt(10_000_000, 100_000_000).toString()
            if (root.child("userIds").child(candidate).reserveFor(uid)) return candidate
        }
        error("Could not allocate a unique User ID. Please try again.")
    }

    private suspend fun DatabaseReference.reserveFor(uid: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val current = currentData.getValue(String::class.java)
                    return if (current == null || current == uid) {
                        currentData.value = uid
                        Transaction.success(currentData)
                    } else Transaction.abort()
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    if (!continuation.isActive) return
                    if (error != null) continuation.resumeWithException(error.toException())
                    else continuation.resume(committed && currentData?.getValue(String::class.java) == uid)
                }
            }, false)
        }

    private fun VoxMeetProfile.toMap(): Map<String, Any> = mapOf(
        "uid" to uid, "numericId" to numericId, "name" to name,
        "gender" to gender, "photoUrl" to photoUrl, "bio" to bio
    )

    private fun DataSnapshot.toProfile(uid: String): VoxMeetProfile? {
        if (!exists()) return null
        return VoxMeetProfile(
            uid = uid,
            numericId = child("numericId").getValue(String::class.java)
                ?: child("numericId").getValue(Long::class.java)?.toString().orEmpty(),
            name = child("name").getValue(String::class.java).orEmpty(),
            gender = child("gender").getValue(String::class.java).orEmpty(),
            photoUrl = child("photoUrl").getValue(String::class.java).orEmpty(),
            bio = child("bio").getValue(String::class.java).orEmpty()
        )
    }

    private suspend fun <T> Task<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (task.isSuccessful) {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(task.result as T)
                } else {
                    continuation.resumeWithException(
                        task.exception ?: IllegalStateException("Firebase request failed.")
                    )
                }
            }
        }

    companion object {
        const val DATABASE_URL =
            "https://voxmeet-a4332-default-rtdb.asia-southeast1.firebasedatabase.app"
    }
}

package com.pocketarcade.leaderboard

import android.content.Context
import android.provider.ContactsContract
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.security.MessageDigest

data class FollowEntry(
    val uid: String = "",
    val username: String = "",
    val avatarIndex: Int = 0,
    val avatarColor: Int = 0,
    val addedAt: Long = 0L
)

enum class TimeRange { WEEK, MONTH, ALL_TIME }

object FriendsManager {

    private val db by lazy { FirebaseFirestore.getInstance() }

    fun follow(
        myUid: String,
        theirUid: String,
        theirUsername: String,
        theirAvatarIndex: Int,
        theirAvatarColor: Int,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        db.collection("following").document(myUid)
            .collection("list").document(theirUid)
            .set(mapOf(
                "uid"         to theirUid,
                "username"    to theirUsername,
                "avatarIndex" to theirAvatarIndex,
                "avatarColor" to theirAvatarColor,
                "addedAt"     to System.currentTimeMillis()
            ))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError() }
    }

    fun unfollow(
        myUid: String,
        theirUid: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        db.collection("following").document(myUid)
            .collection("list").document(theirUid)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError() }
    }

    fun getFollowing(
        myUid: String,
        onResult: (List<FollowEntry>) -> Unit
    ) {
        db.collection("following").document(myUid)
            .collection("list")
            .orderBy("addedAt", Query.Direction.ASCENDING)
            .limit(30)
            .get()
            .addOnSuccessListener { snap ->
                onResult(snap.documents.mapNotNull { doc ->
                    runCatching {
                        FollowEntry(
                            uid         = doc.getString("uid") ?: return@runCatching null,
                            username    = doc.getString("username") ?: "???",
                            avatarIndex = (doc.getLong("avatarIndex") ?: 0L).toInt(),
                            avatarColor = (doc.getLong("avatarColor") ?: 0L).toInt(),
                            addedAt     = doc.getLong("addedAt") ?: 0L
                        )
                    }.getOrNull()
                })
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun checkMutuals(
        myUid: String,
        followingUids: List<String>,
        onResult: (Set<String>) -> Unit
    ) {
        if (followingUids.isEmpty()) { onResult(emptySet()); return }
        val mutuals = mutableSetOf<String>()
        var pending = followingUids.size
        followingUids.forEach { theirUid ->
            db.collection("following").document(theirUid)
                .collection("list").document(myUid)
                .get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result?.exists() == true) mutuals.add(theirUid)
                    if (--pending == 0) onResult(mutuals)
                }
        }
    }

    fun fetchFriendsScores(
        uids: List<String>,
        game: String,
        timeRange: TimeRange,
        mode: String? = null,
        onResult: (List<GlobalEntry>) -> Unit
    ) {
        if (uids.isEmpty()) { onResult(emptyList()); return }
        var q: Query = db.collection("globalScores")
            .whereEqualTo("game", game)
            .whereIn("uid", uids.take(30))
        if (mode != null) q = q.whereEqualTo("mode", mode)
        q.orderBy("score", Query.Direction.DESCENDING)
            .limit(200)
            .get()
            .addOnSuccessListener { snap ->
                val cutoff = when (timeRange) {
                    TimeRange.WEEK     -> System.currentTimeMillis() - 7L * 24 * 3600 * 1000
                    TimeRange.MONTH    -> System.currentTimeMillis() - 30L * 24 * 3600 * 1000
                    TimeRange.ALL_TIME -> 0L
                }
                val entries = snap.documents.mapNotNull { doc ->
                    runCatching {
                        val ts = doc.getLong("timestamp") ?: 0L
                        if (ts < cutoff) return@runCatching null
                        GlobalEntry(
                            uid         = doc.getString("uid") ?: "",
                            username    = doc.getString("username") ?: "???",
                            score       = (doc.getLong("score") ?: 0L).toInt(),
                            country     = doc.getString("country") ?: "",
                            state       = doc.getString("state") ?: "",
                            mode        = doc.getString("mode"),
                            timestamp   = ts,
                            avatarIndex = (doc.getLong("avatarIndex") ?: 0L).toInt(),
                            avatarColor = (doc.getLong("avatarColor") ?: 0L).toInt()
                        )
                    }.getOrNull()
                }
                val best = entries
                    .groupBy { it.uid }
                    .map { (_, scores) -> scores.maxByOrNull { it.score }!! }
                    .sortedByDescending { it.score }
                onResult(best)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun findFromContacts(
        context: Context,
        onResult: (List<Map<String, Any>>) -> Unit
    ) {
        val hashes = mutableListOf<String>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )
        cursor?.use {
            while (it.moveToNext()) {
                val raw = it.getString(0) ?: continue
                val normalized = raw.replace(Regex("[^0-9+]"), "")
                if (normalized.length >= 7) hashes.add(sha256(normalized))
            }
        }
        val distinct = hashes.distinct()
        if (distinct.isEmpty()) { onResult(emptyList()); return }

        val results = mutableListOf<Map<String, Any>>()
        val batches = distinct.chunked(30)
        var pending = batches.size
        batches.forEach { batch ->
            db.collection("phoneHashes")
                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), batch)
                .get()
                .addOnSuccessListener { snap ->
                    snap.documents.forEach { doc ->
                        results.add(mapOf(
                            "uid"         to (doc.getString("uid") ?: ""),
                            "username"    to (doc.getString("username") ?: ""),
                            "avatarIndex" to ((doc.getLong("avatarIndex") ?: 0L).toInt()),
                            "avatarColor" to ((doc.getLong("avatarColor") ?: 0L).toInt())
                        ))
                    }
                    if (--pending == 0) onResult(results)
                }
                .addOnFailureListener { if (--pending == 0) onResult(results) }
        }
    }

    fun storeMyPhoneHash(
        uid: String,
        username: String,
        phoneNumber: String,
        avatarIndex: Int,
        avatarColor: Int
    ) {
        val normalized = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (normalized.length < 7) return
        db.collection("phoneHashes").document(sha256(normalized)).set(mapOf(
            "uid"         to uid,
            "username"    to username,
            "avatarIndex" to avatarIndex,
            "avatarColor" to avatarColor
        ))
    }

    fun fetchAllGroupScores(
        uids: List<String>,
        onResult: (Map<String, Map<String, Int>>) -> Unit
    ) {
        if (uids.isEmpty()) { onResult(emptyMap()); return }
        db.collection("globalScores")
            .whereIn("uid", uids.take(30))
            .get()
            .addOnSuccessListener { snap ->
                val result = mutableMapOf<String, MutableMap<String, Int>>()
                snap.documents.forEach { doc ->
                    val uid   = doc.getString("uid")   ?: return@forEach
                    val game  = doc.getString("game")  ?: return@forEach
                    val score = doc.getLong("score")?.toInt() ?: return@forEach
                    val gameMap = result.getOrPut(uid) { mutableMapOf() }
                    if (score > (gameMap[game] ?: 0)) gameMap[game] = score
                }
                onResult(result)
            }
            .addOnFailureListener { onResult(emptyMap()) }
    }

    fun searchUser(
        username: String,
        onFound: (uid: String, avatarIndex: Int, avatarColor: Int) -> Unit,
        onNotFound: () -> Unit,
        onError: () -> Unit
    ) {
        db.collection("usernames").document(username).get()
            .addOnSuccessListener { snap ->
                val uid = snap.getString("uid")
                if (uid == null) { onNotFound(); return@addOnSuccessListener }
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { userSnap ->
                        val avatarIndex = (userSnap.getLong("avatarIndex") ?: 0L).toInt()
                        val avatarColor = (userSnap.getLong("avatarColor") ?: 0L).toInt()
                        onFound(uid, avatarIndex, avatarColor)
                    }
                    .addOnFailureListener { onError() }
            }
            .addOnFailureListener { onError() }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

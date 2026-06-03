package com.pocketarcade.leaderboard

import android.content.Context
import android.provider.ContactsContract
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.security.MessageDigest
import java.util.Calendar

data class FollowEntry(
    val uid: String = "",
    val username: String = "",
    val avatarIndex: Int = 0,
    val avatarColor: Int = 0,
    val addedAt: Long = 0L
)

enum class TimeRange { WEEK, MONTH, ALL_TIME }

/** "2026-W22" style key for the current calendar week (ISO week-of-year). */
fun currentWeekKey(): String {
    val cal = Calendar.getInstance()
    return "${cal.get(Calendar.YEAR)}-W%02d".format(cal.get(Calendar.WEEK_OF_YEAR))
}

/** "2026-06" style key for the current calendar month. */
fun currentMonthKey(): String {
    val cal = Calendar.getInstance()
    return "${cal.get(Calendar.YEAR)}-%02d".format(cal.get(Calendar.MONTH) + 1)
}

/** Midnight of the most recent Sunday (start of current calendar week). */
fun calendarStartOfWeek(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0);      cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** Midnight of the 1st of the current calendar month. */
fun calendarStartOfMonth(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0);      cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** Days remaining in the current Sun–Sat week (today counts as 1). */
fun daysLeftInWeek(): Int {
    val cal = Calendar.getInstance()
    return Calendar.SATURDAY - cal.get(Calendar.DAY_OF_WEEK) + 1
}

/** Days remaining in the current calendar month (today counts as 1). */
fun daysLeftInMonth(): Int {
    val cal = Calendar.getInstance()
    return cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH) + 1
}

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

        if (timeRange != TimeRange.ALL_TIME) {
            // Time-bounded: use the dedicated periodScores collection.
            // Each document is already the best score per (uid, game, mode, period),
            // so no groupBy deduplication is needed — just sort.
            val periodType = if (timeRange == TimeRange.WEEK) "week" else "month"
            val periodKey  = if (timeRange == TimeRange.WEEK) currentWeekKey() else currentMonthKey()

            var q: Query = db.collection("periodScores")
                .whereEqualTo("game", game)
                .whereIn("uid", uids.take(30))
                .whereEqualTo("periodType", periodType)
                .whereEqualTo("periodKey", periodKey)
            if (mode != null) q = q.whereEqualTo("mode", mode)

            q.limit(200L).get()
                .addOnSuccessListener { snap ->
                    val entries = snap.documents.mapNotNull { doc ->
                        runCatching {
                            GlobalEntry(
                                uid         = doc.getString("uid") ?: "",
                                username    = doc.getString("username") ?: "???",
                                score       = (doc.getLong("score") ?: 0L).toInt(),
                                country     = doc.getString("country") ?: "",
                                state       = doc.getString("state") ?: "",
                                mode        = doc.getString("mode"),
                                timestamp   = doc.getLong("timestamp") ?: 0L,
                                avatarIndex = (doc.getLong("avatarIndex") ?: 0L).toInt(),
                                avatarColor = (doc.getLong("avatarColor") ?: 0L).toInt()
                            )
                        }.getOrNull()
                    }.sortedByDescending { it.score }
                    onResult(entries)
                }
                .addOnFailureListener { onResult(emptyList()) }
            return
        }

        // ALL_TIME: query globalScores (one best-per-player entry per game+mode).
        var q: Query = db.collection("globalScores")
            .whereEqualTo("game", game)
            .whereIn("uid", uids.take(30))
        if (mode != null) q = q.whereEqualTo("mode", mode)
        q = q.orderBy("score", Query.Direction.DESCENDING)

        q.limit(200L).get()
            .addOnSuccessListener { snap ->
                val entries = snap.documents.mapNotNull { doc ->
                    runCatching {
                        GlobalEntry(
                            uid         = doc.getString("uid") ?: "",
                            username    = doc.getString("username") ?: "???",
                            score       = (doc.getLong("score") ?: 0L).toInt(),
                            country     = doc.getString("country") ?: "",
                            state       = doc.getString("state") ?: "",
                            mode        = doc.getString("mode"),
                            timestamp   = doc.getLong("timestamp") ?: 0L,
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

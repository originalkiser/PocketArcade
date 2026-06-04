package com.pocketarcade.leaderboard

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/** Decodes Pong encoded score (playerScore*100 + 99-aiScore) → "ps–ai"; passes through other scores. */
fun formatGlobalScore(game: String, score: Int): String {
    if (game != "pong" && !game.startsWith("pong_")) return "%,d".format(score)
    val ps: Int; val ai: Int
    when {
        score >= 80 -> { ps = score / 100; ai = 99 - score % 100 }
        score >= 10 -> { ps = score / 10;  ai = score % 10 }
        else        -> { ps = 0;           ai = score }
    }
    return "$ps–$ai"
}

data class GlobalEntry(
    val username: String = "",
    val score: Int = 0,
    val country: String = "",
    val state: String = "",
    val mode: String? = null,
    val timestamp: Long = 0L,
    val avatarIndex: Int = 0,
    val avatarColor: Int = 0,
    val uid: String = ""
)

object LocationData {
    val countries = listOf(
        "Argentina", "Australia", "Austria", "Belgium", "Brazil",
        "Canada", "Chile", "China", "Colombia", "Czech Republic",
        "Denmark", "Egypt", "Finland", "France", "Germany",
        "Greece", "Hungary", "India", "Indonesia", "Ireland",
        "Israel", "Italy", "Japan", "Malaysia", "Mexico",
        "Netherlands", "New Zealand", "Nigeria", "Norway", "Pakistan",
        "Philippines", "Poland", "Portugal", "Romania", "Russia",
        "Saudi Arabia", "Singapore", "South Africa", "South Korea", "Spain",
        "Sweden", "Switzerland", "Taiwan", "Thailand", "Turkey",
        "Ukraine", "United Kingdom", "United States", "Vietnam", "Other"
    )

    val usStates = listOf(
        "Alabama", "Alaska", "Arizona", "Arkansas", "California",
        "Colorado", "Connecticut", "Delaware", "Florida", "Georgia",
        "Hawaii", "Idaho", "Illinois", "Indiana", "Iowa",
        "Kansas", "Kentucky", "Louisiana", "Maine", "Maryland",
        "Massachusetts", "Michigan", "Minnesota", "Mississippi", "Missouri",
        "Montana", "Nebraska", "Nevada", "New Hampshire", "New Jersey",
        "New Mexico", "New York", "North Carolina", "North Dakota", "Ohio",
        "Oklahoma", "Oregon", "Pennsylvania", "Rhode Island", "South Carolina",
        "South Dakota", "Tennessee", "Texas", "Utah", "Vermont",
        "Virginia", "Washington", "Washington D.C.", "West Virginia", "Wisconsin",
        "Wyoming"
    )

    val caProvinces = listOf(
        "Alberta", "British Columbia", "Manitoba", "New Brunswick",
        "Newfoundland and Labrador", "Northwest Territories", "Nova Scotia",
        "Nunavut", "Ontario", "Prince Edward Island", "Quebec",
        "Saskatchewan", "Yukon"
    )

    fun subregions(country: String): List<String> = when (country) {
        "United States" -> usStates
        "Canada"        -> caProvinces
        else            -> emptyList()
    }
}

object GlobalLeaderboard {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db   by lazy { FirebaseFirestore.getInstance() }

    val currentUid: String? get() = auth.currentUser?.uid

    fun ensureSignedIn(onReady: (uid: String) -> Unit, onError: (String) -> Unit = {}) {
        val user = auth.currentUser
        if (user != null) { onReady(user.uid); return }
        auth.signInAnonymously()
            .addOnSuccessListener { onReady(it.user!!.uid) }
            .addOnFailureListener { e ->
                android.util.Log.e("GlobalLeaderboard", "signInAnonymously failed", e)
                onError(e.message ?: "unknown error")
            }
    }

    fun claimUsername(
        username: String,
        uid: String,
        country: String,
        state: String,
        avatarIndex: Int,
        avatarColor: Int,
        onSuccess: () -> Unit,
        onTaken: () -> Unit,
        onError: () -> Unit
    ) {
        val usernameRef = db.collection("usernames").document(username)
        val userRef     = db.collection("users").document(uid)
        db.runTransaction { tx ->
            if (tx.get(usernameRef).exists()) throw Exception("taken")
            tx.set(usernameRef, mapOf("uid" to uid))
            tx.set(userRef, mapOf(
                "username"    to username,
                "country"     to country,
                "state"       to state,
                "avatarIndex" to avatarIndex,
                "avatarColor" to avatarColor,
                "createdAt"   to System.currentTimeMillis()
            ))
        }.addOnSuccessListener { onSuccess() }
         .addOnFailureListener { e ->
             if (e.message == "taken") onTaken() else onError()
         }
    }

    fun updateUserAvatar(uid: String, avatarIndex: Int, avatarColor: Int) {
        db.collection("users").document(uid)
            .update(mapOf("avatarIndex" to avatarIndex, "avatarColor" to avatarColor))
    }

    fun changeUsername(
        uid: String,
        oldUsername: String,
        newUsername: String,
        onSuccess: () -> Unit,
        onTaken: () -> Unit,
        onError: () -> Unit
    ) {
        val oldRef  = db.collection("usernames").document(oldUsername)
        val newRef  = db.collection("usernames").document(newUsername)
        val userRef = db.collection("users").document(uid)
        db.runTransaction { tx ->
            if (tx.get(newRef).exists()) throw Exception("taken")
            tx.delete(oldRef)
            tx.set(newRef, mapOf("uid" to uid))
            tx.update(userRef, "username", newUsername)
        }.addOnSuccessListener {
            db.collection("globalScores").whereEqualTo("uid", uid).get()
                .addOnSuccessListener { snap ->
                    if (snap.isEmpty) { onSuccess(); return@addOnSuccessListener }
                    val batch = db.batch()
                    snap.documents.forEach { batch.update(it.reference, "username", newUsername) }
                    batch.commit()
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { onError() }
                }.addOnFailureListener { onError() }
        }.addOnFailureListener { e ->
            if (e.message == "taken") onTaken() else onError()
        }
    }

    fun migrateLocation(
        uid: String,
        newCountry: String,
        newState: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        val userRef = db.collection("users").document(uid)
        db.collection("globalScores").whereEqualTo("uid", uid).get()
            .addOnSuccessListener { snap ->
                val batch = db.batch()
                val update = mapOf("country" to newCountry, "state" to newState)
                snap.documents.forEach { batch.update(it.reference, update) }
                batch.update(userRef, update)
                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError() }
            }.addOnFailureListener { onError() }
    }

    fun submitScore(
        uid: String,
        username: String,
        game: String,
        score: Int,
        country: String,
        state: String,
        mode: String? = null,
        avatarIndex: Int = 0,
        avatarColor: Int = 0
    ) {
        val data = hashMapOf<String, Any>(
            "uid"         to uid,
            "username"    to username,
            "game"        to game,
            "score"       to score,
            "country"     to country,
            "state"       to state,
            "avatarIndex" to avatarIndex,
            "avatarColor" to avatarColor,
            "timestamp"   to System.currentTimeMillis()
        )
        if (mode != null) data["mode"] = mode

        // Enforce per-player 1-entry limit per game+mode combination.
        var q: Query = db.collection("globalScores")
            .whereEqualTo("uid", uid)
            .whereEqualTo("game", game)
        if (mode != null) q = q.whereEqualTo("mode", mode)

        q.get().addOnSuccessListener { snap ->
            val docs = snap.documents
            when {
                docs.isEmpty() -> {
                    // No entry yet — add freely
                    db.collection("globalScores").add(data)
                }
                else -> {
                    // Already has an entry — replace only if new score beats it
                    val existingDoc = docs[0]
                    val existingScore = (existingDoc.getLong("score") ?: 0L).toInt()
                    if (score > existingScore) {
                        db.runTransaction { tx ->
                            tx.delete(existingDoc.reference)
                            tx.set(db.collection("globalScores").document(), data)
                        }
                    }
                    // else: new score doesn't beat existing — skip
                }
            }
        }.addOnFailureListener {
            // If query fails, add anyway (fail open) to avoid losing scores
            db.collection("globalScores").add(data)
        }
    }

    /**
     * Submits a score to a single specific period bucket (week OR month).
     * Used by ScoreSyncManager to re-submit locally-cached scores after an offline session.
     */
    fun submitPeriodScoreForPeriod(
        uid: String, username: String, game: String, score: Int,
        country: String, state: String, mode: String? = null,
        avatarIndex: Int = 0, avatarColor: Int = 0,
        periodType: String, periodKey: String
    ) {
        val modeKey = mode ?: "_"
        val docId   = "$uid|$game|$modeKey|$periodType|$periodKey"
        val data    = hashMapOf<String, Any>(
            "uid"         to uid,
            "username"    to username,
            "game"        to game,
            "score"       to score,
            "country"     to country,
            "state"       to state,
            "avatarIndex" to avatarIndex,
            "avatarColor" to avatarColor,
            "timestamp"   to System.currentTimeMillis(),
            "periodType"  to periodType,
            "periodKey"   to periodKey
        )
        if (mode != null) data["mode"] = mode
        db.collection("periodScores").document(docId).set(data)
            .addOnSuccessListener {
                android.util.Log.d("PocketArcade", "syncPeriod OK  $docId score=$score")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("PocketArcade", "syncPeriod FAIL $docId", e)
            }
    }

    /**
     * Submits the player's score to the period-specific leaderboard collection
     * (`periodScores`). Called independently on every game-over — does NOT require
     * the score to qualify for the device-local top-N list.
     *
     * One document per (uid, game, mode, periodType, periodKey) is stored; only the
     * best score for that period is retained to keep the collection lean.
     */
    fun submitPeriodScore(
        uid: String,
        username: String,
        game: String,
        score: Int,
        country: String,
        state: String,
        mode: String? = null,
        avatarIndex: Int = 0,
        avatarColor: Int = 0
    ) {
        val now     = System.currentTimeMillis()
        val modeKey = mode ?: "_"

        fun submit(periodType: String, periodKey: String) {
            val docId = "$uid|$game|$modeKey|$periodType|$periodKey"
            val ref   = db.collection("periodScores").document(docId)
            val data  = hashMapOf<String, Any>(
                "uid"         to uid,
                "username"    to username,
                "game"        to game,
                "score"       to score,
                "country"     to country,
                "state"       to state,
                "avatarIndex" to avatarIndex,
                "avatarColor" to avatarColor,
                "timestamp"   to now,
                "periodType"  to periodType,
                "periodKey"   to periodKey
            )
            if (mode != null) data["mode"] = mode

            // Write unconditionally — skip the read-first approach which was
            // silently swallowing write failures (no error handler on ref.set()).
            // The doc ID already scopes to (uid, game, mode, period) so each
            // player has exactly one live document per period.
            ref.set(data)
                .addOnSuccessListener {
                    android.util.Log.d("PocketArcade", "periodScore OK  $docId score=$score")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("PocketArcade", "periodScore FAIL $docId", e)
                }
        }

        submit("week",  currentWeekKey())
        submit("month", currentMonthKey())
    }

    /** Fetch a user's country and state from the users collection. */
    fun fetchUserInfo(uid: String, onResult: (country: String, state: String) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                onResult(doc.getString("country") ?: "", doc.getString("state") ?: "")
            }
            .addOnFailureListener { onResult("", "") }
    }

    /** Fetch the best global score per game for a given user (base game key, ignoring mode). */
    fun fetchUserBestScores(uid: String, onResult: (Map<String, Int>) -> Unit) {
        db.collection("globalScores").whereEqualTo("uid", uid).get()
            .addOnSuccessListener { snap ->
                val best = mutableMapOf<String, Int>()
                snap.documents.forEach { doc ->
                    val game  = doc.getString("game") ?: return@forEach
                    val score = (doc.getLong("score") ?: 0L).toInt()
                    // Normalise: "brickbreaker" (ignore mode suffixes stored as separate field)
                    if (score > (best[game] ?: 0)) best[game] = score
                }
                onResult(best)
            }
            .addOnFailureListener { onResult(emptyMap()) }
    }

    fun fetchGlobal(
        game: String,
        mode: String? = null,
        limit: Long = 10,
        onResult: (List<GlobalEntry>) -> Unit
    ) {
        var q: Query = db.collection("globalScores").whereEqualTo("game", game)
        if (mode != null) q = q.whereEqualTo("mode", mode)
        q.orderBy("score", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .addOnSuccessListener { snap -> onResult(snap.documents.mapNotNull { it.toEntry() }) }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun fetchLocal(
        game: String,
        country: String,
        state: String,
        mode: String? = null,
        limit: Long = 10,
        onResult: (List<GlobalEntry>) -> Unit
    ) {
        var q: Query = db.collection("globalScores")
            .whereEqualTo("game", game)
            .whereEqualTo("country", country)
        if (state.isNotEmpty()) q = q.whereEqualTo("state", state)
        if (mode != null) q = q.whereEqualTo("mode", mode)
        q.orderBy("score", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .addOnSuccessListener { snap -> onResult(snap.documents.mapNotNull { it.toEntry() }) }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun fetchGlobalPage(
        game: String,
        mode: String? = null,
        pageSize: Long = 100,
        after: DocumentSnapshot? = null,
        onResult: (List<GlobalEntry>, DocumentSnapshot?) -> Unit
    ) {
        var q: Query = db.collection("globalScores").whereEqualTo("game", game)
        if (mode != null) q = q.whereEqualTo("mode", mode)
        q = q.orderBy("score", Query.Direction.DESCENDING).limit(pageSize)
        if (after != null) q = q.startAfter(after)
        q.get()
            .addOnSuccessListener { snap ->
                val docs = snap.documents
                val entries = docs.mapNotNull { it.toEntry() }
                onResult(entries, if (entries.size.toLong() >= pageSize) docs.lastOrNull() else null)
            }
            .addOnFailureListener { onResult(emptyList(), null) }
    }

    fun fetchLocalPage(
        game: String,
        country: String,
        state: String,
        mode: String? = null,
        pageSize: Long = 100,
        after: DocumentSnapshot? = null,
        onResult: (List<GlobalEntry>, DocumentSnapshot?) -> Unit
    ) {
        var q: Query = db.collection("globalScores")
            .whereEqualTo("game", game)
            .whereEqualTo("country", country)
        if (state.isNotEmpty()) q = q.whereEqualTo("state", state)
        if (mode != null) q = q.whereEqualTo("mode", mode)
        q = q.orderBy("score", Query.Direction.DESCENDING).limit(pageSize)
        if (after != null) q = q.startAfter(after)
        q.get()
            .addOnSuccessListener { snap ->
                val docs = snap.documents
                val entries = docs.mapNotNull { it.toEntry() }
                onResult(entries, if (entries.size.toLong() >= pageSize) docs.lastOrNull() else null)
            }
            .addOnFailureListener { onResult(emptyList(), null) }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toEntry(): GlobalEntry? =
        runCatching {
            GlobalEntry(
                uid         = getString("uid") ?: "",
                username    = getString("username") ?: "???",
                score       = (getLong("score") ?: 0L).toInt(),
                country     = getString("country") ?: "",
                state       = getString("state") ?: "",
                mode        = getString("mode"),
                timestamp   = getLong("timestamp") ?: 0L,
                avatarIndex = (getLong("avatarIndex") ?: 0L).toInt(),
                avatarColor = (getLong("avatarColor") ?: 0L).toInt()
            )
        }.getOrNull()
}

package com.pocketarcade.leaderboard

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class GlobalEntry(
    val username: String = "",
    val score: Int = 0,
    val country: String = "",
    val state: String = "",
    val mode: String? = null,
    val timestamp: Long = 0L
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

    fun ensureSignedIn(onReady: (uid: String) -> Unit, onError: () -> Unit = {}) {
        val user = auth.currentUser
        if (user != null) { onReady(user.uid); return }
        auth.signInAnonymously()
            .addOnSuccessListener { onReady(it.user!!.uid) }
            .addOnFailureListener { onError() }
    }

    fun claimUsername(
        username: String,
        uid: String,
        country: String,
        state: String,
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
                "username"  to username,
                "country"   to country,
                "state"     to state,
                "createdAt" to System.currentTimeMillis()
            ))
        }.addOnSuccessListener { onSuccess() }
         .addOnFailureListener { e ->
             if (e.message == "taken") onTaken() else onError()
         }
    }

    fun submitScore(
        uid: String,
        username: String,
        game: String,
        score: Int,
        country: String,
        state: String,
        mode: String? = null
    ) {
        val data = hashMapOf<String, Any>(
            "uid"       to uid,
            "username"  to username,
            "game"      to game,
            "score"     to score,
            "country"   to country,
            "state"     to state,
            "timestamp" to System.currentTimeMillis()
        )
        if (mode != null) data["mode"] = mode
        db.collection("globalScores").add(data)
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
            .addOnSuccessListener { snap ->
                onResult(snap.documents.mapNotNull { doc ->
                    runCatching {
                        GlobalEntry(
                            username  = doc.getString("username") ?: "???",
                            score     = (doc.getLong("score") ?: 0L).toInt(),
                            country   = doc.getString("country") ?: "",
                            state     = doc.getString("state") ?: "",
                            mode      = doc.getString("mode"),
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    }.getOrNull()
                })
            }
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
            .addOnSuccessListener { snap ->
                onResult(snap.documents.mapNotNull { doc ->
                    runCatching {
                        GlobalEntry(
                            username  = doc.getString("username") ?: "???",
                            score     = (doc.getLong("score") ?: 0L).toInt(),
                            country   = doc.getString("country") ?: "",
                            state     = doc.getString("state") ?: "",
                            mode      = doc.getString("mode"),
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    }.getOrNull()
                })
            }
            .addOnFailureListener { onResult(emptyList()) }
    }
}

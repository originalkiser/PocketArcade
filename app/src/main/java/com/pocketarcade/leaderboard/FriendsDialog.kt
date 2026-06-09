package com.pocketarcade.leaderboard

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.pocketarcade.AvatarUtils
import com.pocketarcade.R

fun showFriendsDialog(
    activity: AppCompatActivity,
    myUid: String,
    myUsername: String,
    myAvatarIndex: Int,
    myAvatarColor: Int
) {
    val view = LayoutInflater.from(activity).inflate(R.layout.dialog_friends_manage, null)
    val dialog = Dialog(activity)
    dialog.setContentView(view)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    val etUsername  = view.findViewById<EditText>(R.id.etFriendUsername)
    val btnAdd      = view.findViewById<TextView>(R.id.btnAddFriend)
    val tvStatus    = view.findViewById<TextView>(R.id.tvFriendAddStatus)
    val btnContacts = view.findViewById<TextView>(R.id.btnFindContacts)
    val btnShare    = view.findViewById<TextView>(R.id.btnShareProfile)
    val container   = view.findViewById<LinearLayout>(R.id.friendsListContainer)
    val progress    = view.findViewById<ProgressBar>(R.id.progressFriends)
    val tvEmpty     = view.findViewById<TextView>(R.id.tvFriendsEmpty)
    val btnClose    = view.findViewById<TextView>(R.id.btnFriendsClose)

    val db = FirebaseFirestore.getInstance()
    val dp = activity.resources.displayMetrics.density

    fun loadFollowing() {
        progress.visibility = View.VISIBLE
        container.removeAllViews()
        tvEmpty.visibility = View.GONE
        FriendsManager.getFollowing(myUid) { following ->
            if (following.isEmpty()) {
                activity.runOnUiThread {
                    progress.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                }
                return@getFollowing
            }
            val uids = following.map { it.uid }
            FriendsManager.checkMutuals(myUid, uids) { mutuals ->
                activity.runOnUiThread {
                    progress.visibility = View.GONE
                    container.removeAllViews()
                    following.forEach { entry ->
                        container.addView(buildFriendRow(activity, entry, entry.uid in mutuals, dp) {
                            FriendsManager.unfollow(myUid, entry.uid,
                                onSuccess = { loadFollowing() },
                                onError   = { activity.runOnUiThread { Toast.makeText(activity, "Error removing", Toast.LENGTH_SHORT).show() } }
                            )
                        })
                    }
                }
            }
        }
    }

    loadFollowing()

    btnAdd.setOnClickListener {
        val username = etUsername.text.toString().lowercase().trim()
        if (username.length < 3) {
            tvStatus.text = "Enter a valid username"
            tvStatus.setTextColor(Color.parseColor("#e74c3c"))
            tvStatus.visibility = View.VISIBLE
            return@setOnClickListener
        }
        if (username == myUsername) {
            tvStatus.text = "That's you!"
            tvStatus.setTextColor(Color.parseColor("#e74c3c"))
            tvStatus.visibility = View.VISIBLE
            return@setOnClickListener
        }
        btnAdd.isEnabled = false
        tvStatus.text = "Looking up..."
        tvStatus.setTextColor(Color.parseColor("#666688"))
        tvStatus.visibility = View.VISIBLE

        db.collection("usernames").document(username).get()
            .addOnSuccessListener { usernameDoc ->
                if (!usernameDoc.exists()) {
                    activity.runOnUiThread {
                        btnAdd.isEnabled = true
                        tvStatus.text = "User not found"
                        tvStatus.setTextColor(Color.parseColor("#e74c3c"))
                    }
                    return@addOnSuccessListener
                }
                val theirUid = usernameDoc.getString("uid") ?: run {
                    activity.runOnUiThread { btnAdd.isEnabled = true; tvStatus.text = "Error"; tvStatus.setTextColor(Color.parseColor("#e74c3c")) }
                    return@addOnSuccessListener
                }
                if (theirUid == myUid) {
                    activity.runOnUiThread { btnAdd.isEnabled = true; tvStatus.text = "That's you!"; tvStatus.setTextColor(Color.parseColor("#e74c3c")) }
                    return@addOnSuccessListener
                }
                db.collection("users").document(theirUid).get()
                    .addOnSuccessListener { userDoc ->
                        val theirAvatarIndex = (userDoc.getLong("avatarIndex") ?: 0L).toInt()
                        val theirAvatarColor = (userDoc.getLong("avatarColor") ?: 0L).toInt()
                        FriendsManager.follow(myUid, theirUid, username, theirAvatarIndex, theirAvatarColor,
                            onSuccess = {
                                activity.runOnUiThread {
                                    btnAdd.isEnabled = true
                                    etUsername.text.clear()
                                    tvStatus.text = "Now following $username!"
                                    tvStatus.setTextColor(Color.parseColor("#2ecc71"))
                                    loadFollowing()
                                }
                            },
                            onError = {
                                activity.runOnUiThread {
                                    btnAdd.isEnabled = true
                                    tvStatus.text = "Error - try again"
                                    tvStatus.setTextColor(Color.parseColor("#e74c3c"))
                                }
                            }
                        )
                    }
                    .addOnFailureListener {
                        activity.runOnUiThread {
                            btnAdd.isEnabled = true
                            tvStatus.text = "Error - try again"
                            tvStatus.setTextColor(Color.parseColor("#e74c3c"))
                        }
                    }
            }
            .addOnFailureListener {
                activity.runOnUiThread {
                    btnAdd.isEnabled = true
                    tvStatus.text = "Connection error"
                    tvStatus.setTextColor(Color.parseColor("#e74c3c"))
                }
            }
    }

    btnContacts.setOnClickListener {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_CONTACTS), 1234)
            Toast.makeText(activity, "Grant contacts permission, then tap again", Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
        progress.visibility = View.VISIBLE
        Thread {
            FriendsManager.findFromContacts(activity) { matches ->
                activity.runOnUiThread {
                    progress.visibility = View.GONE
                    if (matches.isEmpty()) {
                        Toast.makeText(activity, "No friends found in contacts yet", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    showContactMatchesDialog(activity, myUid, matches) { loadFollowing() }
                }
            }
        }.start()
    }

    btnShare.setOnClickListener {
        val text = "Add me as a friend on Pixel Parlor! My username is \"$myUsername\" 🎮"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(intent, "Share Profile"))
    }

    btnClose.setOnClickListener { dialog.dismiss() }
    dialog.show()
}

private fun showContactMatchesDialog(
    activity: AppCompatActivity,
    myUid: String,
    matches: List<Map<String, Any>>,
    onFollowed: () -> Unit
) {
    val dp = activity.resources.displayMetrics.density
    fun Int.px() = (this * dp).toInt()

    val scrollView = ScrollView(activity)
    val inner = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20.px(), 16.px(), 20.px(), 16.px())
        setBackgroundResource(R.color.surface)
    }

    val title = TextView(activity).apply {
        text = "${matches.size} FRIEND${if (matches.size == 1) "" else "S"} FOUND IN CONTACTS"
        setTextColor(Color.parseColor("#4f8ef7"))
        textSize = 13f
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, 12.px())
    }
    inner.addView(title)

    matches.forEach { m ->
        val uid         = m["uid"] as? String ?: return@forEach
        val username    = m["username"] as? String ?: return@forEach
        val avatarIndex = m["avatarIndex"] as? Int ?: 0
        val avatarColor = m["avatarColor"] as? Int ?: 0

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 7.px(), 0, 7.px())
        }

        row.addView(AvatarUtils.buildView(activity, avatarIndex, avatarColor, 28))

        val nameTV = TextView(activity).apply {
            text = username
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 8.px()
            }
        }
        row.addView(nameTV)

        if (uid != myUid) {
            val followBtn = TextView(activity).apply {
                text = "FOLLOW"
                setTextColor(Color.parseColor("#2ecc71"))
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setPadding(8.px(), 4.px(), 8.px(), 4.px())
                setOnClickListener {
                    FriendsManager.follow(myUid, uid, username, avatarIndex, avatarColor,
                        onSuccess = { activity.runOnUiThread { this.text = "✓ FOLLOWING"; isEnabled = false; onFollowed() } },
                        onError   = { activity.runOnUiThread { Toast.makeText(activity, "Error", Toast.LENGTH_SHORT).show() } }
                    )
                }
            }
            row.addView(followBtn)
        }

        inner.addView(row)
    }

    val closeBtn = TextView(activity).apply {
        text = "CLOSE"
        setTextColor(Color.parseColor("#4f8ef7"))
        textSize = 13f
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setPadding(0, 12.px(), 0, 0)
    }
    inner.addView(closeBtn)

    scrollView.addView(inner)

    val d = Dialog(activity)
    d.setContentView(scrollView)
    d.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }
    closeBtn.setOnClickListener { d.dismiss() }
    d.show()
}

private fun buildFriendRow(
    activity: AppCompatActivity,
    entry: FollowEntry,
    isMutual: Boolean,
    dp: Float,
    onRemove: () -> Unit
): View {
    fun Int.px() = (this * dp).toInt()
    val row = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 8.px(), 0, 8.px())
    }

    row.addView(AvatarUtils.buildView(activity, entry.avatarIndex, entry.avatarColor, 28))

    val nameText = if (isMutual) "${entry.username} ↔" else entry.username
    val nameTV = TextView(activity).apply {
        text = nameText
        setTextColor(if (isMutual) Color.parseColor("#2ecc71") else Color.WHITE)
        textSize = 14f
        typeface = Typeface.MONOSPACE
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = 8.px()
        }
    }
    row.addView(nameTV)

    val removeBtn = TextView(activity).apply {
        text = "REMOVE"
        setTextColor(Color.parseColor("#e74c3c"))
        textSize = 10f
        typeface = Typeface.MONOSPACE
        setPadding(6.px(), 3.px(), 6.px(), 3.px())
        setOnClickListener { onRemove() }
    }
    row.addView(removeBtn)

    return row
}

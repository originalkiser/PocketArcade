package com.pocketarcade.leaderboard

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.AvatarUtils
import com.pocketarcade.R
import com.pocketarcade.storage.PrefsManager

private val GAMES_INFO = listOf(
    Triple("snake",        R.drawable.ic_snake,        "SNAKE"),
    Triple("pong",         R.drawable.ic_pong,         "PONG"),
    Triple("asteroids",    R.drawable.ic_asteroids,    "ASTEROIDS"),
    Triple("brickbreaker", R.drawable.ic_brickbreaker, "BRKR BREAKER")
)

/**
 * Shows the mini user-profile dialog.
 *
 * @param myUid            Current user's UID (null if not signed in)
 * @param isFollowing      Whether we already follow this user
 * @param onFollowChanged  Called with (isNowFollowing) after a follow/unfollow action
 */
fun showPlayerProfileDialog(
    activity: AppCompatActivity,
    entry: GlobalEntry,
    myUid: String?,
    isFollowing: Boolean,
    onFollowChanged: (isNowFollowing: Boolean) -> Unit = {}
) {
    val dp          = activity.resources.displayMetrics.density
    val accentBlue  = activity.getColor(R.color.accent_blue)
    val accentGreen = activity.getColor(R.color.accent_green)
    val mutedColor  = activity.getColor(R.color.muted)

    val pView   = LayoutInflater.from(activity).inflate(R.layout.dialog_user_profile, null)
    val pDialog = Dialog(activity)
    pDialog.setContentView(pView)
    pDialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    // Avatar
    pView.findViewById<FrameLayout>(R.id.profileDialogAvatar)
        .addView(AvatarUtils.buildView(activity, entry.avatarIndex, entry.avatarColor, 56))

    // Username
    pView.findViewById<TextView>(R.id.tvProfileUsername).text = "@${entry.username}"

    // Location (abbreviated)
    val locText = when {
        entry.state.isNotEmpty()   -> "${abbreviateState(entry.country, entry.state)}, ${countryCode(entry.country)}"
        entry.country.isNotEmpty() -> countryCode(entry.country)
        else -> "—"
    }
    pView.findViewById<TextView>(R.id.tvProfileLocation).text = locText

    // Follow button
    var followingNow = isFollowing
    val btnFollow = pView.findViewById<TextView>(R.id.btnFollowUser)

    fun refreshFollowBtn() {
        when {
            entry.uid == myUid -> btnFollow.visibility = View.GONE
            followingNow -> {
                btnFollow.text = "FOLLOWING ✓"
                btnFollow.setTextColor(accentGreen)
            }
            else -> {
                btnFollow.text = "+ ADD FRIEND"
                btnFollow.setTextColor(Color.WHITE)
            }
        }
    }
    refreshFollowBtn()

    btnFollow.setOnClickListener {
        if (followingNow) {
            GlobalLeaderboard.ensureSignedIn(onReady = { uid ->
                FriendsManager.unfollow(uid, entry.uid,
                    onSuccess = {
                        followingNow = false
                        activity.runOnUiThread { refreshFollowBtn(); onFollowChanged(false) }
                    },
                    onError = {}
                )
            })
        } else {
            GlobalLeaderboard.ensureSignedIn(
                onReady = { uid ->
                    FriendsManager.follow(uid, entry.uid, entry.username,
                        entry.avatarIndex, entry.avatarColor,
                        onSuccess = {
                            followingNow = true
                            activity.runOnUiThread { refreshFollowBtn(); onFollowChanged(true) }
                        },
                        onError = {}
                    )
                },
                onError = {}
            )
        }
    }

    // Score highlights — fetch async, populate when ready
    val scoresContainer = pView.findViewById<LinearLayout>(R.id.profileScoresContainer)
    GlobalLeaderboard.fetchUserBestScores(entry.uid) { scores ->
        activity.runOnUiThread {
            if (scores.isEmpty()) {
                scoresContainer.visibility = View.GONE
                return@runOnUiThread
            }
            scoresContainer.removeAllViews()
            scoresContainer.visibility = View.VISIBLE

            // Separator
            val sep = View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                ).apply { topMargin = (8 * dp).toInt(); bottomMargin = (8 * dp).toInt() }
                setBackgroundColor(activity.getColor(R.color.border))
            }
            scoresContainer.addView(sep)

            val header = TextView(activity).apply {
                text = "BEST SCORES"
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTextColor(mutedColor)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (6 * dp).toInt() }
            }
            scoresContainer.addView(header)

            GAMES_INFO.forEach { (key, iconRes, label) ->
                val score = scores[key] ?: return@forEach
                val scoreStr = formatGlobalScore(key, score)
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = (4 * dp).toInt() }
                }
                // Left column: game name, right-aligned
                row.addView(TextView(activity).apply {
                    text = label
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setTextColor(mutedColor)
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                // Spacer between columns
                row.addView(View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams((8 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
                })
                // Right column: score, left-aligned
                row.addView(TextView(activity).apply {
                    text = scoreStr
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setTextColor(accentBlue)
                    gravity = Gravity.START
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                scoresContainer.addView(row)
            }
        }
    }

    pView.findViewById<TextView>(R.id.btnCloseProfile).setOnClickListener { pDialog.dismiss() }
    pDialog.show()
}

// ── Location helpers ──────────────────────────────────────────────────────────

private val US_STATE_ABBR = mapOf(
    "Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR",
    "California" to "CA", "Colorado" to "CO", "Connecticut" to "CT", "Delaware" to "DE",
    "Florida" to "FL", "Georgia" to "GA", "Hawaii" to "HI", "Idaho" to "ID",
    "Illinois" to "IL", "Indiana" to "IN", "Iowa" to "IA", "Kansas" to "KS",
    "Kentucky" to "KY", "Louisiana" to "LA", "Maine" to "ME", "Maryland" to "MD",
    "Massachusetts" to "MA", "Michigan" to "MI", "Minnesota" to "MN", "Mississippi" to "MS",
    "Missouri" to "MO", "Montana" to "MT", "Nebraska" to "NE", "Nevada" to "NV",
    "New Hampshire" to "NH", "New Jersey" to "NJ", "New Mexico" to "NM", "New York" to "NY",
    "North Carolina" to "NC", "North Dakota" to "ND", "Ohio" to "OH", "Oklahoma" to "OK",
    "Oregon" to "OR", "Pennsylvania" to "PA", "Rhode Island" to "RI", "South Carolina" to "SC",
    "South Dakota" to "SD", "Tennessee" to "TN", "Texas" to "TX", "Utah" to "UT",
    "Vermont" to "VT", "Virginia" to "VA", "Washington" to "WA", "Washington D.C." to "DC",
    "West Virginia" to "WV", "Wisconsin" to "WI", "Wyoming" to "WY"
)

private val CA_PROVINCE_ABBR = mapOf(
    "Alberta" to "AB", "British Columbia" to "BC", "Manitoba" to "MB",
    "New Brunswick" to "NB", "Newfoundland and Labrador" to "NL", "Northwest Territories" to "NT",
    "Nova Scotia" to "NS", "Nunavut" to "NU", "Ontario" to "ON", "Prince Edward Island" to "PE",
    "Quebec" to "QC", "Saskatchewan" to "SK", "Yukon" to "YT"
)

private val COUNTRY_CODES = mapOf(
    "Argentina" to "AR", "Australia" to "AU", "Austria" to "AT", "Belgium" to "BE",
    "Brazil" to "BR", "Canada" to "CA", "Chile" to "CL", "China" to "CN",
    "Colombia" to "CO", "Czech Republic" to "CZ", "Denmark" to "DK", "Egypt" to "EG",
    "Finland" to "FI", "France" to "FR", "Germany" to "DE", "Greece" to "GR",
    "Hungary" to "HU", "India" to "IN", "Indonesia" to "ID", "Ireland" to "IE",
    "Israel" to "IL", "Italy" to "IT", "Japan" to "JP", "Malaysia" to "MY",
    "Mexico" to "MX", "Netherlands" to "NL", "New Zealand" to "NZ", "Nigeria" to "NG",
    "Norway" to "NO", "Pakistan" to "PK", "Philippines" to "PH", "Poland" to "PL",
    "Portugal" to "PT", "Romania" to "RO", "Russia" to "RU", "Saudi Arabia" to "SA",
    "Singapore" to "SG", "South Africa" to "ZA", "South Korea" to "KR", "Spain" to "ES",
    "Sweden" to "SE", "Switzerland" to "CH", "Taiwan" to "TW", "Thailand" to "TH",
    "Turkey" to "TR", "Ukraine" to "UA", "United Kingdom" to "GB", "United States" to "US",
    "Vietnam" to "VN", "Other" to "??"
)

internal fun countryCode(country: String): String = COUNTRY_CODES[country] ?: country.take(6)

internal fun abbreviateState(country: String, state: String): String = when (country) {
    "United States" -> US_STATE_ABBR[state] ?: state
    "Canada"        -> CA_PROVINCE_ABBR[state] ?: state
    else            -> state
}

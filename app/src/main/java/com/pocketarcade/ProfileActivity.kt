package com.pocketarcade

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.leaderboard.GlobalLeaderboard
import com.pocketarcade.leaderboard.LocationData
import com.pocketarcade.leaderboard.LeaderboardManager
import com.pocketarcade.leaderboard.formatGlobalScore
import com.pocketarcade.leaderboard.showUsernameSetupDialog
import com.pocketarcade.storage.PrefsManager
import java.io.File

class ProfileActivity : AppCompatActivity() {

    private var selAvatarIndex = 0
    private var selAvatarColor = 0
    private var hasPhotoOverride = false
    private var isEditMode = false
    private var isDirty = false

    // Snapshot of original values for change detection
    private var origUsername = ""
    private var origCountry  = ""
    private var origState    = ""
    private var origAvatarIndex = 0
    private var origAvatarColor = 0

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val input = contentResolver.openInputStream(uri) ?: return@registerForActivityResult
            val file = File(filesDir, "profile_photo.jpg")
            file.outputStream().use { out -> input.copyTo(out) }
            PrefsManager.setProfilePhotoPath(this, file.absolutePath)
            hasPhotoOverride = true
            isDirty = true
            refreshAvatarPreview()
            refreshSaveButton()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not load photo", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        selAvatarIndex  = PrefsManager.getAvatarIndex(this)
        selAvatarColor  = PrefsManager.getAvatarColor(this)
        origAvatarIndex = selAvatarIndex
        origAvatarColor = selAvatarColor
        hasPhotoOverride = PrefsManager.getProfilePhotoPath(this) != null

        val btnBack         = findViewById<TextView>(R.id.btnProfileBack)
        val btnEdit         = findViewById<TextView>(R.id.btnEditProfile)
        val avatarContainer = findViewById<FrameLayout>(R.id.profileAvatarContainer)
        val btnChangeAvatar = findViewById<TextView>(R.id.btnProfileChangeAvatar)
        val btnUploadPhoto  = findViewById<TextView>(R.id.btnProfileUploadPhoto)
        val layoutAvatarBtns = findViewById<LinearLayout>(R.id.layoutAvatarEditButtons)
        val layoutNoProfile = findViewById<LinearLayout>(R.id.layoutNoProfile)
        val layoutForm      = findViewById<ScrollView>(R.id.layoutProfileForm)
        val btnCreate       = findViewById<TextView>(R.id.btnCreateProfile)
        val layoutViewMode  = findViewById<LinearLayout>(R.id.layoutViewMode)
        val layoutEditMode  = findViewById<LinearLayout>(R.id.layoutEditMode)
        val tvViewUsername  = findViewById<TextView>(R.id.tvViewUsername)
        val tvViewLocation  = findViewById<TextView>(R.id.tvViewLocation)
        val etUsername      = findViewById<EditText>(R.id.etProfileUsername)
        val tvUsernameError = findViewById<TextView>(R.id.tvProfileUsernameError)
        val spinnerCountry  = findViewById<Spinner>(R.id.spinnerProfileCountry)
        val layoutState     = findViewById<LinearLayout>(R.id.layoutProfileState)
        val spinnerState    = findViewById<Spinner>(R.id.spinnerProfileState)
        val btnSave         = findViewById<TextView>(R.id.btnSaveProfile)
        val tvStatus        = findViewById<TextView>(R.id.tvSaveStatus)
        val btnViewRecordBook = findViewById<TextView>(R.id.btnViewRecordBook)

        // Back press handling — confirm if editing with unsaved changes
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isEditMode && isDirty) {
                    AlertDialog.Builder(this@ProfileActivity)
                        .setTitle("Discard changes?")
                        .setMessage("You have unsaved changes. Discard them?")
                        .setPositiveButton("DISCARD") { _, _ -> finish() }
                        .setNegativeButton("KEEP EDITING", null)
                        .show()
                } else {
                    finish()
                }
            }
        })

        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        refreshAvatarPreview()

        btnChangeAvatar.setOnClickListener {
            hasPhotoOverride = false
            PrefsManager.setProfilePhotoPath(this, null)
            AvatarUtils.showAvatarPickerDialog(this, selAvatarIndex, selAvatarColor) { emojiIdx, colorIdx ->
                selAvatarIndex = emojiIdx
                selAvatarColor = colorIdx
                checkDirty(etUsername, spinnerCountry, spinnerState, layoutState)
                refreshAvatarPreview()
                refreshSaveButton()
            }
        }

        btnUploadPhoto.setOnClickListener {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        val isRegistered = PrefsManager.getGlobalUsername(this) != null
        if (!isRegistered) {
            layoutNoProfile.visibility = View.VISIBLE
            layoutForm.visibility = View.GONE
            btnCreate.setOnClickListener {
                GlobalLeaderboard.ensureSignedIn(
                    onReady = { uid ->
                        runOnUiThread {
                            showUsernameSetupDialog(this, uid, pendingScore = null, onSuccess = {
                                recreate()
                            })
                        }
                    },
                    onError = { msg ->
                        runOnUiThread {
                            Toast.makeText(this, "Sign-in failed: $msg", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
            return
        }

        // --- Registered user ---
        layoutNoProfile.visibility = View.GONE
        layoutForm.visibility = View.VISIBLE
        btnEdit.visibility = View.VISIBLE

        origUsername = PrefsManager.getGlobalUsername(this) ?: ""
        origCountry  = PrefsManager.getGlobalCountry(this)
        origState    = PrefsManager.getGlobalState(this)

        // Populate read-only view mode labels
        tvViewUsername.text = origUsername
        tvViewLocation.text = buildLocationLabel(origCountry, origState)

        // Setup spinners for edit mode
        val countryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, LocationData.countries)
        countryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCountry.adapter = countryAdapter
        spinnerCountry.setSelection(LocationData.countries.indexOf(origCountry).coerceAtLeast(0))

        fun updateStateSpinner() {
            val c = LocationData.countries[spinnerCountry.selectedItemPosition]
            val regions = LocationData.subregions(c)
            layoutState.visibility = if (regions.isNotEmpty()) View.VISIBLE else View.GONE
            if (regions.isNotEmpty()) {
                spinnerState.adapter = ArrayAdapter(
                    this, android.R.layout.simple_spinner_item, regions
                ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                val idx = regions.indexOf(origState)
                if (idx >= 0) spinnerState.setSelection(idx)
            }
        }
        updateStateSpinner()

        // Initialize edit fields with current values
        etUsername.setText(origUsername)

        // Change detection
        etUsername.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                checkDirty(etUsername, spinnerCountry, spinnerState, layoutState)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val spinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (spinnerCountry == p) updateStateSpinner()
                checkDirty(etUsername, spinnerCountry, spinnerState, layoutState)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spinnerCountry.onItemSelectedListener = spinnerListener
        spinnerState.onItemSelectedListener   = spinnerListener

        // EDIT button toggles edit mode
        btnEdit.setOnClickListener {
            if (!isEditMode) setEditMode(true, layoutViewMode, layoutEditMode, layoutAvatarBtns, btnEdit)
        }

        val usernameRegex = Regex("^[a-z0-9_]{3,20}$")

        btnSave.setOnClickListener {
            tvUsernameError.visibility = View.GONE
            tvStatus.visibility = View.GONE

            val newUsername = etUsername.text.toString().lowercase().trim()
            val newCountry  = LocationData.countries[spinnerCountry.selectedItemPosition]
            val newState    = if (layoutState.visibility == View.VISIBLE)
                LocationData.subregions(newCountry)[spinnerState.selectedItemPosition] else ""

            if (!usernameRegex.matches(newUsername)) {
                tvUsernameError.text = "3-20 chars: letters, numbers, underscore only"
                tvUsernameError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            val usernameChanged = newUsername != origUsername
            val locationChanged = newCountry != origCountry || newState != origState
            val avatarChanged   = selAvatarIndex != origAvatarIndex || selAvatarColor != origAvatarColor

            btnSave.isEnabled = false
            tvStatus.text = "Saving..."
            tvStatus.visibility = View.VISIBLE

            GlobalLeaderboard.ensureSignedIn(
                onReady = { uid ->
                    fun doAvatarThenLocation() {
                        if (avatarChanged) {
                            PrefsManager.setAvatarIndex(this, selAvatarIndex)
                            PrefsManager.setAvatarColor(this, selAvatarColor)
                            GlobalLeaderboard.updateUserAvatar(uid, selAvatarIndex, selAvatarColor)
                            origAvatarIndex = selAvatarIndex
                            origAvatarColor = selAvatarColor
                        }
                        if (locationChanged) {
                            GlobalLeaderboard.migrateLocation(uid, newCountry, newState,
                                onSuccess = {
                                    PrefsManager.setGlobalCountry(this, newCountry)
                                    PrefsManager.setGlobalState(this, newState)
                                    origCountry = newCountry; origState = newState
                                    runOnUiThread { onSaveSuccess(tvViewUsername, tvViewLocation, btnSave, tvStatus, btnEdit, layoutViewMode, layoutEditMode, layoutAvatarBtns) }
                                },
                                onError = {
                                    runOnUiThread {
                                        btnSave.isEnabled = true
                                        tvStatus.text = "Error saving location. Try again."
                                    }
                                }
                            )
                        } else {
                            runOnUiThread { onSaveSuccess(tvViewUsername, tvViewLocation, btnSave, tvStatus, btnEdit, layoutViewMode, layoutEditMode, layoutAvatarBtns) }
                        }
                    }

                    if (usernameChanged) {
                        GlobalLeaderboard.changeUsername(uid, origUsername, newUsername,
                            onSuccess = {
                                PrefsManager.setGlobalUsername(this, newUsername)
                                origUsername = newUsername
                                doAvatarThenLocation()
                            },
                            onTaken = {
                                runOnUiThread {
                                    btnSave.isEnabled = true
                                    tvStatus.visibility = View.GONE
                                    tvUsernameError.text = "Username taken — try another"
                                    tvUsernameError.visibility = View.VISIBLE
                                }
                            },
                            onError = {
                                runOnUiThread {
                                    btnSave.isEnabled = true
                                    tvStatus.text = "Error — check your connection"
                                }
                            }
                        )
                    } else {
                        doAvatarThenLocation()
                    }
                },
                onError = { msg ->
                    runOnUiThread {
                        btnSave.isEnabled = true
                        tvStatus.text = "Sign-in failed: $msg"
                    }
                }
            )
        }

        // Score highlights
        loadScoreHighlights()

        // Record Book link
        btnViewRecordBook.setOnClickListener {
            startActivity(Intent(this, RecordBookActivity::class.java))
        }
    }

    // ── Edit mode helpers ─────────────────────────────────────────────────────

    private fun setEditMode(
        on: Boolean,
        layoutViewMode: LinearLayout,
        layoutEditMode: LinearLayout,
        layoutAvatarBtns: LinearLayout,
        btnEdit: TextView
    ) {
        isEditMode = on
        layoutViewMode.visibility  = if (on) View.GONE  else View.VISIBLE
        layoutEditMode.visibility  = if (on) View.VISIBLE else View.GONE
        layoutAvatarBtns.visibility = if (on) View.VISIBLE else View.GONE
        btnEdit.text = if (on) "CANCEL" else "EDIT"
        if (on) {
            btnEdit.setOnClickListener {
                if (isDirty) {
                    AlertDialog.Builder(this)
                        .setTitle("Discard changes?")
                        .setMessage("You have unsaved changes. Discard them?")
                        .setPositiveButton("DISCARD") { _, _ ->
                            // Revert avatar
                            selAvatarIndex = origAvatarIndex
                            selAvatarColor = origAvatarColor
                            hasPhotoOverride = PrefsManager.getProfilePhotoPath(this) != null
                            refreshAvatarPreview()
                            isDirty = false
                            setEditMode(false, layoutViewMode, layoutEditMode, layoutAvatarBtns, btnEdit)
                        }
                        .setNegativeButton("KEEP EDITING", null)
                        .show()
                } else {
                    setEditMode(false, layoutViewMode, layoutEditMode, layoutAvatarBtns, btnEdit)
                }
            }
        } else {
            isDirty = false
            btnEdit.setOnClickListener {
                setEditMode(true, layoutViewMode, layoutEditMode, layoutAvatarBtns, btnEdit)
            }
        }
    }

    private fun checkDirty(
        etUsername: EditText,
        spinnerCountry: Spinner,
        spinnerState: Spinner,
        layoutState: LinearLayout
    ) {
        val newUsername = etUsername.text.toString().lowercase().trim()
        val newCountry  = LocationData.countries.getOrNull(spinnerCountry.selectedItemPosition) ?: ""
        val newState    = if (layoutState.visibility == View.VISIBLE)
            LocationData.subregions(newCountry).getOrNull(spinnerState.selectedItemPosition) ?: "" else ""

        isDirty = newUsername != origUsername
                || newCountry != origCountry
                || newState   != origState
                || selAvatarIndex != origAvatarIndex
                || selAvatarColor != origAvatarColor
                || (hasPhotoOverride != (PrefsManager.getProfilePhotoPath(this) != null))
        refreshSaveButton()
    }

    private fun refreshSaveButton() {
        val btnSave = findViewById<TextView>(R.id.btnSaveProfile) ?: return
        btnSave.visibility = if (isEditMode && isDirty) View.VISIBLE else View.GONE
    }

    private fun onSaveSuccess(
        tvViewUsername: TextView,
        tvViewLocation: TextView,
        btnSave: TextView,
        tvStatus: TextView,
        btnEdit: TextView,
        layoutViewMode: LinearLayout,
        layoutEditMode: LinearLayout,
        layoutAvatarBtns: LinearLayout
    ) {
        tvStatus.visibility = View.GONE
        isDirty = false
        tvViewUsername.text = origUsername
        tvViewLocation.text = buildLocationLabel(origCountry, origState)
        setEditMode(false, layoutViewMode, layoutEditMode, layoutAvatarBtns, btnEdit)
        loadScoreHighlights()
        Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show()
    }

    private fun buildLocationLabel(country: String, state: String): String {
        return when {
            state.isNotEmpty() && country.isNotEmpty() -> "$state, $country"
            country.isNotEmpty() -> country
            else -> "—"
        }
    }

    // ── Score Highlights ──────────────────────────────────────────────────────

    private fun loadScoreHighlights() {
        val container = findViewById<LinearLayout>(R.id.profileHighlightsContainer) ?: return
        container.removeAllViews()
        val dp = resources.displayMetrics.density

        data class GameRow(val key: String, val label: String, val color: Int, val iconRes: Int)
        val gameRows = listOf(
            GameRow(PrefsManager.GAME_SNAKE,        "SNAKE",       getColor(R.color.accent_blue),   R.drawable.ic_snake),
            GameRow(PrefsManager.GAME_PONG,         "PONG",        getColor(R.color.accent_red),    R.drawable.ic_pong),
            GameRow(PrefsManager.GAME_ASTEROIDS,    "ASTEROIDS",   getColor(R.color.accent_cyan),   R.drawable.ic_asteroids),
            GameRow(PrefsManager.GAME_BRICKBREAKER, "BRICK BREAKER", getColor(R.color.accent_yellow), R.drawable.ic_brickbreaker)
        )

        gameRows.forEach { game ->
            val score = PrefsManager.getHighScore(this, game.key)
            val scoreStr = when (game.key) {
                PrefsManager.GAME_PONG -> {
                    val wins = PrefsManager.getPongWins(this)
                    val plays = PrefsManager.getStatPlays(this, game.key)
                    val losses = (plays - wins).coerceAtLeast(0)
                    val wl = if (losses > 0) "%.1f".format(wins.toFloat() / losses) else if (wins > 0) "∞" else "—"
                    val bestGame = if (score >= 100) formatGlobalScore(game.key, score) else if (score > 0) "$score–?" else null
                    val best = if (bestGame != null) "  ·  Best $bestGame" else ""
                    "$wins W  ·  W/L $wl$best"
                }
                else -> if (score > 0) "%,d pts".format(score) else "—"
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = getDrawable(R.drawable.bg_game_tile)
                setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (6 * dp).toInt() }
            }
            val iconSize = (24 * dp).toInt()
            row.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                setImageResource(game.iconRes)
            })
            val nameTV = TextView(this).apply {
                text = game.label
                textSize = 12f
                typeface = Typeface.MONOSPACE
                setTextColor(game.color)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (10 * dp).toInt()
                }
            }
            row.addView(nameTV)
            val scoreTV = TextView(this).apply {
                text = scoreStr
                textSize = 12f
                typeface = Typeface.MONOSPACE
                setTextColor(getColor(R.color.muted))
                gravity = Gravity.END
            }
            row.addView(scoreTV)
            container.addView(row)
        }
    }

    // ── Avatar preview ────────────────────────────────────────────────────────

    private fun refreshAvatarPreview() {
        val container = findViewById<FrameLayout>(R.id.profileAvatarContainer) ?: return
        container.removeAllViews()
        val photoPath = if (hasPhotoOverride) PrefsManager.getProfilePhotoPath(this) else null
        if (photoPath != null) {
            val bmp = android.graphics.BitmapFactory.decodeFile(photoPath)
            if (bmp != null) {
                val iv = ImageView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setImageBitmap(bmp.toCircle())
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                container.addView(iv)
                return
            }
        }
        container.addView(AvatarUtils.buildView(this, selAvatarIndex, selAvatarColor, 80))
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.applyWindowBackground(this)
        val bg = ThemeManager.currentBgColor(this)
        findViewById<LinearLayout>(R.id.profileRootLayout)?.setBackgroundColor(bg)
    }
}

private fun Bitmap.toCircle(): Bitmap {
    val size = minOf(width, height)
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(this, ((size - width) / 2f), ((size - height) / 2f), paint)
    return output
}

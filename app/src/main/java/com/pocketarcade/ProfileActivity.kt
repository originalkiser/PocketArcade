package com.pocketarcade

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.pocketarcade.leaderboard.GlobalLeaderboard
import com.pocketarcade.leaderboard.LocationData
import com.pocketarcade.leaderboard.showUsernameSetupDialog
import com.pocketarcade.storage.PrefsManager
import java.io.File

class ProfileActivity : AppCompatActivity() {

    private var selAvatarIndex = 0
    private var selAvatarColor = 0
    private var hasPhotoOverride = false

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
            refreshAvatarPreview()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not load photo", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        selAvatarIndex = PrefsManager.getAvatarIndex(this)
        selAvatarColor = PrefsManager.getAvatarColor(this)
        hasPhotoOverride = PrefsManager.getProfilePhotoPath(this) != null

        val btnBack         = findViewById<TextView>(R.id.btnProfileBack)
        val avatarContainer = findViewById<FrameLayout>(R.id.profileAvatarContainer)
        val btnChangeAvatar = findViewById<TextView>(R.id.btnProfileChangeAvatar)
        val btnUploadPhoto  = findViewById<TextView>(R.id.btnProfileUploadPhoto)
        val layoutNoProfile = findViewById<LinearLayout>(R.id.layoutNoProfile)
        val layoutForm      = findViewById<ScrollView>(R.id.layoutProfileForm)
        val btnCreate       = findViewById<TextView>(R.id.btnCreateProfile)
        val etUsername      = findViewById<EditText>(R.id.etProfileUsername)
        val tvUsernameError = findViewById<TextView>(R.id.tvProfileUsernameError)
        val spinnerCountry  = findViewById<Spinner>(R.id.spinnerProfileCountry)
        val layoutState     = findViewById<LinearLayout>(R.id.layoutProfileState)
        val spinnerState    = findViewById<Spinner>(R.id.spinnerProfileState)
        val btnSave         = findViewById<TextView>(R.id.btnSaveProfile)
        val tvStatus        = findViewById<TextView>(R.id.tvSaveStatus)

        btnBack.setOnClickListener { finish() }

        refreshAvatarPreview()

        btnChangeAvatar.setOnClickListener {
            hasPhotoOverride = false
            PrefsManager.setProfilePhotoPath(this, null)
            AvatarUtils.showAvatarPickerDialog(this, selAvatarIndex, selAvatarColor) { emojiIdx, colorIdx ->
                selAvatarIndex = emojiIdx
                selAvatarColor = colorIdx
                refreshAvatarPreview()
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

        layoutNoProfile.visibility = View.GONE
        layoutForm.visibility = View.VISIBLE

        val currentUsername = PrefsManager.getGlobalUsername(this) ?: ""
        val currentCountry  = PrefsManager.getGlobalCountry(this)
        val currentState    = PrefsManager.getGlobalState(this)

        etUsername.setText(currentUsername)

        val countryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, LocationData.countries)
        countryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCountry.adapter = countryAdapter
        spinnerCountry.setSelection(LocationData.countries.indexOf(currentCountry).coerceAtLeast(0))

        fun updateStateSpinner() {
            val c = LocationData.countries[spinnerCountry.selectedItemPosition]
            val regions = LocationData.subregions(c)
            layoutState.visibility = if (regions.isNotEmpty()) View.VISIBLE else View.GONE
            if (regions.isNotEmpty()) {
                spinnerState.adapter = ArrayAdapter(
                    this, android.R.layout.simple_spinner_item, regions
                ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                val stateIdx = regions.indexOf(currentState)
                if (stateIdx >= 0) spinnerState.setSelection(stateIdx)
            }
        }
        updateStateSpinner()
        spinnerCountry.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = updateStateSpinner()
            override fun onNothingSelected(p: AdapterView<*>?) {}
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

            val usernameChanged = newUsername != currentUsername
            val locationChanged = newCountry != currentCountry || newState != currentState
            val avatarChanged   = selAvatarIndex != PrefsManager.getAvatarIndex(this) ||
                                  selAvatarColor  != PrefsManager.getAvatarColor(this)

            if (!usernameChanged && !locationChanged && !avatarChanged) {
                tvStatus.text = "No changes to save."
                tvStatus.visibility = View.VISIBLE
                return@setOnClickListener
            }

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
                        }
                        if (locationChanged) {
                            GlobalLeaderboard.migrateLocation(uid, newCountry, newState,
                                onSuccess = {
                                    PrefsManager.setGlobalCountry(this, newCountry)
                                    PrefsManager.setGlobalState(this, newState)
                                    runOnUiThread {
                                        btnSave.isEnabled = true
                                        tvStatus.text = "Saved!"
                                    }
                                },
                                onError = {
                                    runOnUiThread {
                                        btnSave.isEnabled = true
                                        tvStatus.text = "Error saving location. Try again."
                                    }
                                }
                            )
                        } else {
                            runOnUiThread {
                                btnSave.isEnabled = true
                                tvStatus.text = "Saved!"
                            }
                        }
                    }

                    if (usernameChanged) {
                        GlobalLeaderboard.changeUsername(uid, currentUsername, newUsername,
                            onSuccess = {
                                PrefsManager.setGlobalUsername(this, newUsername)
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
    }

    private fun refreshAvatarPreview() {
        val container = findViewById<FrameLayout>(R.id.profileAvatarContainer) ?: return
        container.removeAllViews()
        val photoPath = if (hasPhotoOverride) PrefsManager.getProfilePhotoPath(this) else null
        if (photoPath != null) {
            val bmp = BitmapFactory.decodeFile(photoPath)
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

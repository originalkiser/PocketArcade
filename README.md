# Pixel Parlor

A retro-themed Android game collection with Snake, Pong, Asteroids, and Brick Breaker.
Built in pure Kotlin with no third-party game engines.

## Games

| Game | Controls | Objective |
|------|----------|-----------|
| **Snake** | Swipe or D-pad | Eat food, grow longer, don't hit walls/self |
| **Pong** | Drag paddle | First to 7 points wins |
| **Asteroids** | Virtual joystick (L) + Fire (R) | Destroy asteroids, survive waves |
| **Brick Breaker** | Drag paddle, tap/swipe to aim | Clear all 10 levels |

## Setup

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- An Android device or emulator (API 26+)

### 1. Clone / Open the project
Open `D:\RetroArcade` in Android Studio. It will sync Gradle automatically.

### 2. Press Start 2P font
The font is configured as a **Google Downloadable Font** (`res/font/press_start_2p.xml`).
It downloads automatically on the first launch when internet is available.

**To bundle it offline instead:**
1. Download `PressStart2P-Regular.ttf` from https://fonts.google.com/specimen/Press+Start+2P
2. Place it at `app/src/main/res/font/press_start_2p.ttf`
3. Replace `res/font/press_start_2p.xml` with:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <font-family xmlns:android="http://schemas.android.com/apk/res/android">
       <font android:fontStyle="normal" android:fontWeight="400"
             android:font="@font/press_start_2p" />
   </font-family>
   ```

### 3. AdMob configuration
**Development** — test ad unit IDs are pre-configured in `app/build.gradle.kts`:
```kotlin
buildConfigField("String", "ADMOB_APP_ID",      "\"ca-app-pub-3940256099942544~3347511713\"")
buildConfigField("String", "BANNER_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
```

**Production** — swap these values for your real IDs:
1. Go to https://admob.google.com and create an app + banner ad unit
2. Replace the values above in `app/build.gradle.kts`
3. Also update `res/values/strings.xml` → `admob_app_id`

> If no internet is available or the ad fails to load, a rotating fake-ad banner
> is shown automatically (no action required).

### 4. Google Play Billing (Ad-Free IAP)
**Setup:**
1. Create an app in Google Play Console
2. Under *Monetize → Products → In-app products*, create a one-time product with ID `ad_free`, price $0.99
3. Publish to internal testing track
4. The `BILLING_SKU_AD_FREE` build config field in `app/build.gradle.kts` is already set to `"ad_free"`

**Testing:**
- Add yourself as a license tester in Play Console → *Setup → License testing*
- Install via internal testing track to test billing end-to-end

### 5. Signing for release
In Android Studio: *Build → Generate Signed Bundle/APK*

Or add to `app/build.gradle.kts`:
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("path/to/keystore.jks")
        storePassword = "..."
        keyAlias = "..."
        keyPassword = "..."
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
    }
}
```

## Features

### Ad System
- **Real ads**: AdMob banner (50dp) pinned to top of every screen
- **Fake ads**: Shown when offline or ad fails — rotating funny messages every 9 seconds
- **Ad-Free IAP**: $0.99 one-time via Google Play Billing; hides the banner permanently
- **Upsell card**: Custom full-screen dialog shown every 3–5 sessions with 3-second forced wait

### Demo / Attract Mode
- 15 seconds idle on any game screen → AI plays automatically with "▶ DEMO" watermark
- Each game has its own AI:
  - **Snake**: Manhattan-distance greedy pathfinding with random jitter
  - **Pong**: Both paddles AI-controlled
  - **Asteroids**: Steers toward nearest asteroid
  - **Brick Breaker**: Auto-aims and tracks ball
- Demo togglable in Settings (on by default)
- Tap anywhere to exit demo and start a real game

### High Scores
- Stored locally in SharedPreferences — no accounts, no cloud sync
- Displayed on home screen tiles and in-game headers
- Resettable in Settings

## Project Structure

```
app/src/main/java/com/pocketarcade/
├── MainActivity.kt              Home screen + upsell
├── SettingsActivity.kt          Settings + IAP management
├── ads/
│   ├── AdManager.kt             Banner ad + fallback logic
│   └── FakeAdView.kt            Rotating retro fake-ad banner
├── billing/
│   └── BillingManager.kt        Google Play Billing wrapper
├── storage/
│   └── PrefsManager.kt          SharedPreferences helpers
└── games/
    ├── snake/     SnakeActivity + SnakeView
    ├── pong/      PongActivity + PongView
    ├── asteroids/ AsteroidsActivity + AsteroidsView
    └── brickbreaker/ BrickBreakerActivity + BrickBreakerView
```

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Google Mobile Ads SDK | 23.0.0 | AdMob banner ads |
| Play Billing Library | 6.2.0 | $0.99 ad-free IAP |
| AndroidX AppCompat | 1.7.0 | Activity base classes |
| Material Components | 1.12.0 | Dialog / Switch styling |

## Architecture Notes

- **No game engine** — pure Android Canvas + SurfaceView + Thread
- Pong, Asteroids, and Brick Breaker use `SurfaceView` + background `Thread` for smooth 60fps rendering
- Snake uses a `Handler` tick loop (120ms) since it's not frame-rate sensitive
- Each game is a standalone `Activity` + custom `View` — no shared base class
- `PrefsManager` is a Kotlin `object` (singleton) backed by `SharedPreferences`
- `AdManager` is stateless — call `populateBannerContainer()` each time a screen resumes

## Retro Color Palette

| Name | Hex | Usage |
|------|-----|-------|
| Background | `#0f1117` | Screen/canvas background |
| Surface | `#1a1d2e` | Card backgrounds |
| Card | `#252844` | Game tiles |
| Border | `#3a4a6e` | Grid dots, stroke lines |
| Accent Blue | `#4f8ef7` | Snake, player paddle, primary actions |
| Accent Red | `#e74c3c` | Food, AI paddle, HP-3 bricks |
| Accent Green | `#2ecc71` | HP-1 bricks, win states |
| Accent Yellow | `#f1c40f` | Bullets, fake ads, upsell |
| Accent Cyan | `#00d4ff` | Ship, Asteroids theme |

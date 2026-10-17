# Kotlin Multiplatform migration plan

Status: **Phase 0 done, Phase 1 in progress** (October 2026; integration branch `kmp-migration`)

## Goal

Turn qdeq into a Kotlin Multiplatform (KMP) app for **Android and iOS** (iPhone/iPad). The UI is
shared with **Compose Multiplatform**, reusing the existing Compose screens. Desktop and Web are out
of scope.

The migration is done in phases so that the Android app keeps working after every PR.

## Current state

55 Kotlin files, ~6,800 lines, single `app` module. The UI is fully Compose, except for two
`AndroidView` wrappers (waveform and cue point). State is StateFlow/SharedFlow, DI is Koin,
persistence is Room plus SharedPreferences.

## What is shared and what is platform-specific

| Area | Today | In KMP |
|---|---|---|
| Compose screens (player, queue, playlists, file browser, settings) | ~2,500 lines, all Compose | **Shared.** Mainly switching `R.string` / `R.drawable` / `R.array` to Compose Multiplatform resources (`Res.*`). |
| ViewModels (player, queue, playlists, file browser, settings) | `AndroidViewModel`, Koin, StateFlow | **Shared** with JetBrains' multiplatform `lifecycle-viewmodel` and Koin multiplatform. `Handler`, `LruCache`, `String.format` and `Toast` need common replacements. |
| Playlist database | Room with kapt | **Room KMP** with KSP. Same `qplayer` database file, so no data migration. |
| Settings storage (`PreferencesDataSource`) | SharedPreferences `QDEQ` + Gson | **multiplatform-settings**, which uses the same SharedPreferences file on Android (no migration) and NSUserDefaults on iOS. kotlinx.serialization replaces Gson. |
| Logging | Timber | **Kermit.** |
| Licenses screen | AboutLibraries Compose M3 | **Shared** (AboutLibraries supports KMP). |
| Audio playback | Media3/ExoPlayer; `QDeqPlayer` exposes the Media3 `Player` | **Platform.** Android keeps Media3. iOS uses AVAudioEngine: varispeed (pitch follows speed, "vinyl") and time-pitch (master tempo). |
| Background playback, notification, lock screen | `MediaSessionService` | **Platform.** iOS: background audio session, `MPRemoteCommandCenter`, `MPNowPlayingInfoCenter`. |
| Audio decoding (waveform, scratch) | MediaExtractor + MediaCodec | **Platform.** iOS: `AVAudioFile` already returns decoded PCM. |
| Scratch engine | AudioTrack + scratch math (follower, interpolation, DC blocker) | **Split.** The math becomes shared Kotlin; only the audio output is per platform. iOS: `AVAudioSourceNode`. |
| File browser, SD card | `java.io.File`, storage volumes | **Platform, different concept on iOS** (sandbox, no free file system). See open decisions. |
| Jog wheel vibration | Vibrator | **Platform.** iOS: `UIImpactFeedbackGenerator` / Core Haptics. |
| Crash reporting | Firebase Crashlytics (Android SDK) | **Platform** behind a shared `CrashReporter` interface. iOS: Crashlytics SDK, or Sentry (official KMP SDK) on both platforms. |
| Waveform and cue point views | `AndroidView` wrappers | **Must become Compose Canvas first.** |
| Settings / Licenses / WebView screens | Separate Activities, WebView for privacy/imprint | **Shared:** one app with Compose navigation; privacy and imprint as Compose text screens (removes the WebView). |
| Orientation lock | `requestedOrientation` | **Platform.** iOS: supported orientations in Info.plist (iPhone portrait, iPad landscape). The two-pane layout is already chosen by window size. |

## Phases

### Phase 0: prepare inside the Android app (4–6 PRs, each testable on Android)

1. **Toolchain**
   - Kotlin 1.9.21 → 2.x (Compose compiler as a Gradle plugin).
   - Matching AGP and Gradle versions; Java 17 instead of 1.8.
   - Version catalog (`gradle/libs.versions.toml`).
   - kapt → KSP for Room.
2. **Finish the Compose migration:** waveform and cue point views as Compose Canvas.
3. **Single activity with navigation:** Settings, Licenses, Privacy and Imprint become screens
   inside the app instead of separate Activities.
4. **Replace Android-only libraries**
   - Gson → kotlinx.serialization (must read the queue JSON already stored on devices).
   - Timber → Kermit.
   - SharedPreferences → multiplatform-settings (same `QDEQ` file).
   - Koin → Koin multiplatform artifacts.
   - `File` → `kotlinx-io` paths or plain strings.
   - Common replacements for `Handler`, `LruCache`, `String.format`, `Toast`.
5. **Platform interfaces**
   - `AudioPlayer` (no Media3 types in its API), `PcmDecoder`, `ScratchOutput` (scratch math
     separated from the audio output), `JogHaptics`, `StorageProvider`, `Permissions`,
     `CrashReporter`, `PlatformInfo`.
   - ViewModels depend only on these interfaces; `AndroidViewModel` → `ViewModel`.

### Phase 1: multiplatform module (2–3 PRs, Android only)

- Module layout: `shared` (KMP: `commonMain`, `androidMain`, `iosMain`) and `androidApp` (the
  former `app`; AGP 9 doesn't allow the Android app module to be a KMP module). `iosApp` follows in
  Phase 2.
- **1a (done):** models, settings, Room KMP database (suspending DAOs, same `qplayer` file),
  ViewModels, `PlaybackCoordinator`, platform interfaces and helpers in `shared/commonMain`; their
  unit tests in `androidHostTest`. The iOS targets are compiled from now on, so platform code can't
  slip into `commonMain`. The Android implementations (media service, scratch engine, decoder,
  file storage, Firebase, Koin `platformModule`/`dataModule`) stay in `androidApp`.
- **1b (resources, done):** Compose Multiplatform plugin; strings and arrays (German stays the default,
  English in `values-en`), drawables and the privacy/imprint HTML as Compose Multiplatform
  resources in `shared` (`Res.*`). Only the launcher icon, theme, notification icon and strings
  stay Android resources. The tablet dimensions (`values-sw600dp`/`-sw720dp`) became `Dimens`,
  chosen in code by the screen's smallest width, since Compose Multiplatform resources have no
  screen-width qualifiers.
- **1c (screens, done):** the Compose UI is in `shared/commonMain` (JetBrains builds of Material 3,
  Navigation 3 and lifecycle; on Android the Jetpack versions stay). Platform functions
  (`expect`/`actual`): the storage permission gate and the licenses screen (AboutLibraries list on
  Android, placeholder on iOS). The privacy / imprint HTML is converted by a small common function
  instead of Android's `AnnotatedString.fromHtml`. `androidApp` keeps only the activity, application,
  Koin platform modules and the Android implementations.

Result: the Android app is "KMP inside" and behaves exactly as before.

### Phase 2: iOS app (largest phase)

**2a (done): first iOS app.** `iosApp/` (SwiftUI shell, project generated with XcodeGen from
`iosApp/project.yml`; the `.xcodeproj` is committed) hosts the shared UI via `MainViewController()`
(`shared/src/iosMain`). Koin `iosModules`: settings in NSUserDefaults, the Room database in
Application Support (bundled SQLite driver), the file browser on the app's Documents folder
(`PathStorageProvider`, kotlinx-io; visible in the Files app / Finder). Placeholders: player
(no audio — tracks load, play/pause only switch the state), scratching, waveform, haptics, crash
reporting. Build and run: open `iosApp/iosApp.xcodeproj` in Xcode, pick a simulator, Run (the
build phase calls `./gradlew :shared:embedAndSignAppleFrameworkForXcode`).

Still to do:

- `iosApp` Xcode project hosting the shared Compose UI (`ComposeUIViewController`).
- iOS implementations of the platform interfaces:
  - player and decoder (AVAudioEngine, AVAudioFile);
  - lock screen and remote controls, background audio (Info.plist `UIBackgroundModes: audio`);
  - haptics, settings storage, crash reporting;
  - file access: the app's Documents folder (filled via Finder / the Files app) plus folders the
    user picks, remembered with security-scoped bookmarks.

### Phase 3: iOS scratch engine and polish

- **Scratch output on iOS** via `AVAudioSourceNode`. Highest-risk item: real-time audio callbacks in
  Kotlin/Native must not allocate or wait for the garbage collector. Fallback: write this small
  part in Swift.
- iPhone and iPad layout fine-tuning, app icon from the existing vector, TestFlight.

## Open decisions (before Phase 2)

1. **iOS music source:** the app's Documents folder plus folders picked in the Files app
   (recommended, closest to today's file browser), or the Music library (only non-DRM tracks are
   playable, so most Apple Music downloads won't work).
2. **Crash reporting on iOS:** Crashlytics for iOS, or Sentry on both platforms. Either way the
   privacy statement (`assets/privacy.html`) needs an update.
3. **Apple developer account and Mac with Xcode:** a free account can run the app on your own
   device, but the installed app expires after 7 days. TestFlight and the App Store need the paid
   membership.

## Testing

Android: as today, on device. iOS: Xcode with an iPhone/iPad or the iOS Simulator.

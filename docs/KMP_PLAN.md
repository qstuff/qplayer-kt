# Kotlin Multiplatform migration plan

Status: **Phases 0–3 done, open tasks below** (October 2026; integration branch `kmp-migration`)

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

**2b (done): iOS player.** `IosAudioPlayer` on AVAudioEngine (player node → varispeed →
time-pitch: vinyl pitch or master tempo), seeking by rescheduling the file, background audio,
lock screen / headset controls and Now Playing info, pause on interruptions. `IosWaveformGenerator`
reads the file with AVAudioFile (same overview as Android). Track and folder ids on iOS are
virtual paths (`/Documents/…`, `IosPaths`), because the app container's real path changes with
every install and app update.

Still to do:

- `iosApp` Xcode project hosting the shared Compose UI (`ComposeUIViewController`).
- iOS implementations of the platform interfaces:
  - player and decoder (AVAudioEngine, AVAudioFile);
  - lock screen and remote controls, background audio (Info.plist `UIBackgroundModes: audio`);
  - haptics, settings storage, crash reporting;
  - file access: the app's Documents folder (filled via Finder / the Files app) plus folders the
    user picks, remembered with security-scoped bookmarks.

### Phase 3: iOS scratch engine and polish

**3a (done): iOS scratch engine.** `IosScratchEngine`: AVAudioFile decodes the track into one
native 16-bit PCM block (the length is known up front; freed explicitly), an `AVAudioSourceNode`
renders with the same follower / interpolation / DC blocker as Android, in Kotlin: the render
callback reads only native memory and primitive fields. The output engine runs only while
scratching (+2 s), so it doesn't keep the app awake in the background. If real devices show
dropouts, the render callback moves to Swift (the fallback below).

**3b (done): iOS polish.** Jog wheel haptics via the Taptic Engine (`IosHapticFeedback`), app
icon from the Android launcher vector, licenses screen (AboutLibraries list of the iOS
dependencies, exported at build time), privacy statement with Android and iOS sections.

- **Scratch output on iOS** via `AVAudioSourceNode`. Highest-risk item: real-time audio callbacks in
  Kotlin/Native must not allocate or wait for the garbage collector. Fallback: write this small
  part in Swift.
- iPhone and iPad layout fine-tuning, TestFlight.

## Decisions

1. **iOS music source: Files** (decided October 2026). The app's Documents folder (visible in the
   Files app and Finder, already in the preview app) plus folders the user links with the system
   folder picker (`UIDocumentPickerViewController`): iCloud Drive, USB drives, network shares,
   other file providers. Access to linked folders is kept with security-scoped bookmarks; each
   linked folder becomes an entry in the file browser's storage list (`StorageProvider.roots()`).
   Full access to the audio data, so pitch, master tempo, scratching and waveform work as on
   Android; M3U playlists keep working.
   - Not chosen for now: the Music library (MediaPlayer framework). Only downloaded tracks without
     DRM are playable (purchased / synced from a Mac), Apple Music subscription tracks are not.
     Can be added later as another source. Apple Music via MusicKit is ruled out: playback only
     through Apple's player (no pitch, no scratching, no access to the audio).

## Open decisions

1. **Crash reporting on iOS:** Crashlytics for iOS, or Sentry on both platforms. Either way the
   privacy statement (`files/privacy.html`) needs an update.
2. **Apple developer account:** a free account can run the app on your own device, but the
   installed app expires after 7 days. TestFlight and the App Store need the paid membership.
3. **iCloud / computer backup on iOS:** by default all app data is part of the device backup —
   settings, queue, playlists and also the music in the app's Documents folder (uses the user's
   iCloud storage). Android's Auto Backup was turned off (PR #66). Options: keep (current, and so
   described in the privacy statement), exclude everything, or exclude only the music. Excluding
   is possible per folder (`NSURLIsExcludedFromBackupKey`); the privacy text then changes.

## Open tasks

Not yet scheduled; roughly by benefit.

**Done since:** importing files on iOS (PR #79, by Miriam Busch): audio files and M3U playlists
handed over by other apps ("Open in…", share sheet, AirDrop, also from iCloud Drive and other file
providers) are copied into the Documents folder (`IosFileImport`); the file browser refreshes when
the app returns to the foreground.

**iOS features**
- **Linked folders:** an "add folder" action in the file browser with the system folder picker
  (`UIDocumentPickerViewController`): iCloud Drive, USB drives, network shares, other providers.
  Access kept with security-scoped bookmarks; each folder becomes a storage root with virtual ids
  `/Linked/<name>/…` (like `/Documents/…`, see `IosPaths`). Unlike the import (#79), whole folders
  are browsed in place — no copies, always current.
- **Crash reporting on iOS** (after decision 1).
- **Test on real devices** (needs decision 2): scratch dropouts / latency (if any, move the render
  callback to Swift), haptics, background playback and lock screen, iPad layout, performance of
  the waveform and scratch decode in a release build.
- **Release setup:** app version / build number (`CFBundleShortVersionString` / `CFBundleVersion`
  are fixed in `iosApp/project.yml`), signing, TestFlight, App Store metadata.

- **Xcode project format:** XcodeGen writes an older project format (`objectVersion` 51), which
  Xcode converts locally on opening (an uncommitted change to `project.pbxproj`). Set
  `options.projectFormat` in `iosApp/project.yml` to the current format.

**iOS glitches seen in the simulator**
- The first tap right after the app starts is sometimes lost (UIKit logs "Failed to receive
  system gesture state notification before next touch").
- The overview waveform takes ~10 s for a 4-minute track in the simulator debug build; check in a
  release build on a device, otherwise reuse the scratch decode (see below).

**Both platforms**
- **Scratch fill-up:** after loading, only the start of a track can be scratched until the
  background decode catches up. A first attempt (one decode for waveform + scratch, batched
  writes) is parked on branch `feature/scratch-single-decode` (still the old `app/` layout); it didn't noticeably help on the
  Tab A. Ideas: decode from the playback position first, show the scratchable range.
- **Licenses screen:** the license chips use a colour our colour scheme doesn't define (dark
  pink); give them the accent colour.
- **Unit tests to `commonTest`** (kotlin.test), so they also run on the iOS simulator
  (`iosSimulatorArm64Test`); today they run on the JVM only (`androidHostTest`).
- Deprecation warnings: `TabRow` → `PrimaryTabRow`, `ContextCompat.getExternalFilesDirs`.

## Testing

Android: as today, on device. iOS: Xcode with an iPhone/iPad or the iOS Simulator.

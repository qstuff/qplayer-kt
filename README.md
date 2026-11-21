# qdeq

A DJ-style music player for **Android and iOS** (phone and tablet): play your own audio files with
pitch control, master tempo, cue point and a touch jog wheel that can bend the tempo or scratch
like a record.

qdeq is the Kotlin rewrite of the original qplayer. Since version 4 it is a
**Kotlin Multiplatform** app: UI, ViewModels, data and most of the logic are shared; only audio
playback, decoding, file access and haptics are platform code.

## Features

- **Pitch** fader and fine-tuning buttons with selectable range (±8 / 15 / 50 / 100 %), reset,
  and **master tempo** (change the speed, keep the key).
- **Jog wheel** with three modes — speed by angle, speed by velocity, and **scratch** (moves the
  playback position directly, like vinyl) — adjustable sensitivity and optional haptic feedback.
- **Cue** point, previous / next, repeat (one / all) and shuffle.
- **Waveforms:** an overview of the whole track, also used as the seek bar, that fills in while it's
  created. On tablets also a **scrolling waveform** of exactly **4 bars** around the playhead, with
  beat and bar lines and the current **BPM** (following the pitch); drag it to move the track like
  a record (audible scratching in scratch mode). Waveforms are saved, so a track loaded again
  shows them at once, and the next queue track's is prepared in the background.
- **BPM / beat detection** for tracks with a steady beat (88–176 BPM, the usual DJ range).
- **Queue** (reorderable, swipe to remove), **playlists** (stored in the app, M3U import) and a
  **file browser** for local storage and SD cards (Android) or the app's Documents folder (iOS).
- Background playback with media notification / lock screen controls.
- **Layouts:** on phones *qstuff dj* (jog wheel on top, buttons below, pull-up tab sheet) or
  *transparent* (the original qplayer layout: fader and buttons beside the jog wheel, tabs below) —
  chosen in Settings; on tablets two panes in landscape.
- Opt-in crash reporting.

## Project structure

| Module | Contents |
|---|---|
| [`shared/`](shared) | Kotlin Multiplatform module: Compose Multiplatform UI, ViewModels, Room database, settings, Koin DI, resources. `commonMain` holds almost everything; `androidMain` / `iosMain` contain the few platform `actual`s. |
| [`androidApp/`](androidApp) | Android app: `Application`, the single `PlayerActivity`, Media3 playback service, MediaCodec decoding (waveform, scratch), scratch audio output, storage access, haptics, Firebase Crashlytics. |
| [`iosApp/`](iosApp) | iOS app (SwiftUI host for the shared Compose UI). The iOS platform code itself is Kotlin in `shared/src/iosMain`: AVAudioEngine playback (varispeed / time-pitch), `AVAudioFile` decoding, scratch engine, haptics, file import. |
| [`docs/`](docs) | [KMP migration plan](docs/KMP_PLAN.md) with decisions and open tasks. |

Main libraries: Compose Multiplatform, Jetpack Navigation 3, JetBrains lifecycle-viewmodel,
Koin, Room KMP, multiplatform-settings, kotlinx.serialization, Kermit, Media3 (Android),
AboutLibraries. Versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Building

Requirements: JDK 17+ (Android Studio's bundled JDK works), the Android SDK, and for iOS a Mac
with Xcode.

### Android

The app applies the Google Services / Crashlytics plugins, so it needs a Firebase config at
`androidApp/google-services.json`. That file is not in the repository — use your own Firebase
project (package `org.qstuff.qdeq`).

```bash
./gradlew :androidApp:assembleDebug
```

```bash
./gradlew :androidApp:installDebug
```

Build types: `debug`, `beta` (minified but debuggable, for testing R8) and `release`.

### iOS

Open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme on a simulator or device. A
build phase compiles the shared module into the `Shared` framework
(`./gradlew :shared:embedAndSignAppleFrameworkForXcode`); if Xcode can't find Java, it uses
Android Studio's bundled JDK.

The Xcode project is generated with [XcodeGen](https://github.com/yonaskolb/XcodeGen) from
[`iosApp/project.yml`](iosApp/project.yml). The generated project is committed, so XcodeGen is
only needed after changing `project.yml`:

```bash
cd iosApp && xcodegen
```

Music for the iOS app goes into the app's Documents folder (Files app / Finder), or is handed
over from other apps ("Open in…", share sheet, AirDrop). In the simulator, drag audio files from
Finder onto the simulator window and choose qdeq, or copy a folder into the app's Documents:

```bash
cp -R ~/Music/your-folder "$(xcrun simctl get_app_container booted org.qstuff.qdeq data)/Documents/"
```

### Tests

```bash
./gradlew :shared:testAndroidHostTest
```

```bash
./gradlew :androidApp:testDebugUnitTest
```

The shared tests currently run on the JVM only.

## Contributing

Work happens on feature branches with pull requests into `develop`. Larger features that take
several steps can use an integration branch: the steps' pull requests go into it, and one final
pull request brings the finished feature into `develop`.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
Copyright 2016 – present QStuff (Claus Chierici, coding; Ingo Stahl-Blood, design).

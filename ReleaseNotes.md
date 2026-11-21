# Release notes

## qdeq 4.4.1

- **Scrolling waveform on phones:** in the *qstuff dj* layout the scrolling waveform now sits
  above the overview, as on tablets — 4 bars with beat lines, the BPM, and you can drag it. The
  overview is slimmer to make room.
- **New layout names:** the phone layouts are now called *qstuff dj* (was Modern) and
  *transparent* (was Classic). Your chosen layout stays.
- **Simpler jog switches:** mode and sensitivity in the jog wheel's corners are now plain text
  (angle / speed / scratch, slow / mid / fast) instead of icons.
- **Smaller details:** the BPM label sits centred at the left of the scrolling waveform, long
  track titles keep a margin at the right edge, and the "calculating waveform data" hint is gone —
  the waveform fills in from the left instead.

## qdeq 4.4.0

- **New on tablets: scrolling waveform.** At the top left the music scrolls from right to left
  under a fixed playhead. For tracks with a steady beat it shows exactly 4 bars, with lines for
  every beat and every bar.
- **BPM display:** the track's tempo is detected automatically and shown in the waveform. It
  follows the pitch.
- **Drag the waveform:** move the scrolling waveform with your finger like a record. In scratch
  mode you hear the scratching; otherwise playback jumps to the new position.
- **New tablet arrangement:** track title and time now sit on the right above the buttons. The
  overview waveform is slimmer, so the scrolling one has more room.
- **Fixed:** the app crashed when a track was in the queue twice, for example after adding a
  folder. Affected queues are cleaned up automatically at startup.

## qdeq 4.3.0

- **New: classic player layout.** On phones you can switch back to the original qdeq look under
  Settings → Appearance → Player layout. The pitch fader is on the left, the buttons sit next to
  the jog wheel, and the queue, file browser and playlists are always visible below.
- **Cleaner lists:** the queue, file browser and playlists no longer have separator lines between
  entries.

## qdeq 4.2.0

- **Much faster waveforms:**
  - The waveform appears much sooner, on iOS almost instantly.
  - It now grows from left to right while it's being created, so you can see the progress.
  - Waveforms are saved, so tracks you've loaded before show theirs immediately, even after
    restarting the app.
  - The next track in the queue is prepared in the background, so its waveform is usually ready
    when you get to it.
- **Tablets:** larger text in the title and track info, on the buttons, in the tabs and in the
  lists.
- **Phones:** larger tab labels, a jog wheel with rounded corners, and evenly spaced buttons. The
  pitch slider is now as wide as the button below it.
- The app is called "qdeq" again, in lowercase.

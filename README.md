# Hihu

A light music player for Android: your local files now, YouTube Music next.

Built with Kotlin, Jetpack Compose and Media3. Material 3 colours follow your wallpaper.
Decoding is handed to the audio DSP when the phone supports it, so playback with the screen
off costs less battery.

## Status

Early. What works today:

- search YouTube Music for songs, videos, albums or artists, with suggestions as you type and
  more results as you scroll
- open an album or an artist and play or shuffle its songs
- music videos play as audio only, so they cost no more data or battery than a song
- browse the music files on your phone, and play or shuffle all of them
- mini player and full-screen player tinted by the cover, with seek, shuffle and repeat
- favourites, your own playlists, play history, and a queue you can reorder that is still
  there after the app is closed
- light or dark theme, and three streaming qualities
- notification, lockscreen and headset controls

Planned: signing in to YouTube Music (your account's playlists and likes), home
recommendations, synced lyrics, offline downloads, equalizer and volume normalisation.

## Build

```sh
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Credits

The module layout and its rules follow [Sonora](https://github.com/sonorahq/sonora)
(GPL-3.0-or-later), a native desktop music client. Hihu shares no code with it.

YouTube Music search and streams come from
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0-or-later).

Icons are [Material Icons](https://github.com/google/material-design-icons) (Apache 2.0).

## License

Copyright (C) 2026 Hihu contributors.

Hihu is free software, released under the [GNU General Public License version 3 or
later](LICENSE).

Hihu is an unofficial client. It is not affiliated with, endorsed by, or sponsored by Google
or YouTube.

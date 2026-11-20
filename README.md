# Hihu

A light music player for Android, for YouTube Music and the music files on your phone.

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
- a Home tab with rows made from your own history (keep listening, forgotten favourites,
  similar to the artist you play most) and YouTube Music's home shelves, no account needed
- an Explore tab like YouTube Music's: moods and genres, new albums and music videos, and
  what is trending in your country, from YouTube Music's charts
- download songs from YouTube Music and play them with no connection; downloads stay inside
  Hihu and are listed in the library and in settings
- synced lyrics that follow the song, from LRCLIB; tap a line to jump to it. When LRCLIB has
  none, the plain lyrics from YouTube Music
- even out volume: songs mastered louder than YouTube's usual level are turned down
- light or dark theme, and three streaming qualities
- notification, lockscreen and headset controls

- optionally, sign in to YouTube Music: your account's playlists, albums and artists in the
  library, Home and Explore as your account sees them, and hearts that are also liked on your
  account (likes made elsewhere stay out of Hihu's favourites)

Planned: equalizer.

## Privacy

Hihu has no analytics or crash reporting. Searching and playing talk to YouTube. Signing in
is optional: it happens on Google's own page, and the session stays on the phone, encrypted
with the Android Keystore, and goes only to YouTube Music. YouTube does not allow other apps to
sign in this way, so it could limit the account.
Opening the lyrics of a song sends its title, artist and length to lrclib.net, and nothing
else, and asks YouTube Music when LRCLIB has none; lyrics are only looked up while they are
on screen.

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

Synced lyrics come from [LRCLIB](https://lrclib.net).

Icons are [Material Icons](https://github.com/google/material-design-icons) (Apache 2.0).

## License

Copyright (C) 2026 Hihu contributors.

Hihu is free software, released under the [GNU General Public License version 3 or
later](LICENSE).

Hihu is an unofficial client. It is not affiliated with, endorsed by, or sponsored by Google
or YouTube.

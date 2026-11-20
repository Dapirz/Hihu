# Hihu

A light Android music player for YouTube Music and local files. Kotlin, Jetpack Compose,
Media3. GPL-3.0-or-later. The module layout and rules follow Sonora's crate layout
(github.com/sonorahq/sonora), adapted to Android.

## Module layout

```
app/            composition root: Application, MainActivity, PlaybackService (ExoPlayer + MediaSession),
                Offline (Media3 downloads and the cache the player reads through), HihuDownloadService
views/          screens/, shells/ (HihuRoot), chrome/ (mini player), shared/ (Artwork, SongList, Message)
state/          app-wide state holders and all async work: Playback, Library, Search, Collections, Pager,
                SearchHistory, PlaylistImport, Home, Explore, Moods, Radio, SongLyrics, Downloads (behind the DownloadEngine interface),
                Account (the signed-in session, encrypted with the Android Keystore in SessionStore)
music/          models and provider interfaces only (Song, Collection, Page, SongLibrary, MusicSearch,
                MusicBrowse, MusicAccount, StreamResolver), pure Kotlin
music/local/    the MediaStore provider
music/lrclib/   lyrics from lrclib.net (title, artist and length only), parsed with NewPipe's nanojson
music/youtube/  YouTube Music through NewPipeExtractor: search by kind, suggestions, album and artist
                songs, radio mixes, home/explore/mood feeds and plain lyrics through a small InnerTube client of its
                own (InnerTube.kt, YouTubeFeed.kt; anonymous, with the visitor id in memory only, except a
                second signed client for the account's library and its playlists),
                the trending chart from YouTube Music's own charts page (charts.youtube.com now wants a
                reCAPTCHA token, which Hihu does not get around), and resolving
                `hihu-yt://` URIs to audio streams, and opening shared playlist links (PlaylistLinks)
router/         Destination (tabs, collections, playlists, queue, settings, player) and the Navigator back stack, pure Kotlin
storage/        hihu.db on the platform's SQLite: schema and upgrades only; the queries live in state
ui/             design system: HihuTheme, Metrics, CoverColors, Icons and drawables
```

Dependency direction is strict. Do not create a back edge:

```
app → views → state → music
app → music:local, music:youtube, music:lrclib → music
state → storage
views → ui, router
```

- The database goes through `state.Store`, which runs every query on one background thread.
  A schema change bumps `HihuDatabase.VERSION` and adds its statements under that version in
  `MIGRATIONS`; never edit a table that has shipped. Settings are SharedPreferences in `state.Preferences`.

- `state` and `views` see only the interfaces and models in `music`, never a provider module.
  Only `app/` names a concrete provider.
- `music` and `router` never depend on Android. `ui` never knows about `music`, `state` or
  playback. Widgets that need app state live in `views/chrome/`.
- `app/PlaybackService` owns the one player. ExoPlayer is a dependency of `app` only.
- A provider song whose URI needs work before it plays (`hihu-yt://<id>`) is resolved by a
  `StreamResolver` inside the service's `ResolvingDataSource`, on the loading thread, every time
  it opens. Never resolve stream URLs ahead of time in `state`: they expire.
- The player reads through `Offline.playerSource`, a read-only cache in front of that resolving
  source. A downloaded song is stored under its song URI (`hihu-yt://<id>`), so it plays from the
  cache with no network and no resolving; anything else streams and is not kept.
- `music/youtube` is the only module that knows NewPipeExtractor. Its R8 rules live in
  `app/proguard-rules.pro`; a release build that cannot search usually means a missing keep rule.

## Flavours

`standard` (package `com.daffa.hihu`) has no account. `google` ("Hihu G", package
`com.daffa.hihu.g`) signs in to YouTube Music: the user signs in on Google's own page in a web
view (`SignInScreen`), and the session cookies go to `state.Account`. The flavour difference is
one function, `accountFor` in `app/src/<flavour>/kotlin/Flavor.kt`; views show account UI only
when `AppState.account` is not null. Never log the session cookies or the `Authorization` header.

## Rules

- **Never drive the player from a view.** Go through `state.Playback`, which talks to the
  service through a MediaController.
- **Paged lists go through `state.Pager`.** It drops items with a repeated key, which providers
  send across pages and which would crash a lazy list. Views ask for more with `ListMore`.
- **Only audio is fetched**, music videos included. Hihu never renders video.
- **Async work belongs in `state`.** Launch it in the app scope, keep the `Job` in a field and
  cancel the previous one before starting another. Views only read `StateFlow`s and call methods.
  The one exception is cover images, which `views/shared/Artwork.kt` loads and caches itself.
- **Never hardcode a size, gap or radius in a view.** Read it from `HihuTheme.metrics`. Colours
  come from `MaterialTheme.colorScheme`. A new size goes into `ui/Metrics.kt`.
- **Every user-facing string is a resource.** English in `values/strings.xml`, Indonesian in
  `values-in/strings.xml`. Counts use plurals. Log messages stay in English, prefixed by
  subsystem (`"playback: …"`).
- **Every interactive element has a label.** Icon buttons take a `contentDescription` from a
  string resource; custom clickables add one through `semantics`.
- **Icons are drawables in `ui/src/main/res/drawable`**, exposed through `ui.Icons`.
- **Keep it light.** No DI framework, no image-loading library, no analytics or crash reporting.
  A new dependency needs a reason that a few lines of code cannot cover.
- **Do not add tests unless asked.**

## Building

```sh
./gradlew assembleStandardDebug
./gradlew assembleStandardRelease   # R8-minified, signed with Hihu's release key
./gradlew assembleGoogleRelease     # Hihu G, with the YouTube Music account
adb install -r app/build/outputs/apk/standard/release/app-standard-release.apk
adb install -r app/build/outputs/apk/google/release/app-google-release.apk
```

Release builds are signed with Hihu's own key, `hihu.p12`, kept outside the repository; its path
and password are in `keystore.properties` (ignored, with `storeFile`, `storePassword`, `keyAlias`,
`keyPassword`). Without that file a release build comes out unsigned. Never print or commit either.
Debug builds use the machine's debug key under the package suffix `.debug`.

`local.properties` (ignored) holds `sdk.dir`. compileSdk is 37 because Compose 1.12 needs it;
minSdk is 29.

## Code style

- Every type and every function whose behaviour is not obvious from its name carries a KDoc
  comment saying what it is for and what a caller has to know. One or two plain sentences.
- Two-arm boolean choices use `when (flag) { true -> …; false -> … }`. Return early.

## Versions

`versionName` in `app/build.gradle.kts` follows MAJOR.MINOR.PATCH and goes up with every change
that ships, by how big the change is: a fix or small tweak bumps PATCH (0.1.0 → 0.1.1), a new
feature bumps MINOR and resets PATCH (0.1.1 → 0.2.0), a change that breaks what users have (data,
sign-in, a removed feature) bumps MAJOR. `versionCode` goes up by one each time. Both flavours share
the version. A release on GitHub is tagged `v<versionName>`.

## Commits

Conventional Commits: `type(scope): description`, imperative, lowercase, no trailing period.
Scopes: `app`, `views`, `state`, `music`, `local`, `youtube`, `router`, `ui`, `build`.

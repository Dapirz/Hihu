# Hihu

A light Android music player for YouTube Music and local files. Kotlin, Jetpack Compose,
Media3. GPL-3.0-or-later. The module layout and rules follow Sonora's crate layout
(github.com/sonorahq/sonora), adapted to Android.

## Module layout

```
app/            composition root: Application, MainActivity, PlaybackService (ExoPlayer + MediaSession)
views/          screens/, shells/ (HihuRoot), chrome/ (mini player), shared/ (Artwork, SongList, Message)
state/          app-wide state holders and all async work: Playback, Library, Search, Collections, Pager
music/          models and provider interfaces only (Song, Collection, Page, SongLibrary, MusicSearch,
                MusicBrowse, StreamResolver), pure Kotlin
music/local/    the MediaStore provider
music/lrclib/   lyrics from lrclib.net (title, artist and length only), parsed with NewPipe's nanojson
music/youtube/  YouTube Music through NewPipeExtractor: search by kind, suggestions, album and artist
                songs, and resolving `hihu-yt://` URIs to audio streams
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
  A schema change bumps `HihuDatabase.VERSION` and adds its migration to `onUpgrade`; never
  edit a table that has shipped. Settings are SharedPreferences in `state.Preferences`.

- `state` and `views` see only the interfaces and models in `music`, never a provider module.
  Only `app/` names a concrete provider.
- `music` and `router` never depend on Android. `ui` never knows about `music`, `state` or
  playback. Widgets that need app state live in `views/chrome/`.
- `app/PlaybackService` owns the one player. ExoPlayer is a dependency of `app` only.
- A provider song whose URI needs work before it plays (`hihu-yt://<id>`) is resolved by a
  `StreamResolver` inside the service's `ResolvingDataSource`, on the loading thread, every time
  it opens. Never resolve stream URLs ahead of time in `state`: they expire.
- `music/youtube` is the only module that knows NewPipeExtractor. Its R8 rules live in
  `app/proguard-rules.pro`; a release build that cannot search usually means a missing keep rule.

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
./gradlew assembleDebug
./gradlew assembleRelease      # R8-minified, signed with the debug key for personal installs
adb install -r app/build/outputs/apk/release/app-release.apk
```

`local.properties` (ignored) holds `sdk.dir`. compileSdk is 37 because Compose 1.12 needs it;
minSdk is 29.

## Code style

- Every type and every function whose behaviour is not obvious from its name carries a KDoc
  comment saying what it is for and what a caller has to know. One or two plain sentences.
- Two-arm boolean choices use `when (flag) { true -> …; false -> … }`. Return early.

## Commits

Conventional Commits: `type(scope): description`, imperative, lowercase, no trailing period.
Scopes: `app`, `views`, `state`, `music`, `local`, `youtube`, `router`, `ui`, `build`.

# UI references

Screenshots from Samsung Music and ArchiveTune on the owner's Galaxy A57, November 2026.
They are a guide to layout and feel, not something to copy pixel for pixel. No assets from
either app go into Hihu.

## What Hihu takes

**From Samsung Music** (`samsung-*.jpg`): the local library and the player.

- Brand title at the top left in the accent colour, search on the right.
- The track list sits on a rounded sheet, with a header row: the sort order on the left, and
  round Shuffle and Play all buttons on the right.
- The playing row is shown in the accent colour with a small equaliser mark.
- Floating pill mini player, tinted by the colour of the cover, with previous, play/pause and
  next.
- Full player background is a gradient from near-black at the top into the cover's colour at
  the bottom. Large rounded cover, centred title and artist, thin seek bar, and a transport
  row of shuffle, previous, a large play/pause, next and repeat.

**From ArchiveTune** (`archivetune-*.jpg`): the YouTube Music screens that come with step 2.

- Bottom navigation: Home, Search, Library.
- Home as horizontal shelves (quick picks, keep listening, recommendations).
- Search with a rounded field and a two-column grid of moods and genres in solid colours.
- Library tiles: liked songs, downloaded, local files.

**From YouTube Music** (`ytmusic-*`): what the YouTube screens are built from.

- Home: mood chips across the top, then shelves. Song shelves are a list of four rows with a
  "Play all" button; album and playlist shelves are horizontal rows of square covers.
- Explore: three tiles (New releases, Charts, Moods & genres), then shelves of new albums,
  numbered top songs with a rank change mark, and mood chips with a coloured edge.
- Library: a title with a sort menu, chips (Downloads, Playlists, Songs, Albums, Artists), then
  one list mixing playlists (square cover) and artists (round photo) with a subtitle line.

## What Hihu leaves out

- Background blur and glass effects. The tint is one colour per song, computed once from a
  tiny copy of the cover.
- ArchiveTune's support-ad card, AI mixes, Listen Together and the extra header buttons.
- Samsung's alphabet scroller, until a library is long enough to need it.

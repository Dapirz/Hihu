package app.hihu.music

/**
 * A source of songs the user already owns, such as the files on the device. State and views see
 * only this interface; only the app module names a concrete implementation.
 */
interface SongLibrary {
    /** Reads every song in the library. Callers run it off the main thread. */
    suspend fun songs(): List<Song>
}

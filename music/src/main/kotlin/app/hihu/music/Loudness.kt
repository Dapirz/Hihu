package app.hihu.music

/**
 * How loud a provider's songs are, for evening out the volume from one song to the next.
 * Callers run it off the main thread; an implementation may ask the network.
 */
interface SongLoudness {
    /**
     * How many decibels [uri] is louder than the provider's target loudness (negative when it is
     * quieter), or null when the provider does not know or cannot be reached.
     */
    fun loudnessDb(uri: String): Double?
}

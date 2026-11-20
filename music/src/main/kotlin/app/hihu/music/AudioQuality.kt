package app.hihu.music

/**
 * Which stream a provider picks when it has several. [Normal] is AAC, which the phone can hand
 * to its audio chip; [High] is the highest bitrate whatever the codec; [DataSaver] the lowest.
 */
enum class AudioQuality { High, Normal, DataSaver }

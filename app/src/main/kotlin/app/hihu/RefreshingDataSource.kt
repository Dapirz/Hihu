package app.hihu

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * Wraps the resolving source so a stream URL the server refuses (403 or 410) is forgotten. The
 * player's own retry then opens the song again from where it was, and the resolver fetches a
 * fresh URL instead of handing back the dead one from its cache.
 */
@OptIn(UnstableApi::class)
class RefreshingDataSource private constructor(
    private val inner: DataSource,
    private val forget: (Uri) -> Unit,
) : DataSource by inner {

    class Factory(private val inner: DataSource.Factory, private val forget: (Uri) -> Unit) : DataSource.Factory {
        override fun createDataSource(): DataSource = RefreshingDataSource(inner.createDataSource(), forget)
    }

    private var uri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) = inner.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        return guard { inner.open(dataSpec) }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = guard { inner.read(buffer, offset, length) }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: HttpDataSource.InvalidResponseCodeException) {
        if (e.responseCode in REFUSED) uri?.let(forget)
        throw e
    }

    private companion object {
        val REFUSED = setOf(403, 410)
    }
}

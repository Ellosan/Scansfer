package com.scansfer.app.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.scansfer.app.core.ByteSource
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** What the send screen shows about the clip before the user commits to it. */
data class VideoInfo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val durationMs: Int,
    val thumbnail: Bitmap?,
)

object VideoSource {

    /** Videos above this are technically fine but take painfully long over QR. */
    const val COMFORTABLE_LIMIT_BYTES = 6L * 1024 * 1024

    /** Hard ceiling: the protocol carries a 32-bit length. */
    const val MAX_BYTES = 512L * 1024 * 1024

    fun inspect(context: Context, uri: Uri): VideoInfo? {
        val resolver = context.contentResolver
        var name = "video.mp4"
        var size = -1L

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { name = cursor.getString(it) }
                cursor.getColumnIndex(OpenableColumns.SIZE)
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { size = cursor.getLong(it) }
            }
        }
        if (size < 0) {
            size = runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { it.statSize }
            }.getOrNull() ?: -1L
        }
        if (size <= 0) return null

        val mime = resolver.getType(uri) ?: guessMime(name)

        var duration = 0
        var thumb: Bitmap? = null
        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                duration = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toIntOrNull() ?: 0
                thumb = retriever.getScaledFrameAtTime(
                    (duration * 1000L) / 3,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    480,
                    480,
                )
            }
        }

        return VideoInfo(uri, name, size, mime, duration, thumb)
    }

    /**
     * Maps the picked file so the fountain encoder can hop around it without
     * pulling the whole video onto the heap. Falls back to a cache copy when the
     * provider hands back a pipe rather than a real file.
     */
    fun open(context: Context, uri: Uri): ByteSource {
        runCatching { return MappedSource.fromUri(context.contentResolver, uri) }
        val cached = File(context.cacheDir, "outbound-${System.currentTimeMillis()}.bin")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "cannot open $uri" }
            cached.outputStream().use { input.copyTo(it, DEFAULT_BUFFER_SIZE) }
        }
        return MappedSource.fromFile(cached, deleteOnClose = true)
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "3gp" -> "video/3gpp"
        "mov" -> "video/quicktime"
        else -> "video/mp4"
    }
}

private class MappedSource(
    private val buffer: MappedByteBuffer,
    override val size: Int,
    private val channel: FileChannel,
    private val onClose: () -> Unit,
) : ByteSource {

    override fun copyInto(offset: Int, length: Int, dst: ByteArray) {
        // Duplicate keeps the position bookkeeping thread-local and cheap.
        buffer.duplicate().apply { position(offset) }.get(dst, 0, length)
    }

    override fun close() {
        runCatching { channel.close() }
        onClose()
    }

    companion object {
        fun fromUri(resolver: ContentResolver, uri: Uri): MappedSource {
            val descriptor = requireNotNull(resolver.openFileDescriptor(uri, "r"))
            val stream = FileInputStream(descriptor.fileDescriptor)
            val channel = stream.channel
            val length = channel.size()
            require(length in 1..VideoSource.MAX_BYTES) { "unsupported size $length" }
            val map = channel.map(FileChannel.MapMode.READ_ONLY, 0, length)
            return MappedSource(map, length.toInt(), channel) { runCatching { descriptor.close() } }
        }

        fun fromFile(file: File, deleteOnClose: Boolean): MappedSource {
            val raf = RandomAccessFile(file, "r")
            val channel = raf.channel
            val length = channel.size()
            require(length in 1..VideoSource.MAX_BYTES) { "unsupported size $length" }
            val map = channel.map(FileChannel.MapMode.READ_ONLY, 0, length)
            return MappedSource(map, length.toInt(), channel) {
                runCatching { raf.close() }
                if (deleteOnClose) file.delete()
            }
        }
    }
}

package com.scansfer.app.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Size
import com.scansfer.app.core.ByteSource
import com.scansfer.app.core.MediaKind
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** What the send screen shows about the picked item before the user commits. */
data class MediaInfo(
    val uri: Uri,
    val kind: MediaKind,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    /** Playback length for a video; zero for a photo. */
    val durationMs: Int,
    /** Pixel dimensions for a photo; null when unknown or not applicable. */
    val pixelSize: Size?,
    val thumbnail: Bitmap?,
)

object MediaSource {

    /** Above this a transfer takes long enough to be worth warning about. */
    const val COMFORTABLE_LIMIT_BYTES = 6L * 1024 * 1024

    /** Hard ceiling: the protocol carries a 32-bit length. */
    const val MAX_BYTES = 512L * 1024 * 1024

    private const val THUMBNAIL_EDGE = 720

    fun inspect(context: Context, uri: Uri): MediaInfo? {
        val resolver = context.contentResolver
        var name = ""
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

        val resolvedMime = resolver.getType(uri)
        val kind = MediaKind.of(resolvedMime, name)
        if (name.isBlank()) name = if (kind == MediaKind.PHOTO) "photo.jpg" else "video.mp4"
        val mimeType = resolvedMime?.takeIf { it.isNotBlank() } ?: MediaKind.mimeForExtension(name)

        return when (kind) {
            MediaKind.PHOTO -> inspectPhoto(context, uri, name, size, mimeType)
            MediaKind.VIDEO -> inspectVideo(context, uri, name, size, mimeType)
        }
    }

    private fun inspectPhoto(
        context: Context,
        uri: Uri,
        name: String,
        size: Long,
        mimeType: String,
    ): MediaInfo {
        var pixels: Size? = null
        val thumbnail = runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            // ImageDecoder applies EXIF orientation for us, so the preview is
            // never sideways the way a raw BitmapFactory decode would be.
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                pixels = Size(info.size.width, info.size.height)
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > THUMBNAIL_EDGE) {
                    val scale = THUMBNAIL_EDGE.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        }.getOrNull()

        return MediaInfo(
            uri = uri,
            kind = MediaKind.PHOTO,
            displayName = name,
            sizeBytes = size,
            mimeType = mimeType,
            durationMs = 0,
            pixelSize = pixels,
            thumbnail = thumbnail,
        )
    }

    private fun inspectVideo(
        context: Context,
        uri: Uri,
        name: String,
        size: Long,
        mimeType: String,
    ): MediaInfo {
        var duration = 0
        var pixels: Size? = null
        var thumbnail: Bitmap? = null

        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                duration = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toIntOrNull() ?: 0
                val width = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
                val height = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
                if (width != null && height != null) pixels = Size(width, height)
                thumbnail = retriever.getScaledFrameAtTime(
                    (duration * 1000L) / 3,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    THUMBNAIL_EDGE,
                    THUMBNAIL_EDGE,
                )
            }
        }

        return MediaInfo(
            uri = uri,
            kind = MediaKind.VIDEO,
            displayName = name,
            sizeBytes = size,
            mimeType = mimeType,
            durationMs = duration,
            pixelSize = pixels,
            thumbnail = thumbnail,
        )
    }

    /**
     * Maps the picked file so the fountain encoder can hop around it without
     * pulling the whole thing onto the heap. Falls back to a cache copy when the
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
            require(length in 1..MediaSource.MAX_BYTES) { "unsupported size $length" }
            val map = channel.map(FileChannel.MapMode.READ_ONLY, 0, length)
            return MappedSource(map, length.toInt(), channel) { runCatching { descriptor.close() } }
        }

        fun fromFile(file: File, deleteOnClose: Boolean): MappedSource {
            val raf = RandomAccessFile(file, "r")
            val channel = raf.channel
            val length = channel.size()
            require(length in 1..MediaSource.MAX_BYTES) { "unsupported size $length" }
            val map = channel.map(FileChannel.MapMode.READ_ONLY, 0, length)
            return MappedSource(map, length.toInt(), channel) {
                runCatching { raf.close() }
                if (deleteOnClose) file.delete()
            }
        }
    }
}

package com.scansfer.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.scansfer.app.core.FountainDecoder
import com.scansfer.app.core.MediaKind
import java.io.OutputStream

object MediaStoreSaver {

    private const val ALBUM = "Scansfer"

    /** Where a received item lands, for showing the user after a transfer. */
    fun albumPath(kind: MediaKind): String =
        if (kind == MediaKind.PHOTO) "Pictures › $ALBUM" else "Movies › $ALBUM"

    /**
     * Streams the decoded blocks straight into the gallery. Writing block by
     * block avoids holding a second full copy of the file in memory.
     */
    fun save(
        context: Context,
        decoder: FountainDecoder,
        fileSize: Int,
        displayName: String,
        mimeType: String,
        kind: MediaKind,
    ): Uri {
        val resolver = context.contentResolver
        val photo = kind == MediaKind.PHOTO

        val collection = if (photo) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val directory = if (photo) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName(displayName, kind))
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType.ifBlank { kind.defaultMimeType })
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/$ALBUM")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = requireNotNull(resolver.insert(collection, values)) {
            "MediaStore rejected the insert"
        }

        try {
            resolver.openOutputStream(uri).use { output ->
                requireNotNull(output) { "cannot open $uri for writing" }
                writeInto(output, decoder, fileSize)
            }
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }

        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
        return uri
    }

    private fun writeInto(output: OutputStream, decoder: FountainDecoder, fileSize: Int) {
        var written = 0
        var index = 0
        while (written < fileSize) {
            val block = requireNotNull(decoder.block(index)) { "block $index missing" }
            val take = minOf(block.size, fileSize - written)
            output.write(block, 0, take)
            written += take
            index++
        }
        output.flush()
    }

    private fun uniqueName(displayName: String, kind: MediaKind): String {
        val fallback = if (kind == MediaKind.PHOTO) "scansfer-photo.jpg" else "scansfer-video.mp4"
        val cleaned = displayName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { fallback }
        val stamp = System.currentTimeMillis() % 100_000
        val stem = cleaned.substringBeforeLast('.', cleaned)
        val extension = cleaned.substringAfterLast('.', "")
            .ifBlank { fallback.substringAfterLast('.') }
        return "$stem-$stamp.$extension"
    }
}

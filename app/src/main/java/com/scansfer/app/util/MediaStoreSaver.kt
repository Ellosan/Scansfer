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
    fun albumPath(kind: MediaKind): String = when (kind) {
        MediaKind.PHOTO -> "Pictures › $ALBUM"
        MediaKind.VIDEO -> "Movies › $ALBUM"
        MediaKind.FILE -> "Downloads › $ALBUM"
    }

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

        // Images and Video are indexed collections; anything else belongs in
        // Downloads, which is the one collection that accepts arbitrary types.
        val collection = when (kind) {
            MediaKind.PHOTO -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            MediaKind.FILE -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val directory = when (kind) {
            MediaKind.PHOTO -> Environment.DIRECTORY_PICTURES
            MediaKind.VIDEO -> Environment.DIRECTORY_MOVIES
            MediaKind.FILE -> Environment.DIRECTORY_DOWNLOADS
        }

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
        val fallback = when (kind) {
            MediaKind.PHOTO -> "scansfer-photo.jpg"
            MediaKind.VIDEO -> "scansfer-video.mp4"
            MediaKind.FILE -> "scansfer-file.bin"
        }
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

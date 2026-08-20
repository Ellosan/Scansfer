package com.scansfer.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.scansfer.app.core.FountainDecoder
import java.io.OutputStream

object MediaStoreSaver {

    private const val ALBUM = "Scansfer"

    /**
     * Streams the decoded blocks straight into the gallery. Writing block by
     * block avoids holding a second full copy of the video in memory.
     */
    fun saveVideo(
        context: Context,
        decoder: FountainDecoder,
        fileSize: Int,
        displayName: String,
        mimeType: String,
    ): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, uniqueName(displayName))
            put(MediaStore.Video.Media.MIME_TYPE, mimeType.ifBlank { "video/mp4" })
            put(MediaStore.Video.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_MOVIES}/$ALBUM")
            put(MediaStore.Video.Media.IS_PENDING, 1)
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
            ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
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

    private fun uniqueName(displayName: String): String {
        val cleaned = displayName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "scansfer-video.mp4" }
        val stamp = System.currentTimeMillis() % 100_000
        val stem = cleaned.substringBeforeLast('.', cleaned)
        val extension = cleaned.substringAfterLast('.', "mp4")
        return "$stem-$stamp.$extension"
    }
}

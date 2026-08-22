package com.scansfer.app

import com.scansfer.app.core.Manifest
import com.scansfer.app.core.MediaKind
import com.scansfer.app.core.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The receiver routes a finished transfer to Pictures or Movies purely from the
 * manifest's MIME type, so this mapping has to be right and has to survive the
 * encode/decode round trip.
 */
class MediaKindTest {

    @Test
    fun `image and video mime types map to the right kind`() {
        val photos = listOf(
            "image/jpeg", "image/png", "image/webp", "image/heic",
            "image/gif", "image/avif", "IMAGE/JPEG", "  image/jpeg  ",
        )
        for (mime in photos) {
            assertEquals("$mime should be a photo", MediaKind.PHOTO, MediaKind.of(mime))
        }

        val videos = listOf(
            "video/mp4", "video/x-matroska", "video/webm", "video/quicktime", "VIDEO/MP4",
        )
        for (mime in videos) {
            assertEquals("$mime should be a video", MediaKind.VIDEO, MediaKind.of(mime))
        }
    }

    @Test
    fun `a missing mime type falls back to the file extension`() {
        assertEquals(MediaKind.PHOTO, MediaKind.of(null, "holiday.JPG"))
        assertEquals(MediaKind.PHOTO, MediaKind.of("", "shot.heic"))
        assertEquals(MediaKind.PHOTO, MediaKind.of("application/octet-stream", "img.png"))
        assertEquals(MediaKind.VIDEO, MediaKind.of(null, "clip.mp4"))
        assertEquals(MediaKind.VIDEO, MediaKind.of("", "movie.mkv"))
        // Nothing to go on at all: assume video, the more conservative container.
        assertEquals(MediaKind.VIDEO, MediaKind.of(null, null))
    }

    @Test
    fun `extension to mime mapping stays consistent with kind detection`() {
        val names = listOf(
            "a.jpg", "a.jpeg", "a.png", "a.webp", "a.heic", "a.heif", "a.gif",
            "a.bmp", "a.avif", "a.mp4", "a.m4v", "a.mkv", "a.webm", "a.3gp", "a.mov",
        )
        for (name in names) {
            val mime = MediaKind.mimeForExtension(name)
            assertEquals(
                "$name -> $mime disagrees with extension detection",
                MediaKind.of(null, name),
                MediaKind.of(mime),
            )
        }
    }

    @Test
    fun `manifest kind survives the wire`() {
        val cases = listOf(
            Triple("beach.jpg", "image/jpeg", MediaKind.PHOTO),
            Triple("dog.png", "image/png", MediaKind.PHOTO),
            Triple("clip.mp4", "video/mp4", MediaKind.VIDEO),
            // A provider that hands back nothing useful still lands correctly.
            Triple("sunset.heic", "", MediaKind.PHOTO),
        )

        for ((name, mime, expected) in cases) {
            val manifest = Manifest(
                sessionId = 3, fileSize = 2_000_000, blockSize = 1000, blockCount = 2000,
                fileCrc = 12, fileName = name, mimeType = mime, durationMs = 0,
            )
            assertEquals("$name before encoding", expected, manifest.kind)

            val frame = assertNotNull(Protocol.parse(manifest.encode(padTo = 1008)))
            val decoded = Manifest.decode(Protocol.parse(manifest.encode(padTo = 1008))!!.body)!!
            assertEquals("$name after decoding", expected, decoded.kind)
            assertEquals(manifest, decoded)
        }
    }

    @Test
    fun `wildcard and default mime types are sane`() {
        assertEquals("image/*", MediaKind.PHOTO.wildcardMimeType)
        assertEquals("video/*", MediaKind.VIDEO.wildcardMimeType)
        assertEquals(MediaKind.PHOTO, MediaKind.of(MediaKind.PHOTO.defaultMimeType))
        assertEquals(MediaKind.VIDEO, MediaKind.of(MediaKind.VIDEO.defaultMimeType))
    }
}

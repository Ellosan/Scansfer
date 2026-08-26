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
    fun `anything that is not image or video is a plain file`() {
        val files = listOf(
            "application/pdf", "text/plain", "application/zip", "application/json",
            "audio/mpeg", "application/octet-stream", "application/epub+zip",
        )
        for (mime in files) {
            assertEquals("$mime should be a file", MediaKind.FILE, MediaKind.of(mime, "thing.bin"))
        }
        // Audio is a file, not a video: it must not land in the Movies folder.
        assertEquals(MediaKind.FILE, MediaKind.of("audio/mpeg", "song.mp3"))
        assertEquals(MediaKind.FILE, MediaKind.of(null, "notes.txt"))
        assertEquals(MediaKind.FILE, MediaKind.of("", "archive.zip"))
        // Nothing to go on at all is a file rather than a guess at media.
        assertEquals(MediaKind.FILE, MediaKind.of(null, null))
        assertEquals(MediaKind.FILE, MediaKind.of(null, "no-extension"))
    }

    @Test
    fun `a missing mime type falls back to the file extension`() {
        assertEquals(MediaKind.PHOTO, MediaKind.of(null, "holiday.JPG"))
        assertEquals(MediaKind.PHOTO, MediaKind.of("", "shot.heic"))
        assertEquals(MediaKind.PHOTO, MediaKind.of("application/octet-stream", "img.png"))
        assertEquals(MediaKind.VIDEO, MediaKind.of(null, "clip.mp4"))
        assertEquals(MediaKind.VIDEO, MediaKind.of("", "movie.mkv"))
    }

    @Test
    fun `extension to mime mapping stays consistent with kind detection`() {
        val names = listOf(
            "a.jpg", "a.jpeg", "a.png", "a.webp", "a.heic", "a.heif", "a.gif",
            "a.bmp", "a.avif", "a.mp4", "a.m4v", "a.mkv", "a.webm", "a.3gp", "a.mov",
            "a.avi", "a.pdf", "a.txt", "a.csv", "a.json", "a.zip", "a.mp3", "a.epub",
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
            Triple("report.pdf", "application/pdf", MediaKind.FILE),
            Triple("backup.zip", "", MediaKind.FILE),
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
        assertEquals("*/*", MediaKind.FILE.wildcardMimeType)
        for (kind in MediaKind.entries) {
            assertEquals(
                "${kind.name} default mime should map back to itself",
                kind,
                MediaKind.of(kind.defaultMimeType),
            )
        }
    }
}

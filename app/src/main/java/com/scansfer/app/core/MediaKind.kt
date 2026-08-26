package com.scansfer.app.core

/**
 * What kind of thing is being sent.
 *
 * Deliberately *not* a field on the wire: the manifest already carries a MIME
 * type, which is the authoritative signal, so deriving the kind from it keeps
 * the protocol unchanged. That is what let photo support land in 2.0, and
 * arbitrary files in 2.2, without touching the frame format.
 */
enum class MediaKind {
    PHOTO,
    VIDEO,
    FILE,
    ;

    val defaultMimeType: String
        get() = when (this) {
            PHOTO -> "image/jpeg"
            VIDEO -> "video/mp4"
            FILE -> OCTET_STREAM
        }

    /** For ACTION_VIEW and ACTION_SEND when the exact type is unknown. */
    val wildcardMimeType: String
        get() = when (this) {
            PHOTO -> "image/*"
            VIDEO -> "video/*"
            FILE -> "*/*"
        }

    companion object {
        const val OCTET_STREAM = "application/octet-stream"

        private val PHOTO_EXTENSIONS =
            setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "avif")
        private val VIDEO_EXTENSIONS =
            setOf("mp4", "m4v", "mkv", "webm", "3gp", "mov", "avi")

        fun of(mimeType: String?, fileName: String? = null): MediaKind {
            val mime = mimeType?.trim()?.lowercase().orEmpty()
            if (mime.startsWith("image/")) return PHOTO
            if (mime.startsWith("video/")) return VIDEO
            // Providers hand back a blank or generic type often enough that the
            // extension is worth a second look before giving up on media.
            return when (extensionOf(fileName)) {
                in PHOTO_EXTENSIONS -> PHOTO
                in VIDEO_EXTENSIONS -> VIDEO
                else -> FILE
            }
        }

        fun mimeForExtension(fileName: String): String =
            when (val extension = extensionOf(fileName)) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "heic", "heif" -> "image/$extension"
                "gif" -> "image/gif"
                "bmp" -> "image/bmp"
                "avif" -> "image/avif"
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "3gp" -> "video/3gpp"
                "mov" -> "video/quicktime"
                "avi" -> "video/x-msvideo"
                "pdf" -> "application/pdf"
                "txt", "md", "log" -> "text/plain"
                "csv" -> "text/csv"
                "json" -> "application/json"
                "xml" -> "application/xml"
                "zip" -> "application/zip"
                "apk" -> "application/vnd.android.package-archive"
                "mp3" -> "audio/mpeg"
                "ogg", "opus" -> "audio/ogg"
                "epub" -> "application/epub+zip"
                else -> OCTET_STREAM
            }

        private fun extensionOf(fileName: String?): String =
            fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
    }
}

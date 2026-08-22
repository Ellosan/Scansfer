package com.scansfer.app.core

/**
 * What kind of thing is being sent.
 *
 * Deliberately *not* a field on the wire: the manifest already carries a MIME
 * type, which is the authoritative signal, so deriving the kind from it keeps
 * the protocol unchanged and lets 1.x and 2.x senders and receivers interoperate.
 */
enum class MediaKind {
    PHOTO,
    VIDEO,
    ;

    val defaultMimeType: String get() = if (this == PHOTO) "image/jpeg" else "video/mp4"

    /** For ACTION_VIEW and ACTION_SEND when the exact type is unknown. */
    val wildcardMimeType: String get() = if (this == PHOTO) "image/*" else "video/*"

    companion object {
        fun of(mimeType: String?, fileName: String? = null): MediaKind {
            val mime = mimeType?.trim()?.lowercase().orEmpty()
            if (mime.startsWith("image/")) return PHOTO
            if (mime.startsWith("video/")) return VIDEO
            return fromExtension(fileName)
        }

        /** Fallback for providers that hand back a blank or bogus MIME type. */
        private fun fromExtension(fileName: String?): MediaKind =
            when (fileName?.substringAfterLast('.', "")?.lowercase()) {
                "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "avif" -> PHOTO
                else -> VIDEO
            }

        fun mimeForExtension(fileName: String): String =
            when (val extension = fileName.substringAfterLast('.', "").lowercase()) {
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
                else -> "video/mp4"
            }
    }
}

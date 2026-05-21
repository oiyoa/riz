package com.riz.app.crypto

object FileExtensions {
    val IMAGE = listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg")
    val VIDEO = listOf("mp4", "mov", "avi", "mkv", "webm", "3gp", "m4v")
    val AUDIO = listOf("mp3", "wav", "ogg", "flac", "m4a", "aac", "opus")
    val ARCHIVE = listOf("zip", "rar", "tar", "gz", "7z", "bz2", "xz")
    val PLAIN_TEXT = listOf("txt", "md", "rtf", "csv", "log")
    val DOCUMENT = listOf("doc", "docx", "odt")
    val PDF = listOf("pdf")
    val APK = listOf("apk")

    val ALL: List<String> = IMAGE + VIDEO + AUDIO + ARCHIVE + PLAIN_TEXT + DOCUMENT + PDF + APK
}

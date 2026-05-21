package com.riz.app.crypto

import com.riz.app.util.AppLog
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater

object Compression {
    private const val BUFFER_SIZE = 8192

    // Cap so an incompressible-payload size estimate doesn't itself OOM before any work happens.
    private const val MAX_INITIAL_BUFFER = 4 * 1024 * 1024

    fun compressData(bytes: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream(initialBufferFor(bytes.size))
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            DeflaterOutputStream(outputStream, deflater, BUFFER_SIZE).use { dos ->
                dos.write(bytes)
            }
        } finally {
            deflater.end()
        }
        return outputStream.toByteArray()
    }

    // Drops each entry's bytes as soon as it's deflated so they don't co-exist with the cipher buf.
    fun compressMultiFile(files: MutableList<FileEntry>): ByteArray {
        val encodedNames = files.map { it.name.toByteArray(Charsets.UTF_8) }
        encodedNames.forEachIndexed { i, name ->
            require(name.size <= MAX_FILENAME_LEN) { "Filename too long: ${files[i].name}" }
        }
        val rawSize =
            HEADER_PREAMBLE_SIZE +
                files.indices.sumOf { i ->
                    PER_FILE_HEADER_SIZE + encodedNames[i].size + files[i].data.size
                }

        val outputStream = ByteArrayOutputStream(initialBufferFor(rawSize))
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            DataOutputStream(DeflaterOutputStream(outputStream, deflater, BUFFER_SIZE)).use { out ->
                out.writeByte(BinaryFormat.MULTI_FILE_MAGIC.toInt())
                out.writeShort(files.size)
                for (i in files.indices) {
                    val file = files[i]
                    val nameBytes = encodedNames[i]
                    out.writeByte(nameBytes.size)
                    out.write(nameBytes)
                    out.writeInt(file.data.size)
                    out.write(file.data)
                    files[i] = FileEntry(file.name, EMPTY_BYTES)
                }
            }
        } finally {
            deflater.end()
        }
        return outputStream.toByteArray()
    }

    private val EMPTY_BYTES = ByteArray(0)

    fun decompressData(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): ByteArray {
        val inflater = Inflater(true)
        return try {
            inflater.setInput(bytes, offset, length)
            inflateInternal(inflater, length) ?: bytes.copyOfRange(offset, offset + length)
        } catch (e: Exception) {
            // Return original bytes on error as fallback
            AppLog.w("Compression", "Decompression failed", e)
            bytes.copyOfRange(offset, offset + length)
        } finally {
            inflater.end()
        }
    }

    private fun inflateInternal(
        inflater: Inflater,
        initialSize: Int,
    ): ByteArray? {
        val outputStream = ByteArrayOutputStream(initialSize)
        val buffer = ByteArray(BUFFER_SIZE)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count > 0) {
                outputStream.write(buffer, 0, count)
            } else if (inflater.needsInput() || inflater.needsDictionary()) {
                break
            }
        }
        return if (inflater.finished()) outputStream.toByteArray() else null
    }

    private fun initialBufferFor(rawSize: Int): Int = minOf(rawSize, MAX_INITIAL_BUFFER).coerceAtLeast(BUFFER_SIZE)

    private const val MAX_FILENAME_LEN = 254
    private const val HEADER_PREAMBLE_SIZE = 1 + 2 // magic + count(short)
    private const val PER_FILE_HEADER_SIZE = 1 + 4 // nameLen(byte) + dataLen(int)
}

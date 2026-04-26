package com.riz.app.crypto

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

object Compression {
    private const val BUFFER_SIZE = 8192

    fun compressData(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(bytes)
        deflater.finish()

        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        deflater.end()

        return outputStream.toByteArray()
    }

    fun decompressData(bytes: ByteArray): ByteArray = decompressData(bytes, 0, bytes.size)

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
            println("Decompression failed: ${e.message}")
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
}

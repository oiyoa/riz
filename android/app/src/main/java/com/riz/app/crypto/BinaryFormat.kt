package com.riz.app.crypto

import java.nio.ByteBuffer

data class FileEntry(
    val name: String,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FileEntry
        return name == other.name && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

object BinaryFormat {
    const val MULTI_FILE_MAGIC: Byte = 0xFF.toByte()
    private const val MAX_FILENAME_LEN = 254
    private const val BYTE_MASK = 0xFF
    private const val SHORT_MASK = 0xFFFF
    private const val INT_SIZE = 4
    private const val SHORT_SIZE = 2

    fun packMultipleFiles(files: List<FileEntry>): ByteArray {
        val encodedEntries =
            files.map { file ->
                val nameBytes = file.name.toByteArray(Charsets.UTF_8)
                require(nameBytes.size <= MAX_FILENAME_LEN) { "Filename too long: ${file.name}" }
                nameBytes to file.data
            }

        // 1 (magic) + 2 (count) = 3
        val totalSize = 3 + encodedEntries.sumOf { (name, data) -> 1 + name.size + INT_SIZE + data.size }

        return ByteBuffer
            .allocate(totalSize)
            .apply {
                put(MULTI_FILE_MAGIC)
                putShort(files.size.toShort())
                for ((name, data) in encodedEntries) {
                    put(name.size.toByte())
                    put(name)
                    putInt(data.size)
                    put(data)
                }
            }.array()
    }

    fun unpackMultipleFiles(dec: ByteArray): List<FileEntry> {
        val buffer = ByteBuffer.wrap(dec)
        require(buffer.hasRemaining()) { "Empty buffer during multi-file unpack" }
        val magic = buffer.get()
        require(magic == MULTI_FILE_MAGIC) {
            "Not a multi-file format. Expected ${MULTI_FILE_MAGIC.toInt() and BYTE_MASK}, " +
                "got ${magic.toInt() and BYTE_MASK}"
        }

        require(buffer.remaining() >= SHORT_SIZE) { "Buffer too short for file count" }
        val count = buffer.short.toInt() and SHORT_MASK
        return List(count) {
            require(buffer.hasRemaining()) { "Buffer exhausted while reading name length" }
            val nameLen = buffer.get().toInt() and BYTE_MASK
            require(buffer.remaining() >= nameLen) {
                "Buffer too short for filename ($nameLen bytes expected, ${buffer.remaining()} available)"
            }
            val nameBytes = ByteArray(nameLen).also { buffer.get(it) }
            val name = String(nameBytes, Charsets.UTF_8)

            require(buffer.remaining() >= INT_SIZE) { "Buffer too short for data length" }
            val dataLen = buffer.int
            require(buffer.remaining() >= dataLen) {
                "Buffer too short for data ($dataLen bytes expected, ${buffer.remaining()} available)"
            }
            val data = ByteArray(dataLen).also { buffer.get(it) }

            FileEntry(name, data)
        }
    }

    fun isMultiFile(dec: ByteArray): Boolean = dec.isNotEmpty() && dec[0] == MULTI_FILE_MAGIC

    fun packSingleFile(
        name: String,
        data: ByteArray,
    ): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        require(nameBytes.size <= MAX_FILENAME_LEN) { "Filename too long: $name" }

        return ByteBuffer
            .allocate(1 + nameBytes.size + data.size)
            .apply {
                put(nameBytes.size.toByte())
                put(nameBytes)
                put(data)
            }.array()
    }

    fun unpackSingleFile(dec: ByteArray): Pair<String, ByteArray> {
        val buffer = ByteBuffer.wrap(dec)
        require(buffer.hasRemaining()) { "Buffer is empty during single-file unpack" }
        val nameLen = buffer.get().toInt() and BYTE_MASK
        require(buffer.remaining() >= nameLen) {
            "Buffer too short for filename ($nameLen bytes expected, ${buffer.remaining()} available)"
        }
        val nameBytes = ByteArray(nameLen).also { buffer.get(it) }
        val name = String(nameBytes, Charsets.UTF_8)
        val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
        return name to data
    }

    fun splitBytes(
        bytes: ByteArray,
        chunkSize: Int,
    ): List<ByteArray> =
        bytes.indices.step(chunkSize).map { offset ->
            bytes.copyOfRange(offset, minOf(offset + chunkSize, bytes.size))
        }
}

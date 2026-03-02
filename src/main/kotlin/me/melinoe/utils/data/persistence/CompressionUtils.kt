package me.melinoe.utils.data.persistence

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Utility class for compression and encoding operations.
 * Provides GZIP compression/decompression and Base64 encoding/decoding.
 */
object CompressionUtils {
    
    /**
     * Compresses a string using GZIP compression.
     * 
     * @param input The string to compress
     * @return Compressed data as ByteArray
     * @throws Exception if compression fails
     */
    fun compress(input: String): ByteArray {
        val outputStream = ByteArrayOutputStream()
        GZIPOutputStream(outputStream).use { gzip ->
            gzip.write(input.toByteArray(Charsets.UTF_8))
        }
        return outputStream.toByteArray()
    }
    
    /**
     * Decompresses GZIP-compressed data back to a string.
     * 
     * @param compressed The compressed data
     * @return Decompressed string
     * @throws Exception if decompression fails
     */
    fun decompress(compressed: ByteArray): String {
        val inputStream = ByteArrayInputStream(compressed)
        return GZIPInputStream(inputStream).use { gzip ->
            gzip.readBytes().toString(Charsets.UTF_8)
        }
    }
    
    /**
     * Encodes a byte array to Base64 string.
     * 
     * @param data The data to encode
     * @return Base64-encoded string
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun encodeBase64(data: ByteArray): String {
        return Base64.encode(data)
    }
    
    /**
     * Decodes a Base64 string to byte array.
     * 
     * @param encoded The Base64-encoded string
     * @return Decoded byte array
     * @throws IllegalArgumentException if the string is not valid Base64
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun decodeBase64(encoded: String): ByteArray {
        return Base64.decode(encoded)
    }
    
    /**
     * Compresses and encodes a string to Base64.
     * Convenience method combining compress() and encodeBase64().
     * 
     * @param input The string to compress and encode
     * @return Base64-encoded compressed string
     */
    fun compressAndEncode(input: String): String {
        return encodeBase64(compress(input))
    }
    
    /**
     * Decodes and decompresses a Base64 string.
     * Convenience method combining decodeBase64() and decompress().
     * 
     * @param encoded The Base64-encoded compressed string
     * @return Decompressed string
     */
    fun decodeAndDecompress(encoded: String): String {
        return decompress(decodeBase64(encoded))
    }
    
    /**
     * Detects if data is GZIP-compressed by checking the magic number.
     * 
     * @param data The data to check
     * @return true if data appears to be GZIP-compressed
     */
    fun isCompressed(data: ByteArray): Boolean {
        if (data.size < 2) return false
        // GZIP magic number: 0x1f 0x8b
        return data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()
    }
    
    /**
     * Attempts to decompress data, returning original if not compressed.
     * Useful for backward compatibility with uncompressed data.
     * 
     * @param data The data to decompress
     * @return Decompressed string, or original data as string if not compressed
     */
    fun decompressIfNeeded(data: ByteArray): String {
        return if (isCompressed(data)) {
            decompress(data)
        } else {
            data.toString(Charsets.UTF_8)
        }
    }
}

package me.melinoe.utils.render

import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import me.melinoe.Melinoe
import net.minecraft.client.resources.sounds.AbstractSoundInstance
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.AudioStream
import net.minecraft.client.sounds.SoundManager
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundSource
import net.minecraft.util.valueproviders.ConstantFloat
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBVorbis
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import javax.sound.sampled.AudioFormat

/**
 * Plays a supplied sound (MP3 or OGG) in place of a totem's create drop sound
 */
object CustomBagSounds {
    
    private const val DIR = "config/melinoe/totems"
    
    // The totem pop lasts 2s; cut the sound 2s after that so long tracks don't linger
    private const val MAX_PLAY_MS = 4000L
    private const val FADE_MS = 500f
    
    // Instances play `melinoe:custom_bags/<type>`
    private const val NAMESPACE = "melinoe"
    private const val ID_PREFIX = "custom_bags/"
    private const val STREAM_PREFIX = "sounds/$ID_PREFIX"
    private const val STREAM_SUFFIX = ".ogg"
    
    /** Decoded 16-bit PCM for a type, capped at [MAX_PLAY_MS] worth of samples */
    private class Pcm(val mtime: FileTime, val samples: ShortArray, val sampleRate: Int, val channels: Int)
    private val cache = HashMap<String, Pcm>()
    
    @Volatile
    private var current: SoundInstance? = null
    
    fun friendlyFromCreateSuffix(suffix: String): String? = CustomBagImages.friendlyFromSoundSuffix(suffix)
    fun hasSound(type: String): Boolean = soundFile(type) != null
    
    private fun soundFile(type: String): Path? =
        firstExisting(Paths.get(DIR, "${type}_create.ogg"), Paths.get(DIR, "${type}_create.mp3"))
    
    // ==================== IMPORT / CLEAR ====================
    
    /**
     * Import [sourceFile] (an .mp3 or .ogg) as the create sound for [type]
     */
    fun saveSoundFrom(type: String, sourceFile: Path): Boolean {
        return try {
            val name = sourceFile.fileName.toString().lowercase()
            val isOgg = name.endsWith(".ogg")
            val isMp3 = name.endsWith(".mp3")
            if (!isOgg && !isMp3) return false
            if (!withinSizeLimit(sourceFile)) return false

            val bytes = Files.readAllBytes(sourceFile)
            if (isOgg && !looksLikeOgg(bytes)) return false
            if (isMp3 && !looksLikeMp3(bytes)) return false
            
            val dir = Paths.get(DIR)
            Files.createDirectories(dir)
            val oggDest = dir.resolve("${type}_create.ogg")
            val mp3Dest = dir.resolve("${type}_create.mp3")
            if (isOgg) {
                Files.copy(sourceFile, oggDest, StandardCopyOption.REPLACE_EXISTING)
                Files.deleteIfExists(mp3Dest)
            } else {
                Files.copy(sourceFile, mp3Dest, StandardCopyOption.REPLACE_EXISTING)
                Files.deleteIfExists(oggDest)
            }
            synchronized(cache) { cache.remove(type) }
            Melinoe.logger.info("Saved custom totem create sound '$type' from $sourceFile")
            true
        } catch (e: Exception) {
            Melinoe.logger.warn("Failed to save custom totem sound for '$type' from $sourceFile: ${e.message}", e)
            false
        }
    }
    
    /** Delete the custom create sound (mp3 and/or ogg) for [type] */
    fun clearSound(type: String): Boolean {
        return try {
            val mp3 = Files.deleteIfExists(Paths.get(DIR, "${type}_create.mp3"))
            val ogg = Files.deleteIfExists(Paths.get(DIR, "${type}_create.ogg"))
            synchronized(cache) { cache.remove(type) }
            mp3 || ogg
        } catch (e: Exception) {
            Melinoe.logger.warn("Failed to clear custom totem sound for '$type': ${e.message}", e)
            false
        }
    }
    
    private fun looksLikeOgg(b: ByteArray): Boolean =
        b.size >= 4 && b[0] == 'O'.code.toByte() && b[1] == 'g'.code.toByte() &&
                b[2] == 'g'.code.toByte() && b[3] == 'S'.code.toByte()
    
    private fun looksLikeMp3(b: ByteArray): Boolean {
        if (b.size < 3) return false
        if (b[0] == 'I'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == '3'.code.toByte()) return true // ID3 tag
        return (b[0].toInt() and 0xFF) == 0xFF && (b[1].toInt() and 0xE0) == 0xE0 // MPEG frame sync
    }
    
    // ==================== PLAYBACK ====================
    
    /**
     * Play the custom create sound for [type] at [volume] (0..1) through the vanilla sound engine,
     * stopping any custom sound already playing
     */
    fun playCreate(type: String, volume: Float = 1f) {
        if (!hasSound(type)) return
        stop()
        val instance = CustomBagSoundInstance(type, volume.coerceIn(0f, 1f))
        current = instance
        Melinoe.mc.soundManager.play(instance)
    }
    
    /** Stop whatever custom sound is currently playing */
    fun stop() {
        current?.let { Melinoe.mc.soundManager.stop(it) }
        current = null
    }
    
    /**
     * A sound that bypasses sounds.json
     */
    private class CustomBagSoundInstance(type: String, volume: Float) : AbstractSoundInstance(
        Identifier.fromNamespaceAndPath(NAMESPACE, ID_PREFIX + type),
        SoundSource.MASTER,
        SoundInstance.createUnseededRandom(),
    ) {
        init {
            this.volume = volume
            this.relative = true
            this.attenuation = SoundInstance.Attenuation.NONE
        }
        
        override fun resolve(manager: SoundManager): WeighedSoundEvents {
            val events = WeighedSoundEvents(identifier, null)
            val one = ConstantFloat.of(1f)
            val entry = Sound(identifier, one, one, 1, Sound.Type.FILE, true /* stream */, false, 16)
            sound = entry
            events.addSound(entry)
            return events
        }
    }
    
    // ==================== STREAM SERVING ====================
    
    /** Whether [id] is one of our custom bag sound stream paths */
    fun isCustomSound(id: Identifier): Boolean = typeFromStreamId(id) != null
    
    /** Open a PCM stream for [id], decoding the user's file on first use */
    fun openStream(id: Identifier): AudioStream {
        val type = typeFromStreamId(id) ?: throw IllegalArgumentException("Not a custom bag sound: $id")
        val pcm = load(type) ?: throw IOException("No custom bag sound available for '$type'")
        return PcmStream(pcm)
    }
    
    private fun typeFromStreamId(id: Identifier): String? {
        if (id.namespace != NAMESPACE) return null
        val path = id.path
        if (!path.startsWith(STREAM_PREFIX) || !path.endsWith(STREAM_SUFFIX)) return null
        return path.substring(STREAM_PREFIX.length, path.length - STREAM_SUFFIX.length)
    }
    
    /** Serves [pcm] in chunks; the fade/cutoff are positional, and an empty buffer signals the end */
    private class PcmStream(private val pcm: Pcm) : AudioStream {
        private val format = AudioFormat(pcm.sampleRate.toFloat(), 16, pcm.channels, true, false)
        // The fade is measured against the full play window
        private val windowSamples = cutoffSamples(pcm.sampleRate, pcm.channels)
        private var pos = 0
        
        override fun getFormat(): AudioFormat = format
        
        override fun read(size: Int): ByteBuffer {
            val remaining = pcm.samples.size - pos
            // Whole frames only
            val count = minOf(size / 2, remaining.coerceAtLeast(0)).let { it - it % pcm.channels }
            if (count <= 0) return EMPTY // end of stream
            val buf = BufferUtils.createByteBuffer(count * 2)
            for (i in pos until pos + count) {
                val s = (pcm.samples[i] * gainAt(i)).toInt().coerceIn(-32768, 32767)
                buf.put((s and 0xFF).toByte())
                buf.put(((s shr 8) and 0xFF).toByte())
            }
            pos += count
            buf.flip()
            return buf
        }
        
        /** 1.0 until [FADE_MS] before the cutoff, then ramping down to 0 */
        private fun gainAt(sampleIndex: Int): Float {
            val remainingMs = (windowSamples - sampleIndex).toFloat() * 1000f / (pcm.sampleRate * pcm.channels)
            return (remainingMs / FADE_MS).coerceIn(0f, 1f)
        }
        
        override fun close() {}
        
        private companion object {
            private val EMPTY: ByteBuffer = BufferUtils.createByteBuffer(0)
        }
    }
    
    // ==================== DECODING ====================
    
    /** Number of samples in the [MAX_PLAY_MS] play window at the given rate/channel count */
    private fun cutoffSamples(sampleRate: Int, channels: Int): Int =
        (MAX_PLAY_MS * sampleRate / 1000).toInt() * channels
    
    /** Decode (and cache) the sound file for [type], re-decoding only when it changes on disk */
    private fun load(type: String): Pcm? {
        val file = soundFile(type) ?: return null
        val mtime = Files.getLastModifiedTime(file)
        synchronized(cache) { cache[type]?.let { if (it.mtime == mtime) return it } }
        val pcm = try {
            val bytes = Files.readAllBytes(file)
            if (file.fileName.toString().lowercase().endsWith(".ogg")) decodeOgg(bytes, mtime) else decodeMp3(bytes, mtime)
        } catch (e: Exception) {
            Melinoe.logger.warn("Failed to decode custom bag sound for '$type': ${e.message}")
            return null
        }
        synchronized(cache) { cache[type] = pcm }
        return pcm
    }
    
    private fun decodeOgg(bytes: ByteArray, mtime: FileTime): Pcm {
        val data = MemoryUtil.memAlloc(bytes.size)
        var decoded: ShortBuffer? = null
        try {
            data.put(bytes).flip()
            MemoryStack.stackPush().use { stack ->
                val channelsBuf = stack.mallocInt(1)
                val rateBuf = stack.mallocInt(1)
                val pcm = STBVorbis.stb_vorbis_decode_memory(data, channelsBuf, rateBuf)
                    ?: throw IOException("STB Vorbis failed to decode OGG")
                decoded = pcm
                val channels = channelsBuf.get(0)
                val rate = rateBuf.get(0)
                val samples = ShortArray(minOf(pcm.remaining(), cutoffSamples(rate, channels)))
                pcm.get(samples)
                return Pcm(mtime, samples, rate, channels)
            }
        } finally {
            decoded?.let { MemoryUtil.memFree(it) }
            MemoryUtil.memFree(data)
        }
    }
    
    private fun decodeMp3(bytes: ByteArray, mtime: FileTime): Pcm {
        val bitstream = Bitstream(ByteArrayInputStream(bytes))
        val decoder = Decoder()
        var sampleRate = 0
        var channels = 0
        val chunks = ArrayList<ShortArray>()
        var total = 0
        try {
            while (true) {
                val header = bitstream.readFrame() ?: break
                val out = decoder.decodeFrame(header, bitstream) as SampleBuffer
                if (sampleRate == 0) {
                    sampleRate = out.sampleFrequency
                    channels = out.channelCount
                }
                chunks.add(out.buffer.copyOf(out.bufferLength))
                total += out.bufferLength
                bitstream.closeFrame()
                // Never decode past the cutoff
                if (total >= cutoffSamples(sampleRate, channels)) break
            }
        } finally {
            try { bitstream.close() } catch (_: Exception) {}
        }
        if (sampleRate == 0 || total == 0) throw IOException("No MP3 frames decoded")
        val samples = ShortArray(minOf(total, cutoffSamples(sampleRate, channels)))
        var off = 0
        for (chunk in chunks) {
            if (off >= samples.size) break
            val n = minOf(chunk.size, samples.size - off)
            System.arraycopy(chunk, 0, samples, off, n)
            off += n
        }
        return Pcm(mtime, samples, sampleRate, channels)
    }
}
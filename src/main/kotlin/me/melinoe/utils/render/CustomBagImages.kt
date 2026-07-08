package me.melinoe.utils.render

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import me.melinoe.Melinoe
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import java.awt.AlphaComposite
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode

internal fun firstExisting(vararg candidates: Path): Path? = candidates.firstOrNull { Files.exists(it) }
internal const val MAX_CUSTOM_FILE_BYTES = 15L * 1024 * 1024
internal fun withinSizeLimit(file: Path): Boolean = Files.size(file) <= MAX_CUSTOM_FILE_BYTES

/**
 * Runtime override that swaps a bag totem's model for our own screen and fills
 * it with the image that the user provided
 */
object CustomBagImages {
    
    private const val DIR = "config/melinoe/totems"
    private val CENTER_SPRITE = Identifier.fromNamespaceAndPath("melinoe", "mob/bags/image")
    
    private data class Bag(
        val label: String,
        val friendly: String,
        val path: String,
        val modelStem: String,
        val soundSuffix: String,
    )
    
    private val BAGS: List<Bag> = listOf(
        Bag("Companion", "companion", "mob/pouch/companion", "companion", "companion"),
        Bag("Shiny", "shiny", "mob/pouch/shiny_totem", "shiny_totem", "shiny"),
        Bag("Unholy", "unholy", "mob/pouch/unholy_totem", "unholy_totem", "unholy"),
        Bag("Voidbound", "voidbound", "mob/pouch/voidbound_totem", "voidbound_totem", "voidbound"),
        Bag("Bloodshot", "bloodshot", "mob/pouch/bloodshot_totem", "bloodshot_totem", "bloodshot"),
        Bag("Royal", "royal", "mob/pouch/royal_totem", "royal_totem", "royal"),
        Bag("Gilded", "gilded", "mob/pouch/gilded_totem", "gilded_totem", "gilded"),
        Bag("Irradiated", "irradiated", "mob/pouch/irradiated_totem", "irradiated_totem", "irradiated"),
        Bag("Halloween", "halloween", "mob/pouch/halloween_totem", "halloween_totem", "halloween"),
        Bag("Valentine", "valentine", "mob/pouch/valentine_totem", "valentine_totem", "valentines"),
        Bag("Christmas", "christmas", "mob/pouch/christmas_totem", "christmas_totem", "christmas"),
    )
    
    private val byPath: Map<String, Bag> = BAGS.associateBy { it.path }
    private val byFriendly: Map<String, Bag> = BAGS.associateBy { it.friendly }
    
    val typeLabels: List<Pair<String, String>> = BAGS.map { it.label to it.friendly }
    fun soundSuffixFor(friendly: String): String? = byFriendly[friendly]?.soundSuffix
    fun friendlyFromSoundSuffix(suffix: String): String? = BAGS.firstOrNull { it.soundSuffix == suffix }?.friendly
    fun telosIdentifierFor(friendly: String): Identifier? =
        byFriendly[friendly]?.let { Identifier.fromNamespaceAndPath("telos", it.path) }
    
    private class Frame(val image: NativeImage, var delayMs: Int)
    private class Entry(val mtime: FileTime, val frames: List<Frame>)
    private val sources = HashMap<String, Entry>()
    
    // Atlas + sprite are resolved once when the animation starts and reused every frame
    private class ActiveAnim(
        val frames: List<Frame>,
        val startMs: Long,
        val totalMs: Int,
        val atlas: TextureAtlas,
        val sprite: TextureAtlasSprite,
    )
    @Volatile private var active: ActiveAnim? = null
    private var tickerRegistered = false
    private const val MAX_ANIM_MS = 4000L // safety window

    // ==================== IMPORT / CLEAR ====================
    
    /**
     * Import [sourceFile] as the custom image for [type]
     */
    fun saveImageFrom(type: String, sourceFile: Path): Boolean {
        return try {
            if (!withinSizeLimit(sourceFile)) return false

            val dir = Paths.get(DIR)
            Files.createDirectories(dir)
            val png = dir.resolve("$type.png")
            val gif = dir.resolve("$type.gif")

            if (sourceFile.fileName.toString().lowercase().endsWith(".gif")) {
                if (gifFrameCount(sourceFile) < 1) return false      // validate it's a readable GIF
                Files.copy(sourceFile, gif, StandardCopyOption.REPLACE_EXISTING)
                Files.deleteIfExists(png)
            } else {
                // ImageIO reads JPG/PNG
                val img = ImageIO.read(sourceFile.toFile())
                if (img != null) {
                    ImageIO.write(img, "png", png.toFile())
                } else {
                    // Last resort for formats ImageIO lacks
                    val ni = Files.newInputStream(sourceFile).use { NativeImage.read(it) }
                    try { ni.writeToFile(png) } finally { ni.close() }
                }
                Files.deleteIfExists(gif)
            }
            synchronized(sources) { sources.remove(type)?.frames?.forEach { it.image.close() } }
            // Warm GIFs via the tracked async decoder so the first preview doesn't freeze and the
            // Preview button can show "Processing"
            resolveFile(type)?.let { if (isGif(it)) warmAsync(type, it) }
            Melinoe.logger.info("Saved custom totem image '$type' from $sourceFile")
            true
        } catch (e: Exception) {
            Melinoe.logger.warn("Failed to save custom totem image for '$type' from $sourceFile: ${e.message}", e)
            false
        }
    }
    
    /** Delete the custom image */
    fun clearImage(type: String): Boolean {
        return try {
            val png = Files.deleteIfExists(Paths.get(DIR, "$type.png"))
            val gif = Files.deleteIfExists(Paths.get(DIR, "$type.gif"))
            synchronized(sources) { sources.remove(type)?.frames?.forEach { it.image.close() } }
            png || gif
        } catch (e: Exception) {
            Melinoe.logger.warn("Failed to clear custom totem image for '$type': ${e.message}", e)
            false
        }
    }
    
    // ==================== APPLY AT DROP ====================
    
    /**
     * If the given totem type has a custom image, retarget [stack] to our screen model and paint the
     * image over [CENTER_SPRITE]
     */
    fun applyFor(stack: ItemStack, path: String) {
        try {
            val bag = byPath[path] ?: return
            val file = resolveFile(bag.friendly) ?: return // no custom image -> keep the default totem
            
            // Get the frames without ever decoding a GIF on the render thread
            val entry = cachedEntry(bag.friendly, file)
                ?: if (isGif(file)) {
                    warmAsync(bag.friendly, file)
                    return
                } else {
                    framesFor(bag.friendly, file) ?: return // static image
                }
            if (entry.frames.isEmpty()) return
            
            val (atlas, sprite) = resolveSprite() ?: run {
                Melinoe.logger.warn("Custom totem image: sprite $CENTER_SPRITE not stitched into any atlas; rebuild/reload resources?")
                return
            }
            
            // Render our flat screen instead of the server's chest
            stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("melinoe", "bags/${bag.modelStem}"))
            
            uploadIntoSprite(atlas, sprite, entry.frames[0].image)
            
            active = if (entry.frames.size > 1) {
                ensureTicker()
                ActiveAnim(entry.frames, System.currentTimeMillis(), entry.frames.sumOf { it.delayMs }, atlas, sprite)
            } else {
                null // static image
            }
        } catch (e: Exception) {
            Melinoe.logger.warn("Failed to apply custom totem for '$path': ${e.message}", e)
        }
    }
    
    private fun resolveFile(key: String): Path? =
        firstExisting(Paths.get(DIR, "$key.gif"), Paths.get(DIR, "$key.png"))
    
    /** Register the once-off tick loop that advances the active GIF */
    private fun ensureTicker() {
        if (tickerRegistered) return
        tickerRegistered = true
        ClientTickEvents.END_CLIENT_TICK.register { advance() }
    }
    
    /** Upload the GIF frame due at the current elapsed time */
    private fun advance() {
        val a = active ?: return
        val elapsed = System.currentTimeMillis() - a.startMs
        if (elapsed > MAX_ANIM_MS || a.totalMs <= 0) {
            active = null
            return
        }
        val t = elapsed % a.totalMs
        var acc = 0L
        var idx = a.frames.size - 1
        for (i in a.frames.indices) {
            acc += a.frames[i].delayMs
            if (t < acc) { idx = i; break }
        }
        try {
            uploadIntoSprite(a.atlas, a.sprite, a.frames[idx].image)
        } catch (e: Exception) {
            Melinoe.logger.debug("Totem GIF frame upload failed: ${e.message}")
        }
    }
    
    // ==================== DECODING ====================
    
    private fun isGif(file: Path): Boolean = file.fileName.toString().lowercase().endsWith(".gif")
    
    /** Non-blocking cache lookup: returns the decoded frames only if already cached and fresh */
    private fun cachedEntry(key: String, file: Path): Entry? {
        val mtime = Files.getLastModifiedTime(file)
        synchronized(sources) { sources[key]?.let { if (it.mtime == mtime) return it } }
        return null
    }
    
    /** Keys currently being decoded on a background thread, to avoid launching duplicate decodes */
    private val decoding = HashSet<String>()
    
    /** Whether [type]'s GIF is being decoded right now */
    fun isProcessing(type: String): Boolean = synchronized(decoding) { type in decoding }
    
    /**
     * Decode every configured custom GIF into the cache now, so drops never decode on
     * the render thread
     */
    fun warmAll() {
        for (bag in BAGS) {
            val file = resolveFile(bag.friendly) ?: continue
            if (isGif(file)) warmAsync(bag.friendly, file)
        }
    }
    
    /** Decode [file] for [key] on a background thread and cache it */
    private fun warmAsync(key: String, file: Path) {
        if (cachedEntry(key, file) != null) return // already cached
        synchronized(decoding) { if (!decoding.add(key)) return }
        Thread({
            try {
                framesFor(key, file)
            } catch (e: Exception) {
                Melinoe.logger.warn("Failed to decode custom totem GIF for '$key': ${e.message}")
            } finally {
                synchronized(decoding) { decoding.remove(key) }
            }
        }, "melinoe-bag-gif-decode").apply { isDaemon = true }.start()
    }
    
    /** Read (and cache) frames for [key], re-decoding only when the file changes on disk */
    private fun framesFor(key: String, file: Path): Entry? {
        val mtime = Files.getLastModifiedTime(file)
        synchronized(sources) { sources[key]?.let { if (it.mtime == mtime) return it } }
        
        val frames = if (file.fileName.toString().lowercase().endsWith(".gif")) {
            decodeGif(file)
        } else {
            listOf(Frame(decodeStatic(file), 100))
        }
        if (frames.isEmpty()) return null
        
        synchronized(sources) {
            val cached = sources[key]
            if (cached != null && cached.mtime == mtime) {
                frames.forEach { it.image.close() }
                return cached
            }
            cached?.frames?.forEach { it.image.close() }
            val entry = Entry(mtime, frames)
            sources[key] = entry
            return entry
        }
    }
    
    /** Decode a single still image. STB handles the normalized PNG; ImageIO covers anything else */
    private fun decodeStatic(file: Path): NativeImage {
        return try {
            capSize(Files.newInputStream(file).use { NativeImage.read(it) })
        } catch (e: Exception) {
            val img = ImageIO.read(file.toFile()) ?: throw e
            toNativeImage(snapshotFrame(img))
        }
    }

    /** Downscale [image] to at most [MAX_FRAME_DIM] on each side, or return it unchanged if it fits */
    private fun capSize(image: NativeImage): NativeImage {
        if (image.width <= MAX_FRAME_DIM && image.height <= MAX_FRAME_DIM) return image
        val scale = MAX_FRAME_DIM.toFloat() / maxOf(image.width, image.height)
        val scaled = NativeImage((image.width * scale).toInt().coerceAtLeast(1), (image.height * scale).toInt().coerceAtLeast(1), false)
        try {
            image.resizeSubRectTo(0, 0, image.width, image.height, scaled)
        } catch (e: Exception) {
            scaled.close()
            throw e
        } finally {
            image.close()
        }
        return scaled
    }
    
    private fun gifFrameCount(file: Path): Int {
        Files.newInputStream(file).use { ins ->
            ImageIO.createImageInputStream(ins).use { iis ->
                val readers = ImageIO.getImageReadersByFormatName("gif")
                if (!readers.hasNext()) return 0
                val reader = readers.next()
                return try {
                    reader.input = iis
                    reader.getNumImages(true)
                } finally {
                    reader.dispose()
                }
            }
        }
    }
    
    /**
     * Decode an animated GIF into fully-composited frames
     */
    private fun decodeGif(file: Path): List<Frame> {
        val frames = ArrayList<Frame>()
        // Nested .use: closing an ImageInputStream does NOT close the stream it wraps
        Files.newInputStream(file).use { ins -> ImageIO.createImageInputStream(ins).use { iis ->
            val readers = ImageIO.getImageReadersByFormatName("gif")
            if (!readers.hasNext()) return emptyList()
            val reader = readers.next()
            try {
                reader.input = iis
                val count = reader.getNumImages(true)
                val (lw, lh) = logicalSize(reader) ?: (reader.getWidth(0) to reader.getHeight(0))
                // When the GIF has more frames than we keep, sample them evenly across the
                // animation; null means keep everything
                val keep: Set<Int>? = if (count > MAX_FRAMES) {
                    (0 until MAX_FRAMES).mapTo(HashSet()) { it * count / MAX_FRAMES }
                } else null
                var canvas: BufferedImage? = null
                var prevDisposal = "none"
                var prevX = 0; var prevY = 0; var prevW = 0; var prevH = 0

                for (i in 0 until count) {
                    val frame = reader.read(i)
                    val meta = gifFrameMeta(reader.getImageMetadata(i))
                    if (canvas == null) {
                        canvas = BufferedImage(maxOf(lw, frame.width), maxOf(lh, frame.height), BufferedImage.TYPE_INT_ARGB)
                    }
                    val g = canvas.createGraphics()
                    // Apply the previous frame's disposal before drawing this one
                    if (prevDisposal == "restoreToBackgroundColor") {
                        g.composite = AlphaComposite.Clear
                        g.fillRect(prevX, prevY, prevW, prevH)
                        g.composite = AlphaComposite.SrcOver
                    }
                    g.drawImage(frame, meta.x, meta.y, null)
                    g.dispose()
                    
                    if (keep == null || i in keep) {
                        // Snapshot the composited canvas as this frame
                        frames.add(Frame(toNativeImage(snapshotFrame(canvas)), meta.delayMs))
                    } else {
                        // Dropped frame: fold its delay into the kept frame before it
                        frames.lastOrNull()?.let { it.delayMs += meta.delayMs }
                    }

                    prevDisposal = meta.disposal
                    prevX = meta.x; prevY = meta.y; prevW = frame.width; prevH = frame.height
                }
            } finally {
                reader.dispose()
            }
        } }
        return frames
    }
    
    private const val MAX_FRAME_DIM = 512
    private const val MAX_FRAMES = 30

    /** Copy [canvas], downscaled to at most [MAX_FRAME_DIM] on each side */
    private fun snapshotFrame(canvas: BufferedImage): BufferedImage {
        val scale = minOf(1f, MAX_FRAME_DIM.toFloat() / maxOf(canvas.width, canvas.height))
        val w = (canvas.width * scale).toInt().coerceAtLeast(1)
        val h = (canvas.height * scale).toInt().coerceAtLeast(1)
        val snap = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = snap.createGraphics()
        if (scale < 1f) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        }
        g.drawImage(canvas, 0, 0, w, h, null)
        g.dispose()
        return snap
    }
    
    private class GifMeta(val x: Int, val y: Int, val delayMs: Int, val disposal: String)
    
    private fun gifFrameMeta(meta: javax.imageio.metadata.IIOMetadata): GifMeta {
        var x = 0; var y = 0; var delayMs = 100; var disposal = "none"
        val root = meta.getAsTree("javax_imageio_gif_image_1.0") as IIOMetadataNode
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? IIOMetadataNode ?: continue
            when (node.nodeName) {
                "ImageDescriptor" -> {
                    x = node.getAttribute("imageLeftPosition").toIntOrNull() ?: 0
                    y = node.getAttribute("imageTopPosition").toIntOrNull() ?: 0
                }
                "GraphicControlExtension" -> {
                    delayMs = ((node.getAttribute("delayTime").toIntOrNull() ?: 10) * 10).coerceAtLeast(20)
                    disposal = node.getAttribute("disposalMethod").ifBlank { "none" }
                }
            }
        }
        return GifMeta(x, y, delayMs, disposal)
    }
    
    private fun logicalSize(reader: javax.imageio.ImageReader): Pair<Int, Int>? {
        return try {
            val sm = reader.streamMetadata ?: return null
            val root = sm.getAsTree("javax_imageio_gif_stream_1.0") as IIOMetadataNode
            val lsd = root.getElementsByTagName("LogicalScreenDescriptor").item(0) as? IIOMetadataNode ?: return null
            val w = lsd.getAttribute("logicalScreenWidth").toIntOrNull() ?: return null
            val h = lsd.getAttribute("logicalScreenHeight").toIntOrNull() ?: return null
            if (w > 0 && h > 0) w to h else null
        } catch (e: Exception) {
            null
        }
    }
    
    private fun toNativeImage(img: BufferedImage): NativeImage {
        val w = img.width
        val h = img.height
        val argb = img.getRGB(0, 0, w, h, null, 0, w)
        val ni = NativeImage(w, h, false)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val p = argb[row + x]
                val abgr = (p and 0xFF00FF00.toInt()) or ((p and 0xFF) shl 16) or ((p ushr 16) and 0xFF)
                ni.setPixelABGR(x, y, abgr)
            }
        }
        return ni
    }
    
    // ==================== ATLAS UPLOAD ====================
    
    /** Locate the atlas that actually contains the sprite */
    private fun resolveSprite(): Pair<TextureAtlas, TextureAtlasSprite>? {
        var found: Pair<TextureAtlas, TextureAtlasSprite>? = null
        Melinoe.mc.atlasManager.forEach { _, atlas ->
            if (found == null) {
                val sprite = atlas.getSprite(CENTER_SPRITE)
                // getSprite returns the "missing" sprite when absent
                if (sprite.contents().name() == CENTER_SPRITE) {
                    found = atlas to sprite
                }
            }
        }
        return found
    }
    
    /**
     * Scale [source] to the sprite's dimensions and write it over the sprite region at every mip
     * level, so the swapped image stays correct as the totem animation scales it up and down
     */
    private fun uploadIntoSprite(atlas: TextureAtlas, sprite: TextureAtlasSprite, source: NativeImage) {
        val gpuTexture = atlas.texture
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        val spriteW = sprite.contents().width()
        val spriteH = sprite.contents().height()
        
        val mipLevels = gpuTexture.mipLevels
        for (mip in 0 until mipLevels) {
            val w = spriteW shr mip
            val h = spriteH shr mip
            if (w <= 0 || h <= 0) break
            
            val scaled = NativeImage(w, h, false)
            try {
                source.resizeSubRectTo(0, 0, source.width, source.height, scaled)
                encoder.writeToTexture(
                    gpuTexture, scaled,
                    mip, 0,
                    sprite.getX() shr mip, sprite.getY() shr mip,
                    w, h,
                    0, 0,
                )
            } finally {
                scaled.close()
            }
        }
    }
}
package me.melinoe.utils.ui.rendering

import me.melinoe.Melinoe
import me.melinoe.Melinoe.mc
import me.melinoe.utils.Color.Companion.alpha
import me.melinoe.utils.Color.Companion.blue
import me.melinoe.utils.Color.Companion.green
import me.melinoe.utils.Color.Companion.red
import net.minecraft.resources.Identifier
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NanoSVG.*
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.nanovg.NanoVGGL3.*
import org.lwjgl.stb.STBImage.stbi_load_from_memory
import org.lwjgl.system.MemoryUtil.memAlloc
import org.lwjgl.system.MemoryUtil.memFree
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object NVGRenderer {

    private val nvgPaint = NVGPaint.malloc()
    private val nvgColor = NVGColor.malloc()
    private val nvgColor2: NVGColor = NVGColor.malloc()

    val defaultFont: Font by lazy {
        try {
            Font("Default", mc.resourceManager.getResource(Identifier.parse("melinoe:font.ttf")).get().open())
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to load font: melinoe:font.ttf", e)
            Font("Default", java.io.ByteArrayInputStream(ByteArray(0)))
        }
    }

    object EmojiData {
        data class Asset(val path: String, val col: Int, val totalCols: Int)
        val emojiMap = mutableMapOf<Char, Asset>()
        
        init {
            val providers = listOf(
                "melinoe:emoji/face_smiling.png" to "\uF400\uF401\uF402\uF403\uF404\uF405\uF406\uF407\uF408\uF409\uF40A\uF40B\uF40C",
                "melinoe:emoji/face_affection.png" to "\uF40D\uF40E\uF40F\uF410\uF411\uF412",
                "melinoe:emoji/face_tongue.png" to "\uF413\uF414\uF415\uF416\uF417\uF418",
                "melinoe:emoji/face_hand.png" to "\uF419\uF41A\uF41B\uF41C\uF41D\uF41E\uF41F",
                "melinoe:emoji/face_neutral_skeptical.png" to "\uF420\uF421\uF422\uF423\uF424\uF425\uF426\uF427\uF428\uF429\uF42A",
                "melinoe:emoji/face_sleepy.png" to "\uF42B\uF42C\uF42D\uF42E\uF42F\uF430",
                "melinoe:emoji/face_unwell.png" to "\uF431\uF432\uF433\uF434\uF435\uF436\uF437",
                "melinoe:emoji/face_hat.png" to "\uF438",
                "melinoe:emoji/face_glasses.png" to "\uF439\uF43A\uF43B",
                "melinoe:emoji/face_concerned.png" to "\uF43C\uF43D\uF43E\uF43F\uF440\uF441\uF442\uF443\uF444\uF445\uF446\uF447\uF448\uF449\uF44A\uF44B\uF44C\uF44D",
                "melinoe:emoji/face_negative.png" to "\uF44E\uF44F\uF450\uF451",
                "melinoe:emoji/face_costume.png" to "\uF452\uF453\uF454\uF455",
                "melinoe:emoji/cat_face.png" to "\uF456\uF457\uF458\uF459\uF45A\uF45B\uF45C\uF45D\uF45E",
                "melinoe:emoji/monkey_face.png" to "\uF45F\uF460\uF461",
                "melinoe:emoji/heart.png" to "\uF462\uF463\uF464\uF465\uF466\uF467\uF468\uF469",
                "melinoe:emoji/emotion.png" to "\uF46A\uF46B\uF46C\uF46D\uF46E",
                "melinoe:emoji/hand_fingers_open.png" to "\uF46F",
                "melinoe:emoji/hand_fingers_partial.png" to "\uF470\uF471\uF472\uF473\uF474\uF475",
                "melinoe:emoji/hand_single_finger.png" to "\uF476\uF477\uF478\uF479\uF47A\uF47B",
                "melinoe:emoji/hand_fingers_closed.png" to "\uF47C\uF47D\uF47E\uF47F\uF480\uF481",
                "melinoe:emoji/hands.png" to "\uF482\uF483\uF484\uF485\uF486\uF487\uF488",
                "melinoe:emoji/body_parts.png" to "\uF48C\uF48D\uF48E\uF48F",
                "melinoe:emoji/plant_flower.png" to "\uF490",
                "melinoe:emoji/award_medal.png" to "\uF491\uF492\uF493\uF494",
                "melinoe:emoji/heart_on_fire_mending_heart.png" to "\uF497\uF498",
                "melinoe:emoji/emoji.png" to "\uF499\uF49A\uF49B\uF49C\uF49D\uF49E\uF49F\uF4A0\uF4A1\uF4A2\uF4A3\uF4A4\uF4A5\uF4A6\uF4A7\uF4A8\uF4A9\uF4AA\uF4AB\uF4AC\uF4AD\uF4AE\uF4AF\uF4B0\uF4B1"
            )
            for ((path, chars) in providers) {
                val total = chars.length
                for (i in chars.indices) {
                    emojiMap[chars[i]] = Asset(path, i, total)
                }
            }
        }
    }
    
    private val fontMap = HashMap<Font, NVGFont>()
    private val fontBounds = FloatArray(4)

    private val images = HashMap<Image, NVGImage>()

    private val nvgEmojis = mutableMapOf<String, Int>()
    
    private var scissor: Scissor? = null
    private var drawing: Boolean = false
    private var vg = -1L

    init {
        vg = nvgCreate(NVG_ANTIALIAS or NVG_STENCIL_STROKES)
        require(vg != -1L) { "Failed to initialize NanoVG" }
    }

    fun devicePixelRatio(): Float {
        return try {
            val window = mc.window
            val fbw = window.width
            val ww = window.screenWidth
            if (ww == 0) 1f else fbw.toFloat() / ww.toFloat()
        } catch (_: Throwable) {
            1f
        }
    }

    fun beginFrame(width: Float, height: Float) {
        if (drawing) throw IllegalStateException("[NVGRenderer] Already drawing, but called beginFrame")

        val dpr = devicePixelRatio()

        nvgBeginFrame(vg, width / dpr, height / dpr, dpr)
        nvgTextAlign(vg, NVG_ALIGN_LEFT or NVG_ALIGN_TOP)
        drawing = true
    }

    fun endFrame() {
        if (!drawing) throw IllegalStateException("[NVGRenderer] Not drawing, but called endFrame")
        nvgEndFrame(vg)

        drawing = false
    }

    fun push() = nvgSave(vg)

    fun pop() = nvgRestore(vg)

    fun scale(x: Float, y: Float) = nvgScale(vg, x, y)

    fun translate(x: Float, y: Float) = nvgTranslate(vg, x, y)

    fun rotate(amount: Float) = nvgRotate(vg, amount)

    fun globalAlpha(amount: Float) = nvgGlobalAlpha(vg, amount.coerceIn(0f, 1f))

    fun pushScissor(x: Float, y: Float, w: Float, h: Float) {
        scissor = Scissor(scissor, x, y, w + x, h + y)
        scissor?.applyScissor()
    }

    fun popScissor() {
        nvgResetScissor(vg)
        scissor = scissor?.previous
        scissor?.applyScissor()
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float, color: Int) {
        nvgBeginPath(vg)
        nvgMoveTo(vg, x1, y1)
        nvgLineTo(vg, x2, y2)
        nvgStrokeWidth(vg, thickness)
        color(color)
        nvgStrokeColor(vg, nvgColor)
        nvgStroke(vg)
    }

    fun drawHalfRoundedRect(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float, roundTop: Boolean) {
        nvgBeginPath(vg)

        if (roundTop) {
            nvgMoveTo(vg, x, y + h)
            nvgLineTo(vg, x + w, y + h)
            nvgLineTo(vg, x + w, y + radius)
            nvgArcTo(vg, x + w, y, x + w - radius, y, radius)
            nvgLineTo(vg, x + radius, y)
            nvgArcTo(vg, x, y, x, y + radius, radius)
            nvgLineTo(vg, x, y + h)
        } else {
            nvgMoveTo(vg, x, y)
            nvgLineTo(vg, x + w, y)
            nvgLineTo(vg, x + w, y + h - radius)
            nvgArcTo(vg, x + w, y + h, x + w - radius, y + h, radius)
            nvgLineTo(vg, x + radius, y + h)
            nvgArcTo(vg, x, y + h, x, y + h - radius, radius)
            nvgLineTo(vg, x, y)
        }

        nvgClosePath(vg)
        color(color)
        nvgFillColor(vg, nvgColor)
        nvgFill(vg)
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float) {
        nvgBeginPath(vg)
        nvgRoundedRect(vg, x, y, w, h + .5f, radius)
        color(color)
        nvgFillColor(vg, nvgColor)
        nvgFill(vg)
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        nvgBeginPath(vg)
        nvgRect(vg, x, y, w, h + .5f)
        color(color)
        nvgFillColor(vg, nvgColor)
        nvgFill(vg)
    }

    fun hollowRect(x: Float, y: Float, w: Float, h: Float, thickness: Float, color: Int, radius: Float) {
        nvgBeginPath(vg)
        nvgRoundedRect(vg, x, y, w, h, radius)
        nvgStrokeWidth(vg, thickness)
        nvgPathWinding(vg, NVG_HOLE)
        color(color)
        nvgStrokeColor(vg, nvgColor)
        nvgStroke(vg)
    }

    fun gradientRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color1: Int,
        color2: Int,
        gradient: Gradient,
        radius: Float
    ) {
        nvgBeginPath(vg)
        nvgRoundedRect(vg, x, y, w, h, radius)
        gradient(color1, color2, x, y, w, h, gradient)
        nvgFillPaint(vg, nvgPaint)
        nvgFill(vg)
    }

    fun dropShadow(x: Float, y: Float, width: Float, height: Float, blur: Float, spread: Float, radius: Float) {
        nvgRGBA(0, 0, 0, 125, nvgColor)
        nvgRGBA(0, 0, 0, 0, nvgColor2)

        nvgBoxGradient(
            vg,
            x - spread,
            y - spread,
            width + 2 * spread,
            height + 2 * spread,
            radius + spread,
            blur,
            nvgColor,
            nvgColor2,
            nvgPaint
        )
        nvgBeginPath(vg)
        nvgRoundedRect(
            vg,
            x - spread - blur,
            y - spread - blur,
            width + 2 * spread + 2 * blur,
            height + 2 * spread + 2 * blur,
            radius + spread
        )
        nvgRoundedRect(vg, x, y, width, height, radius)
        nvgPathWinding(vg, NVG_HOLE)
        nvgFillPaint(vg, nvgPaint)
        nvgFill(vg)
    }

    fun circle(x: Float, y: Float, radius: Float, color: Int) {
        nvgBeginPath(vg)
        nvgCircle(vg, x, y, radius)
        color(color)
        nvgFillColor(vg, nvgColor)
        nvgFill(vg)
    }

    fun text(text: String, x: Float, y: Float, size: Float, color: Int, font: Font) {
        var currentX = x
        val currentText = StringBuilder()
        
        for (i in text.indices) {
            val c = text[i]
            if (EmojiData.emojiMap.containsKey(c)) {
                if (currentText.isNotEmpty()) {
                    val str = currentText.toString()
                    nvgFontSize(vg, size)
                    nvgFontFaceId(vg, getFontID(font))
                    color(color)
                    nvgFillColor(vg, nvgColor)
                    nvgText(vg, currentX, y + .5f, str)
                    currentX += nvgTextBounds(vg, 0f, 0f, str, fontBounds)
                    currentText.clear()
                }
                drawEmoji(c, currentX, y, size)
                currentX += size + 2f // Standard spacing offset
            } else {
                currentText.append(c)
            }
        }
        
        if (currentText.isNotEmpty()) {
            val str = currentText.toString()
            nvgFontSize(vg, size)
            nvgFontFaceId(vg, getFontID(font))
            color(color)
            nvgFillColor(vg, nvgColor)
            nvgText(vg, currentX, y + .5f, str)
        }
    }
    
    fun textShadow(text: String, x: Float, y: Float, size: Float, color: Int, font: Font) {
        var currentX = x
        val currentText = StringBuilder()
        
        for (i in text.indices) {
            val c = text[i]
            if (EmojiData.emojiMap.containsKey(c)) {
                if (currentText.isNotEmpty()) {
                    val str = currentText.toString()
                    nvgFontFaceId(vg, getFontID(font))
                    nvgFontSize(vg, size)
                    
                    color(-16777216)
                    nvgFillColor(vg, nvgColor)
                    nvgText(vg, round(currentX + 2f), round(y + 2f), str)
                    
                    color(color)
                    nvgFillColor(vg, nvgColor)
                    nvgText(vg, round(currentX), round(y), str)
                    
                    currentX += nvgTextBounds(vg, 0f, 0f, str, fontBounds)
                    currentText.clear()
                }
                // Draw unshadowed emoji seamlessly
                drawEmoji(c, round(currentX), round(y), size)
                currentX += size + 2f
            } else {
                currentText.append(c)
            }
        }
        if (currentText.isNotEmpty()) {
            val str = currentText.toString()
            nvgFontFaceId(vg, getFontID(font))
            nvgFontSize(vg, size)
            
            color(-16777216)
            nvgFillColor(vg, nvgColor)
            nvgText(vg, round(currentX + 2f), round(y + 2f), str)
            
            color(color)
            nvgFillColor(vg, nvgColor)
            nvgText(vg, round(currentX), round(y), str)
        }
    }

    fun textWidth(text: String, size: Float, font: Font): Float {
        var width = 0f
        val currentText = StringBuilder()
        
        for (i in text.indices) {
            val c = text[i]
            if (EmojiData.emojiMap.containsKey(c)) {
                if (currentText.isNotEmpty()) {
                    nvgFontSize(vg, size)
                    nvgFontFaceId(vg, getFontID(font))
                    width += nvgTextBounds(vg, 0f, 0f, currentText.toString(), fontBounds)
                    currentText.clear()
                }
                width += size + 2f
            } else {
                currentText.append(c)
            }
        }
        if (currentText.isNotEmpty()) {
            nvgFontSize(vg, size)
            nvgFontFaceId(vg, getFontID(font))
            width += nvgTextBounds(vg, 0f, 0f, currentText.toString(), fontBounds)
        }
        return width
    }
    
    /**
     * Manually fetches the image bypassing Image.kt to prevent the classloader missing
     * files located inside ResourcePacks or non-standard asset hierarchies.
     */
    private fun getEmojiTextureId(path: String): Int {
        return nvgEmojis.getOrPut(path) {
            val location = Identifier.parse(path)
            
            // Replicate Minecraft BitmapProvider appending rules just in case
            val textureLocation = if (location.path.startsWith("textures/")) location else location.withPrefix("textures/")
            
            val resourceOpt = mc.resourceManager.getResource(textureLocation)
            val resource = if (resourceOpt.isPresent) resourceOpt.get() else mc.resourceManager.getResource(location).orElse(null)
            
            if (resource == null) {
                Melinoe.logger.error("Emoji texture not found (bypassing): $path")
                return@getOrPut -1
            }
            
            resource.open().use { stream ->
                val bytes = stream.readBytes()
                val buffer = memAlloc(bytes.size)
                try {
                    buffer.put(bytes)
                    buffer.flip()
                    
                    val w = IntArray(1)
                    val h = IntArray(1)
                    val channels = IntArray(1)
                    val imgBuffer = stbi_load_from_memory(buffer, w, h, channels, 4)
                    
                    if (imgBuffer != null) {
                        val id = nvgCreateImageRGBA(vg, w[0], h[0], 0, imgBuffer)
                        org.lwjgl.stb.STBImage.stbi_image_free(imgBuffer)
                        id
                    } else {
                        Melinoe.logger.error("Failed to decode emoji texture: $path")
                        -1
                    }
                } finally {
                    memFree(buffer)
                }
            }
        }
    }
    
    private fun drawEmoji(c: Char, x: Float, y: Float, size: Float) {
        val asset = EmojiData.emojiMap[c] ?: return
        val imgId = getEmojiTextureId(asset.path)
        if (imgId == -1) return
        
        val iw = size * asset.totalCols
        val ih = size
        val ix = x - (asset.col * size)
        
        nvgImagePattern(vg, ix, y, iw, ih, 0f, imgId, 1f, nvgPaint)
        nvgBeginPath(vg)
        nvgRect(vg, x, y, size, size)
        nvgFillPaint(vg, nvgPaint)
        nvgFill(vg)
    }

    fun drawWrappedString(
        text: String,
        x: Float,
        y: Float,
        w: Float,
        size: Float,
        color: Int,
        font: Font,
        lineHeight: Float = 1f
    ) {
        nvgFontSize(vg, size)
        nvgFontFaceId(vg, getFontID(font))
        nvgTextLineHeight(vg, lineHeight)
        color(color)
        nvgFillColor(vg, nvgColor)
        nvgTextBox(vg, x, y, w, text)
    }

    fun wrappedTextBounds(
        text: String,
        w: Float,
        size: Float,
        font: Font,
        lineHeight: Float = 1f
    ): FloatArray {
        val bounds = FloatArray(4)
        nvgFontSize(vg, size)
        nvgFontFaceId(vg, getFontID(font))
        nvgTextLineHeight(vg, lineHeight)
        nvgTextBoxBounds(vg, 0f, 0f, w, text, bounds)
        return bounds // [minX, minY, maxX, maxY]
    }

    fun createNVGImage(textureId: Int, textureWidth: Int, textureHeight: Int): Int =
        nvglCreateImageFromHandle(vg, textureId, textureWidth, textureHeight, NVG_IMAGE_NEAREST or NVG_IMAGE_NODELETE)

    fun image(image: Int, textureWidth: Int, textureHeight: Int, subX: Int, subY: Int, subW: Int, subH: Int, x: Float, y: Float, w: Float, h: Float, radius: Float) {
        if (image == -1) return

        val sx = subX.toFloat() / textureWidth
        val sy = subY.toFloat() / textureHeight
        val sw = subW.toFloat() / textureWidth
        val sh = subH.toFloat() / textureHeight

        val iw = w / sw
        val ih = h / sh
        val ix = x - iw * sx
        val iy = y - ih * sy

        nvgImagePattern(vg, ix, iy, iw, ih, 0f, image, 1f, nvgPaint)
        nvgBeginPath(vg)
        nvgRoundedRect(vg, x, y, w, h + .5f, radius)
        nvgFillPaint(vg, nvgPaint)
        nvgFill(vg)
    }

    fun image(image: Image, x: Float, y: Float, w: Float, h: Float, radius: Float) {
        nvgImagePattern(vg, x, y, w, h, 0f, getImage(image), 1f, nvgPaint)
        nvgBeginPath(vg)
        nvgRoundedRect(vg, x, y, w, h + .5f, radius)
        nvgFillPaint(vg, nvgPaint)
        nvgFill(vg)
    }

    fun image(image: Image, x: Float, y: Float, w: Float, h: Float) {
        nvgImagePattern(vg, x, y, w, h, 0f, getImage(image), 1f, nvgPaint)
        nvgBeginPath(vg)
        nvgRect(vg, x, y, w, h + .5f)
        nvgFillPaint(vg, nvgPaint)
        nvgFill(vg)
    }

    fun createImage(resourcePath: String): Image {
        val image = images.keys.find { it.identifier == resourcePath } ?: Image(resourcePath)
        if (image.isSVG) images.getOrPut(image) { NVGImage(0, loadSVG(image)) }.count++
        else images.getOrPut(image) { NVGImage(0, loadImage(image)) }.count++
        return image
    }

    // lowers reference count by 1, if it reaches 0 it gets deleted from mem
    fun deleteImage(image: Image) {
        val nvgImage = images[image] ?: return
        nvgImage.count--
        if (nvgImage.count == 0) {
            nvgDeleteImage(vg, nvgImage.nvg)
            images.remove(image)
        }
    }

    private fun getImage(image: Image): Int {
        return images[image]?.nvg ?: throw IllegalStateException("Image (${image.identifier}) doesn't exist")
    }

    private fun loadImage(image: Image): Int {
        val w = IntArray(1)
        val h = IntArray(1)
        val channels = IntArray(1)
        val buffer = stbi_load_from_memory(
            image.buffer(),
            w,
            h,
            channels,
            4
        ) ?: throw NullPointerException("Failed to load image: ${image.identifier}")
        return nvgCreateImageRGBA(vg, w[0], h[0], 0, buffer)
    }

    private fun loadSVG(image: Image): Int {
        val vec = image.stream.use { it.bufferedReader().readText() }
        val svg = nsvgParse(vec, "px", 96f) ?: throw IllegalStateException("Failed to parse ${image.identifier}")

        val width = svg.width().toInt()
        val height = svg.height().toInt()
        val buffer = memAlloc(width * height * 4)

        try {
            val rasterizer = nsvgCreateRasterizer()
            nsvgRasterize(rasterizer, svg, 0f, 0f, 1f, buffer, width, height, width * 4)
            val nvgImage = nvgCreateImageRGBA(vg, width, height, 0, buffer)
            nsvgDeleteRasterizer(rasterizer)
            return nvgImage
        } finally {
            nsvgDelete(svg)
            memFree(buffer)
        }
    }

    private fun color(color: Int) {
        nvgRGBA(color.red.toByte(), color.green.toByte(), color.blue.toByte(), color.alpha.toByte(), nvgColor)
    }

    private fun color(color1: Int, color2: Int) {
        nvgRGBA(color1.red.toByte(), color1.green.toByte(), color1.blue.toByte(), color1.alpha.toByte(), nvgColor)
        nvgRGBA(color2.red.toByte(), color2.green.toByte(), color2.blue.toByte(), color2.alpha.toByte(), nvgColor2)
    }

    private fun gradient(color1: Int, color2: Int, x: Float, y: Float, w: Float, h: Float, direction: Gradient) {
        color(color1, color2)
        when (direction) {
            Gradient.LeftToRight -> nvgLinearGradient(vg, x, y, x + w, y, nvgColor, nvgColor2, nvgPaint)
            Gradient.TopToBottom -> nvgLinearGradient(vg, x, y, x, y + h, nvgColor, nvgColor2, nvgPaint)
        }
    }

    private fun getFontID(font: Font): Int {
        return fontMap.getOrPut(font) {
            val buffer = font.buffer()
            NVGFont(nvgCreateFontMem(vg, font.name, buffer, false), buffer)
        }.id
    }

    private class Scissor(val previous: Scissor?, val x: Float, val y: Float, val maxX: Float, val maxY: Float) {
        fun applyScissor() {
            if (previous == null) nvgScissor(vg, x, y, maxX - x, maxY - y)
            else {
                val x = max(x, previous.x)
                val y = max(y, previous.y)
                val width = max(0f, (min(maxX, previous.maxX) - x))
                val height = max(0f, (min(maxY, previous.maxY) - y))
                nvgScissor(vg, x, y, width, height)
            }
        }
    }

    private data class NVGImage(var count: Int, val nvg: Int)
    private data class NVGFont(val id: Int, val buffer: ByteBuffer)
}

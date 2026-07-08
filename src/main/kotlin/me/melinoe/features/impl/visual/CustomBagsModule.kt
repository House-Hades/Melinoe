package me.melinoe.features.impl.visual

import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.ActionSetting
import me.melinoe.clickgui.settings.impl.NumberSetting
import me.melinoe.clickgui.settings.impl.SelectorSetting
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.Message
import me.melinoe.utils.TelosItemUtils
import me.melinoe.utils.render.CustomBagImages
import me.melinoe.utils.render.CustomBagSounds
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Custom Bags Module - replace the image and drop sound of a loot bag with a custom one
 */
object CustomBagsModule : Module(
    name = "Custom Bags",
    category = Category.VISUAL,
    description = "Replace the image and drop sound of loot bags with your own"
) {

    // Display label shown in the selector -> internal key, sourced from CustomBagImages
    private val labelToKey: Map<String, String> = CustomBagImages.typeLabels.toMap()

    private val bagType = +SelectorSetting(
        name = "Bag",
        default = "Bloodshot",
        options = labelToKey.keys.toList(),
        desc = "Which bag's image/sound you're setting"
    )

    private val selectedKey: String
        get() = labelToKey[bagType.selected] ?: error("No bag key for selector label '${bagType.selected}'")

    // One volume slider per bag type
    private val volumeSettings: Map<String, NumberSetting<Int>> = CustomBagImages.typeLabels.associate { (label, key) ->
        key to +NumberSetting("$label Volume", 100, min = 0, max = 100, desc = "Volume of the $label bag sound", unit = "%")
            .withDependency { selectedKey == key }
    }

    /** Custom-sound volume for [friendly] as a 0..1 gain, applied when the sound starts */
    fun soundVolumeFor(friendly: String): Float = (volumeSettings[friendly]?.value ?: 100) / 100f

    // How long the config stays hidden during a preview
    private const val PREVIEW_REOPEN_MS = 2000L

    /**
     * True only while [preview] is driving a totem animation, so the drop-detection mixin can skip
     * incrementing lifetime stats for a fake preview pop
     */
    @Volatile
    var previewing: Boolean = false
        private set

    init {
        +ActionSetting(
            "Set Image…",
            "Pick a PNG, JPG, or GIF"
        ) { setImage() }

        +ActionSetting(
            "Clear Image",
            "Remove the custom image for the selected bag"
        ) { CustomBagImages.clearImage(selectedKey) }

        +ActionSetting(
            "Set Sound…",
            "Pick an MP3 or OGG"
        ) { setSound() }

        +ActionSetting(
            "Clear Sound",
            "Remove the custom drop sound for the selected bag"
        ) { CustomBagSounds.clearSound(selectedKey) }

        +ActionSetting(
            "Preview",
            "Play the selected bag's drop animation and sound",
            label = { if (CustomBagImages.isProcessing(selectedKey)) "Processing" else "Preview" },
            enabled = { !CustomBagImages.isProcessing(selectedKey) }
        ) { preview() }

        // Pre-decode any configured GIFs at startup so drops/previews never freeze or delay on first use
        CustomBagImages.warmAll()
    }

    private fun setImage() {
        val key = selectedKey
        val label = bagType.selected
        pickFileAsync("Select image for the $label bag", listOf("*.png", "*.jpg", "*.jpeg", "*.gif"), "Images (PNG, JPG, GIF)") { path ->
            if (!CustomBagImages.saveImageFrom(key, path)) {
                Message.error("Couldn't use that image; pick a PNG, JPG, or GIF up to 15MB")
            }
        }
    }

    private fun setSound() {
        val key = selectedKey
        val label = bagType.selected
        pickFileAsync("Select drop sound for the $label bag", listOf("*.mp3", "*.ogg"), "Audio (MP3, OGG)") { path ->
            if (!CustomBagSounds.saveSoundFrom(key, path)) {
                Message.error("Couldn't use that sound; pick an MP3 or OGG up to 15MB")
            }
        }
    }

    /**
     * Open a native file picker restricted to [patterns] off the render thread
     */
    private fun pickFileAsync(title: String, patterns: List<String>, desc: String, onChosen: (Path) -> Unit) {
        Thread({
            val chosen = MemoryStack.stackPush().use { stack ->
                val filters = stack.mallocPointer(patterns.size)
                patterns.forEach { filters.put(stack.UTF8(it)) }
                filters.flip()
                TinyFileDialogs.tinyfd_openFileDialog(title, "", filters, desc, false)
            } ?: return@Thread
            onChosen(Paths.get(chosen))
        }, "melinoe-totem-file-picker").apply { isDaemon = true }.start()
    }

    /** Trigger the vanilla totem pop for the selected bag so the image can be checked in-game */
    private fun preview() {
        if (mc.player == null) {
            Message.error("Join a world first")
            return
        }
        val id = CustomBagImages.telosIdentifierFor(selectedKey) ?: run {
            Message.error("No preview available for ${bagType.selected}")
            return
        }
        val stack = TelosItemUtils.createItemStack(id)
        // The Preview button is clicked from the config screen
        val returnScreen = mc.screen
        mc.execute {
            mc.setScreen(null)
            // The mixin's stat handlers fire synchronously inside displayItemActivation
            previewing = true
            try {
                mc.gameRenderer.displayItemActivation(stack)
            } finally {
                previewing = false
            }
            playDropSound(selectedKey)
        }
        // Reopen the config after the pop ends, unless the user opened something else in the meantime.
        if (returnScreen != null) {
            Thread({
                try { Thread.sleep(PREVIEW_REOPEN_MS) } catch (_: InterruptedException) { return@Thread }
                mc.execute { if (mc.screen == null) mc.setScreen(returnScreen) }
            }, "melinoe-preview-reopen").apply { isDaemon = true }.start()
        }
    }

    /**
     * Play the create sound a real drop makes as the totem appears; the custom sound if one is set,
     * otherwise the default Telos create sound
     */
    private fun playDropSound(friendly: String) {
        if (CustomBagSounds.hasSound(friendly)) {
            CustomBagSounds.playCreate(friendly, soundVolumeFor(friendly))
            return
        }
        val suffix = CustomBagImages.soundSuffixFor(friendly) ?: friendly
        val event = SoundEvent.createVariableRangeEvent(Identifier.parse("noise:player.bags.create_$suffix"))
        mc.soundManager.playDelayed(SimpleSoundInstance.forUI(event, 1f, 1f), 0)
    }
}

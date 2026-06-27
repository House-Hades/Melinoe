package me.melinoe.features.impl.combat

import me.melinoe.Melinoe
import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.*
import me.melinoe.events.*
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.Color
import me.melinoe.utils.ItemUtils
import me.melinoe.utils.createSoundSettings
import me.melinoe.utils.emoji.EmojiReplacer
import me.melinoe.utils.playSoundSettings
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import kotlin.math.ceil

/**
 * Armor Cooldowns Module.
 *
 * Tracks armor ability cooldowns via the vanilla item-cooldown indicator
 */
object ArmorCooldownsModule : Module(
    name = "Armor Cooldowns",
    category = Category.COMBAT,
    description = "Tracks armor ability cooldowns with HUD bars, per-slot titles and sounds."
) {

    val ARMOR_SLOTS = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

    // Bar visuals
    const val BAR_HEIGHT = 5
    private const val BAR_BG_COLOR = 0xC0404040.toInt()
    private const val BAR_BORDER_COLOR = 0xFF252326.toInt()
    private const val BAR_READY_COLOR = 0xFF02963D.toInt()

    private const val RED_R = 139f
    private const val RED_G = 0f
    private const val RED_B = 0f
    private const val ORG_R = 250f
    private const val ORG_G = 129f
    private const val ORG_B = 42f

    private const val DEFAULT_COOLDOWN_MS = 1000L

    val showBars = registerSetting(BooleanSetting("Show Cooldown Bars", true, desc = "Draw a cooldown bar under each ability piece on the Armor HUD."))

    // Shared title popup position
    private val titleHud by HUDSetting(
        name = "Title Display",
        x = 400,
        y = 200,
        scale = 2f,
        toggleable = true,
        default = true,
        description = "Position of the armor ready title popup. Toggle to show/hide.",
        module = this
    ) { example ->
        val currentTitle = if (example) buildStyledTitleText("Armor Ready!", 0xFF7CFFB2.toInt()) else customTitle
        if (currentTitle == null) return@HUDSetting 0 to 0

        val color = if (example) 0xFF7CFFB2.toInt() else customTitleColor
        val textRenderer = mc.font
        val textWidth = textRenderer.width(currentTitle)
        val textHeight = textRenderer.lineHeight

        text(textRenderer, currentTitle, 0, 0, color, true)

        textWidth to textHeight
    }

    // Per-slot notification configuration.
    private class SlotConfig(
        val slot: EquipmentSlot,
        val titleEnabled: BooleanSetting,
        val titleText: StringSetting,
        val titleColor: ColorSetting,
        val duration: NumberSetting<Float>,
        val playSound: BooleanSetting,
        val sound: () -> Triple<String, Float, Float>
    )

    private val slotConfigs = LinkedHashMap<EquipmentSlot, SlotConfig>()

    // A tracked, currently-on-cooldown armor piece.
    private class Entry(
        val key: String,
        val slot: EquipmentSlot,
        var stack: ItemStack,
        var endMs: Long,
        val isNature: Boolean,
        val totalMs: Long
    )
    
    private val entries = LinkedHashMap<String, Entry>()

    // Active title popup state
    private var customTitle: Component? = null
    private var customTitleColor: Int = 0xFFFFFFFF.toInt()
    private var titleDisplayTicks = 0

    init {
        // Build per-slot settings
        for ((slot, label, defaultTitle) in listOf(
            Triple(EquipmentSlot.HEAD, "Helmet", "Helmet Ready!"),
            Triple(EquipmentSlot.CHEST, "Chestplate", "Chestplate Ready!"),
            Triple(EquipmentSlot.LEGS, "Leggings", "Leggings Ready!"),
            Triple(EquipmentSlot.FEET, "Boots", "Boots Ready!")
        )) {
            val dropdown = +DropdownSetting("$label Settings", false)
            val titleEnabled = +BooleanSetting("$label Title", true, desc = "Show a title popup when the $label ability is ready.").withDependency { dropdown.value }
            val titleText = +StringSetting("$label Title Text", defaultTitle, desc = "Text shown when the $label ability is ready.", length = 64).withDependency { dropdown.value && titleEnabled.value }
            val titleColor = +ColorSetting("$label Title Color", Color(0xFF7CFFB2.toInt()), desc = "Color of the $label title text.").withDependency { dropdown.value && titleEnabled.value }
            val duration = +NumberSetting("$label Duration", 60.0f, 10.0f, 100.0f, desc = "Duration of the $label title in ticks.").withDependency { dropdown.value && titleEnabled.value }
            val playSound = +BooleanSetting("$label Play Sound", true, desc = "Play a sound when the $label ability is ready.").withDependency { dropdown.value }
            val sound = createSoundSettings(
                name = "$label Sound",
                default = "entity.player.levelup",
                dependencies = { dropdown.value && playSound.value },
                buttonName = "$label Test Sound"
            )

            slotConfigs[slot] = SlotConfig(slot, titleEnabled, titleText, titleColor, duration, playSound, sound)
        }

        on<TickEvent.End> {
            if (!enabled) return@on
            val player = mc.player ?: return@on
            val now = System.currentTimeMillis()
            
            val candidates = ArrayList<Pair<ItemStack, EquipmentSlot>>()
            val wornKeys = HashSet<String>()
            for (slot in ARMOR_SLOTS) {
                val stack = player.getItemBySlot(slot)
                if (!ItemUtils.hasArmorAbility(stack)) continue
                wornKeys.add(keyOf(stack))
                candidates.add(stack to slot)
            }
            for (stack in player.inventory.nonEquipmentItems) {
                if (!ItemUtils.hasArmorAbility(stack)) continue
                val slot = slotOf(stack) ?: continue
                candidates.add(stack to slot)
            }

            // Discover/refresh cooldowns across all gathered pieces
            val seenKeys = HashSet<String>()
            for ((stack, slot) in candidates) {
                val key = keyOf(stack)
                if (!seenKeys.add(key)) continue

                val percent = player.cooldowns.getCooldownPercent(stack, 0f)
                val existing = entries[key]
                if (existing != null) {
                    existing.stack = stack
                    if (percent > 0f) existing.endMs = now + (percent * existing.totalMs).toLong()
                } else if (percent > 0f) {
                    val isNature = ItemUtils.ItemType.fromItemStack(stack) == ItemUtils.ItemType.UT_NATURE
                    val total = totalMs(stack)
                    entries[key] = Entry(key, slot, stack.copy(), now + (percent * total).toLong(), isNature, total)
                }
            }

            // Re-sync any tracked
            for (entry in entries.values) {
                if (entry.key in seenKeys) continue
                val percent = player.cooldowns.getCooldownPercent(entry.stack, 0f)
                if (percent > 0f) entry.endMs = now + (percent * entry.totalMs).toLong()
            }

            // Fire ready notifications and drop finished cooldowns
            val iterator = entries.values.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now >= entry.endMs) {
                    iterator.remove()
                    fireReady(entry.slot)
                }
            }

            // Title popup countdown
            if (titleDisplayTicks > 0) {
                titleDisplayTicks--
                if (titleDisplayTicks <= 0) customTitle = null
            }
        }

        // World switch: cooldowns persist
        on<WorldLoadEvent> {
            entries.values.removeIf { it.isNature }
        }

        // Realm switch: the server resets all cooldowns
        on<HubToRealmEvent> { reset() }
        on<RealmToHubEvent> { reset() }
        on<RealmToRealmEvent> { reset() }
    }
    
    // Resolves the armor slot an ability piece belongs to via its Equippable component, or null if it isn't armor
    private fun slotOf(stack: ItemStack): EquipmentSlot? {
        val slot = stack.get(DataComponents.EQUIPPABLE)?.slot() ?: return null
        return if (slot in ARMOR_SLOTS) slot else null
    }

    // Stable identity for an ability piece
    private fun keyOf(stack: ItemStack): String {
        val model = stack.get(DataComponents.ITEM_MODEL)?.toString() ?: ""
        val name = ItemUtils.getPlainName(stack)
        return "$model|$name|${stack.item}"
    }

    // Total cooldown duration in milliseconds, parsed from lore
    private fun totalMs(stack: ItemStack): Long {
        val seconds = ItemUtils.parseItemCooldown(stack)
        return if (seconds > 0f) (seconds * 1000f).toLong() else DEFAULT_COOLDOWN_MS
    }

    private fun fireReady(slot: EquipmentSlot) {
        val config = slotConfigs[slot] ?: return

        if (titleHud.enabled && config.titleEnabled.value) {
            customTitleColor = config.titleColor.value.rgba
            customTitle = buildStyledTitleText(config.titleText.value, customTitleColor)
            titleDisplayTicks = config.duration.value.toInt()
        }

        if (config.playSound.value) {
            try {
                playSoundSettings(config.sound())
            } catch (e: Exception) {
                Melinoe.logger.warn("Armor Cooldowns: Failed to play sound: ${e.message}")
            }
        }
    }

    private fun buildStyledTitleText(text: String, textColor: Int): Component {
        if (text.isEmpty()) return Component.literal("")
        return EmojiReplacer.replaceIn(Component.literal(text).withStyle(Style.EMPTY.withColor(textColor)))
    }

    // Clears all tracked cooldowns and any active popup
    fun reset() {
        entries.clear()
        customTitle = null
        titleDisplayTicks = 0
    }
    
    // Whether an armor piece has a trackable ability cooldown
    fun hasAbility(stack: ItemStack): Boolean = ItemUtils.hasArmorAbility(stack)

    // Remaining cooldown progress (0..1) for a stack
    fun progressFor(stack: ItemStack): Float {
        if (stack.isEmpty) return 0f
        val entry = entries[keyOf(stack)] ?: return 0f
        val remaining = entry.endMs - System.currentTimeMillis()
        if (remaining <= 0L) return 0f
        val total = entry.totalMs.toFloat()
        return (remaining / total).coerceIn(0f, 1f)
    }

    // Tracked pieces in [slot] that are still on cooldown but are not the piece currently worn
    fun extraPieces(slot: EquipmentSlot): List<ItemStack> {
        if (entries.isEmpty()) return emptyList()
        val player = mc.player ?: return emptyList()
        val worn = player.getItemBySlot(slot)
        val wornKey = if (ItemUtils.hasArmorAbility(worn)) keyOf(worn) else null
        // Single pass, allocate the result list only if something actually matches
        var result: MutableList<ItemStack>? = null
        for (entry in entries.values) {
            if (entry.slot == slot && entry.key != wornKey) {
                (result ?: ArrayList<ItemStack>(2).also { result = it }).add(entry.stack)
            }
        }
        return result ?: emptyList()
    }

    // Draws a horizontal cooldown bar
    fun renderArmorBar(context: GuiGraphicsExtractor, x: Int, y: Int, width: Int, progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        val x2 = x + width
        val y2 = y + BAR_HEIGHT

        // Border
        context.fill(x, y, x2, y + 1, BAR_BORDER_COLOR)
        context.fill(x, y2 - 1, x2, y2, BAR_BORDER_COLOR)
        context.fill(x, y, x + 1, y2, BAR_BORDER_COLOR)
        context.fill(x2 - 1, y, x2, y2, BAR_BORDER_COLOR)

        val innerX1 = x + 1
        val innerY1 = y + 1
        val innerX2 = x2 - 1
        val innerY2 = y2 - 1

        // Background
        context.fill(innerX1, innerY1, innerX2, innerY2, BAR_BG_COLOR)

        if (clamped > 0f) {
            val innerWidth = innerX2 - innerX1
            val emptySpace = ceil(innerWidth * clamped).toInt()
            val fillX = innerX1 + emptySpace
            if (fillX < innerX2) {
                context.fill(fillX, innerY1, innerX2, innerY2, getCooldownColor(clamped))
            }
        } else {
            context.fill(innerX1, innerY1, innerX2, innerY2, BAR_READY_COLOR)
        }
    }

    private fun getCooldownColor(progress: Float): Int {
        val r = (ORG_R + (RED_R - ORG_R) * progress).toInt()
        val g = (ORG_G + (RED_G - ORG_G) * progress).toInt()
        val b = (ORG_B + (RED_B - ORG_B) * progress).toInt()
        return (255 shl 24) or (r shl 16) or (g shl 8) or b
    }

    override fun onDisable() {
        super.onDisable()
        reset()
    }
}
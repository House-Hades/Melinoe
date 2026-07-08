package me.melinoe.features.impl.visual

import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.KeybindSetting
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.features.impl.ClickGUIModule
import me.melinoe.utils.Message
import net.minecraft.client.resources.model.EquipmentAssetManager
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.equipment.EquipmentAsset
import net.minecraft.world.item.equipment.EquipmentAssets
import net.minecraft.world.item.equipment.Equippable
import org.lwjgl.glfw.GLFW
import java.util.*

/**
 * Hide Armor module - visually hides all equipped armor on the player.
 */
object HideArmorModule : Module(
    name = "Hide Armor",
    category = Category.VISUAL,
    description = "Visually hides all equipped armor on the player"
) {
    val hideOthers by BooleanSetting("Hide for Others", true, "Hide armor pieces for other players")
    val hideHelmet by BooleanSetting("Hide Helmet", true, "Hide helmet armor piece")
    val hideChestplate by BooleanSetting("Hide Chestplate", true, "Hide chestplate armor piece")
    val hideLeggings by BooleanSetting("Hide Leggings", true, "Hide leggings armor piece")
    val hideBoots by BooleanSetting("Hide Boots", true, "Hide boots armor piece")

    /**
     * Whether armor is actually being hidden right now. Gated independently of the module so the
     * "Toggle Hiding" keybind can stop/resume hiding without disabling the module (which would also
     * kill the reveal keybind)
     */
    @JvmStatic
    var hiding = true
        private set

    /** How long the "true items" reveal stays active after pressing the keybind */
    private const val REVEAL_DURATION_MS = 3000L

    /** Timestamp until which the true-item reveal is active */
    private var revealUntil = 0L

    @Suppress("unused")
    private val toggleHideKey by KeybindSetting(
        "Toggle Hiding",
        GLFW.GLFW_KEY_UNKNOWN,
        "Toggle armor hiding on and off."
    ).onPress { toggleHiding() }

    @Suppress("unused")
    private val revealKey by KeybindSetting(
        "Show True Items",
        GLFW.GLFW_KEY_UNKNOWN,
        "Briefly reveal players' real (unskinned) item models for 3s."
    ).onPress { revealTrueItems() }

    /** Starts the true-item reveal window */
    private fun revealTrueItems() {
        revealUntil = System.currentTimeMillis() + REVEAL_DURATION_MS
        if (ClickGUIModule.enableNotification) {
            Message.success("Showing true items for 3s")
        }
    }

    /** Flips whether armor is hidden, leaving the module itself enabled */
    private fun toggleHiding() {
        hiding = !hiding
        if (ClickGUIModule.enableNotification) {
            if (hiding) Message.success("Armor hidden") else Message.error("Armor visible")
        }
    }

    /** Whether the true-item reveal is currently active */
    @JvmStatic
    fun isRevealActive(): Boolean = enabled && System.currentTimeMillis() < revealUntil

    /**
     * Called for every item the game is about to resolve a model for (head item, held items,
     * While the reveal is active, items with a Telos cosmetic skin are rendered with their
     * real model instead
     */
    @JvmStatic
    fun resolveRenderStack(stack: ItemStack): ItemStack {
        if (!isRevealActive() || stack.isEmpty) return stack

        val itemType = telosItemType(stack) ?: return stack
        val trueModel = Identifier.fromNamespaceAndPath(itemType.namespace, "material/${itemType.path}")

        // Not skinned (already showing its real model)
        if (stack.get(DataComponents.ITEM_MODEL) == trueModel) return stack

        return stack.copy().apply { set(DataComponents.ITEM_MODEL, trueModel) }
    }

    /**
     * Worn 3D armor renders from the `equippable` component's equipment asset, not the item
     * model. A skin swaps that asset or strips it entirely. While revealing, point the asset
     * back at the real one so the true armor renders in 3D — including helmets whose asset the skin
     * removed
     */
    @JvmStatic
    fun resolveWornStack(stack: ItemStack): ItemStack {
        if (!isRevealActive() || stack.isEmpty) return stack

        val equippable = stack.get(DataComponents.EQUIPPABLE) ?: return stack
        val trueKey = trueWornAsset(stack) ?: return stack

        // Already wearing its real armor
        if (equippable.assetId().orElse(null) == trueKey) return stack

        val swapped = Equippable(
            equippable.slot(), equippable.equipSound(), Optional.of(trueKey),
            equippable.cameraOverlay(), equippable.allowedEntities(), equippable.dispensable(),
            equippable.swappable(), equippable.damageOnHurt(), equippable.equipOnInteract(),
            equippable.canBeSheared(), equippable.shearingSound()
        )
        return stack.copy().apply { set(DataComponents.EQUIPPABLE, swapped) }
    }

    /**
     * True while revealing if [stack] is a skinned piece that will now render as worn 3D armor.
     */
    @JvmStatic
    fun revealsAsWorn(stack: ItemStack): Boolean =
        isRevealActive() && !stack.isEmpty && trueWornAsset(stack) != null

    /**
     * The real equipment asset for [stack], derived from `realm:item_type`
     * Null if it isn't armour
     */
    private fun trueWornAsset(stack: ItemStack): ResourceKey<EquipmentAsset>? {
        val itemType = telosItemType(stack) ?: return null
        val parts = itemType.path.split('/')
        if (parts.size < 4 || parts[0] != "armour") return null

        val assetId = Identifier.fromNamespaceAndPath(itemType.namespace, "${parts[1]}/${parts.last()}")
        val key = ResourceKey.create(equipmentAssetRoot, assetId)
        return if (equipmentAssetExists(key)) key else null
    }

    /** Whether an equipment asset is actually loaded */
    private fun equipmentAssetExists(key: ResourceKey<EquipmentAsset>): Boolean {
        val manager = equipmentAssets ?: return false
        return manager.get(key) !== EquipmentAssetManager.MISSING
    }

    private val equipmentAssetRoot: ResourceKey<Registry<EquipmentAsset>>
        @Suppress("UNCHECKED_CAST")
        get() = EquipmentAssets.ROOT_ID as ResourceKey<Registry<EquipmentAsset>>

    /** The client's equipment-asset registry, captured at construction by the manager mixin. */
    @JvmStatic
    var equipmentAssets: EquipmentAssetManager? = null

    /**
     * True if [stack] is an actual armour piece (helmet/chestplate/leggings/boots)
     */
    fun isArmorPiece(stack: ItemStack): Boolean {
        val model = stack.get(DataComponents.ITEM_MODEL) ?: return false
        return model.path.startsWith("material/armour/")
    }

    /** Reads the real Telos item id from custom data */
    private fun telosItemType(stack: ItemStack): Identifier? {
        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        val itemTypeStr = customData.copyTag()
            .getCompoundOrEmpty("PublicBukkitValues")
            .getStringOr("realm:item_type", "")
        return if (itemTypeStr.isEmpty()) null else Identifier.tryParse(itemTypeStr)
    }
}

package me.melinoe.features.impl.misc

import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.NumberSetting
import me.melinoe.features.Category
import me.melinoe.features.Module

/**
 * Customizes the viewmodel (held items and hand rendering).
 */
object ViewModelModule : Module(
    name = "View Model",
    description = "Customize the position, scale, rotation and animations of your held items",
    category = Category.MISC
) {
    // Animation settings
    val noEquipAnimation by BooleanSetting("No Equip Animation", false, "Removes the item swapping animation.")
    val applyToHand by BooleanSetting("Apply To Hand", false, "Applies the viewmodel changes to the empty hand.")
    
    // Speed settings
    val speed by NumberSetting("Swing Speed", 0, 0, 50, 1, "Apply a custom swing speed. Set to 0 to disable.")
    
    // Position offsets
    private val positionDropdown = +me.melinoe.clickgui.settings.impl.DropdownSetting("Position", false)
    val offsetX by NumberSetting("Offset X", 0.0, -2.0, 2.0, 0.01, "The X axis offset position of your held item.").withDependency { positionDropdown.value }
    val offsetY by NumberSetting("Offset Y", 0.0, -2.0, 2.0, 0.01, "The Y axis offset position of your held item.").withDependency { positionDropdown.value }
    val offsetZ by NumberSetting("Offset Z", 0.0, -2.0, 2.0, 0.01, "The Z axis offset position of your held item.").withDependency { positionDropdown.value }
    
    // Scale settings
    private val scaleDropdown = +me.melinoe.clickgui.settings.impl.DropdownSetting("Scale", false)
    val scaleX by NumberSetting("Scale X", 1.0, 0.1, 3.0, 0.01, "The X axis scale of your held item.").withDependency { scaleDropdown.value }
    val scaleY by NumberSetting("Scale Y", 1.0, 0.1, 3.0, 0.01, "The Y axis scale of your held item.").withDependency { scaleDropdown.value }
    val scaleZ by NumberSetting("Scale Z", 1.0, 0.1, 3.0, 0.01, "The Z axis scale of your held item.").withDependency { scaleDropdown.value }
    
    // Rotation settings
    private val rotationDropdown = +me.melinoe.clickgui.settings.impl.DropdownSetting("Rotation", false)
    val rotX by NumberSetting("Rotation X", 0.0, -180.0, 180.0, 0.5, "The X axis rotation of your held item.").withDependency { rotationDropdown.value }
    val rotY by NumberSetting("Rotation Y", 0.0, -180.0, 180.0, 0.5, "The Y axis rotation of your held item.").withDependency { rotationDropdown.value }
    val rotZ by NumberSetting("Rotation Z", 0.0, -180.0, 180.0, 0.5, "The Z axis rotation of your held item.").withDependency { rotationDropdown.value }
    
    // Swing animation settings
    private val swingDropdown = +me.melinoe.clickgui.settings.impl.DropdownSetting("Swing Animation", false)
    val swingX by NumberSetting("Swing X", 1.0, 0.0, 2.0, 0.01, "The X multiplier for swing animation offset.").withDependency { swingDropdown.value }
    val swingY by NumberSetting("Swing Y", 1.0, 0.0, 2.0, 0.01, "The Y multiplier for swing animation offset.").withDependency { swingDropdown.value }
    val swingZ by NumberSetting("Swing Z", 1.0, 0.0, 2.0, 0.01, "The Z multiplier for swing animation offset.").withDependency { swingDropdown.value }
}

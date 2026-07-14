package me.melinoe.mixin.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import me.melinoe.features.impl.misc.TooltipScaleModule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
    @Shadow
    @Final
    private Matrix3x2fStack pose;

    @Inject(
        method = "tooltip",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/TooltipRenderUtil;extractTooltipBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIILnet/minecraft/resources/Identifier;)V"
        )
    )
    private void applyTooltipScale(
        Font font,
        List<ClientTooltipComponent> lines,
        int xo,
        int yo,
        ClientTooltipPositioner positioner,
        @Nullable Identifier style,
        CallbackInfo ci,
        @Local(name = "textWidth") int textWidth,
        @Local(name = "tempHeight") int tempHeight
    ) {
        if (!TooltipScaleModule.INSTANCE.getEnabled()) return;

        float scale = (float) TooltipScaleModule.INSTANCE.getScale();
        
        this.pose.translate(xo - xo * scale, yo - yo * scale);
        this.pose.scale(scale, scale);
    }
}

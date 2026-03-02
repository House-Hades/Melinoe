package me.melinoe.mixin.accessors;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin for CompositeRenderType to get the render pipeline.
 * Used for Iris shader compatibility.
 */
@Mixin(RenderType.CompositeRenderType.class)
public interface CompositeRenderTypeAccessor {
    @Accessor("renderPipeline")
    RenderPipeline getRenderPipeline();
}

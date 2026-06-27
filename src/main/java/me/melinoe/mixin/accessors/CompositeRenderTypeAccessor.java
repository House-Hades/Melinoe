package me.melinoe.mixin.accessors;

import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin for CompositeRenderType to get the render pipeline.
 * Used for Iris shader compatibility.
 */
@Mixin(CompositeRenderer.class)
public interface CompositeRenderTypeAccessor {
    @Accessor("pipeline")
    WorldRenderingPipeline getRenderPipeline();
}

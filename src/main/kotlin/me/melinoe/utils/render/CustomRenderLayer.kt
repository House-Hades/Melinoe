package me.melinoe.utils.render

import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType

/**
 * Custom render layers for world rendering.
 */
object CustomRenderLayer {
    
    val LINE_LIST: RenderType = RenderType.create(
        "line-list",
        RenderSetup.builder(CustomRenderPipelines.LINE_LIST)
            .bufferSize(RenderType.BIG_BUFFER_SIZE)
            .sortOnUpload()
            .createRenderSetup()
    )
    
    val LINE_LIST_ESP: RenderType = RenderType.create(
        "line-list-esp",
        RenderSetup.builder(CustomRenderPipelines.LINE_LIST_ESP)
            .bufferSize(RenderType.BIG_BUFFER_SIZE)
            .sortOnUpload()
            .createRenderSetup()
    )
    
    val TRIANGLE_STRIP: RenderType = RenderType.create(
        "triangle_strip",
        RenderSetup.builder(CustomRenderPipelines.TRIANGLE_STRIP)
            .bufferSize(RenderType.BIG_BUFFER_SIZE)
            .sortOnUpload()
            .createRenderSetup()
    )
    
    val TRIANGLE_STRIP_ESP: RenderType = RenderType.create(
        "triangle_strip_esp",
        RenderSetup.builder(CustomRenderPipelines.TRIANGLE_STRIP_ESP)
            .bufferSize(RenderType.BIG_BUFFER_SIZE)
            .sortOnUpload()
            .createRenderSetup()
    )
    
    val QUADS: RenderType = RenderType.create(
        "quads",
        RenderSetup.builder(CustomRenderPipelines.QUADS)
            .bufferSize(RenderType.BIG_BUFFER_SIZE)
            .createRenderSetup()
    )
}
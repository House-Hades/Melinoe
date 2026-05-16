package me.melinoe.utils.render

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import java.util.Optional

/**
 * Custom render pipelines for world rendering.
 */
object CustomRenderPipelines {
    
    val LINE_LIST: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("melinoe", "pipeline/lines"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES)
            .withCull(false)
            .withColorTargetState(ColorTargetState(
                Optional.of(BlendFunction.TRANSLUCENT),
                ColorTargetState.WRITE_RED or ColorTargetState.WRITE_GREEN or ColorTargetState.WRITE_BLUE or ColorTargetState.WRITE_ALPHA
            ))
            .withDepthStencilState(DepthStencilState(
                CompareOp.LESS_THAN_OR_EQUAL, // LEQUAL_DEPTH_TEST
                true, // Depth Write
                -1f, // Depth Bias scale (brings it slightly forward like previous ViewOffsetZ)
                -1f  // Depth Bias constant
            ))
            .build()
    )
    
    val LINE_LIST_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("melinoe", "pipeline/lines_esp"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES)
            .withCull(false)
            .withColorTargetState(ColorTargetState(
                Optional.of(BlendFunction.TRANSLUCENT),
                ColorTargetState.WRITE_RED or ColorTargetState.WRITE_GREEN or ColorTargetState.WRITE_BLUE or ColorTargetState.WRITE_ALPHA
            ))
            .withDepthStencilState(DepthStencilState(
                CompareOp.ALWAYS_PASS, // NO_DEPTH_TEST
                false, // No Depth Write
                0f,
                0f
            ))
            .build()
    )
    
    val TRIANGLE_STRIP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("melinoe", "pipeline/debug_filled_box"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
            .withCull(false)
            .withColorTargetState(ColorTargetState(
                Optional.of(BlendFunction.TRANSLUCENT),
                ColorTargetState.WRITE_RED or ColorTargetState.WRITE_GREEN or ColorTargetState.WRITE_BLUE or ColorTargetState.WRITE_ALPHA
            ))
            .withDepthStencilState(DepthStencilState(
                CompareOp.LESS_THAN_OR_EQUAL,
                true,
                -1f,
                -1f
            ))
            .build()
    )
    
    val TRIANGLE_STRIP_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("melinoe", "pipeline/debug_filled_box_esp"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
            .withCull(false)
            .withColorTargetState(ColorTargetState(
                Optional.of(BlendFunction.TRANSLUCENT),
                ColorTargetState.WRITE_RED or ColorTargetState.WRITE_GREEN or ColorTargetState.WRITE_BLUE or ColorTargetState.WRITE_ALPHA
            ))
            .withDepthStencilState(DepthStencilState(
                CompareOp.ALWAYS_PASS,
                false,
                0f,
                0f
            ))
            .build()
    )
    
    val QUADS: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("melinoe", "pipeline/quads"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withCull(false)
            .withColorTargetState(ColorTargetState(
                Optional.of(BlendFunction.TRANSLUCENT),
                ColorTargetState.WRITE_RED or ColorTargetState.WRITE_GREEN or ColorTargetState.WRITE_BLUE or ColorTargetState.WRITE_ALPHA
            ))
            .withDepthStencilState(DepthStencilState(
                CompareOp.ALWAYS_PASS,
                false,
                0f,
                0f
            ))
            .build()
    )
}
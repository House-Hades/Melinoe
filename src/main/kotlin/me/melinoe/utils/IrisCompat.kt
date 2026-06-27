package me.melinoe.utils

import com.mojang.blaze3d.pipeline.RenderPipeline
import me.melinoe.utils.render.CustomRenderLayer
import net.fabricmc.loader.api.FabricLoader
import net.irisshaders.iris.api.v0.IrisApi
import net.irisshaders.iris.api.v0.IrisProgram

/**
 * Iris shader compatibility handler.
 * Registers custom render pipelines with Iris so shaders work correctly.
 */
interface IrisCompat {
    fun registerPipeline(pipeline: RenderPipeline, shaderType: IrisShaderType) {}
    fun registerRenderType(pipeline: RenderPipeline, shaderType: IrisShaderType) {
        registerPipeline(pipeline, shaderType)
    }
    
    companion object : IrisCompat by resolve() {
        init {
            registerRenderType(CustomRenderLayer.LINE_LIST.pipeline(), IrisShaderType.LINES)
            registerRenderType(CustomRenderLayer.LINE_LIST_ESP.pipeline(), IrisShaderType.LINES)
            registerRenderType(CustomRenderLayer.TRIANGLE_STRIP.pipeline(), IrisShaderType.BASIC)
            registerRenderType(CustomRenderLayer.TRIANGLE_STRIP_ESP.pipeline(), IrisShaderType.BASIC)
            registerRenderType(CustomRenderLayer.QUADS.pipeline(), IrisShaderType.BASIC)
        }
    }
}

enum class IrisShaderType {
    LINES,
    BASIC,
}

internal object IrisCompatImpl : IrisCompat {
    private val instance by lazy { IrisApi.getInstance() }
    
    override fun registerPipeline(pipeline: RenderPipeline, shaderType: IrisShaderType) {
        val type = when (shaderType) {
            IrisShaderType.BASIC -> IrisProgram.BASIC
            IrisShaderType.LINES -> IrisProgram.LINES
        }
        instance.assignPipeline(pipeline, type)
    }
}


internal object IrisCompatNoOp : IrisCompat

internal fun resolve(): IrisCompat = if (FabricLoader.getInstance().isModLoaded("iris")) IrisCompatImpl else IrisCompatNoOp

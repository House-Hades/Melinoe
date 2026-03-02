package me.melinoe.utils.ui.rendering

import org.lwjgl.nanovg.NanoVGGL3
import org.lwjgl.opengl.GL30C

/**
 * NVG Context Manager.
 */
object NVGContextManager {

    private var nvgContext: Long = 0
    private var initialized = false

    val context: Long
        get() {
            if (!initialized) init()
            return nvgContext
        }

    fun init() {
        if (initialized) return

        try {
            // Create NVG context
            nvgContext = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS or NanoVGGL3.NVG_STENCIL_STROKES)
            if (nvgContext == 0L) {
                throw RuntimeException("Failed to create NVG context")
            }

            // NVGRenderer initializes itself, no need to call init

            initialized = true
        } catch (e: Exception) {
            System.err.println("Failed to initialize NVG context: ${e.message}")
        }
    }

    fun beginFrame(width: Int, height: Int, pixelRatio: Float = 1.0f) {
        if (!initialized) {
            // Try to initialize now if not already done
            init()
            if (!initialized) return
        }

        try {
            // Setup OpenGL state for NVG
            GL30C.glEnable(GL30C.GL_BLEND)
            GL30C.glBlendFunc(GL30C.GL_SRC_ALPHA, GL30C.GL_ONE_MINUS_SRC_ALPHA)
            GL30C.glDisable(GL30C.GL_DEPTH_TEST)
            GL30C.glDisable(GL30C.GL_SCISSOR_TEST)
            GL30C.glDisable(GL30C.GL_CULL_FACE)

            // Begin NVG frame
            org.lwjgl.nanovg.NanoVG.nvgBeginFrame(nvgContext, width.toFloat(), height.toFloat(), pixelRatio)
        } catch (e: Exception) {
            System.err.println("Failed to begin NVG frame: ${e.message}")
        }
    }

    fun endFrame() {
        if (!initialized) return

        try {
            // End NVG frame
            org.lwjgl.nanovg.NanoVG.nvgEndFrame(nvgContext)

            // Restore OpenGL state
            GL30C.glEnable(GL30C.GL_DEPTH_TEST)
            GL30C.glEnable(GL30C.GL_CULL_FACE)
        } catch (e: Exception) {
            System.err.println("Failed to end NVG frame: ${e.message}")
        }
    }

    fun destroy() {
        if (!initialized) return

        // NVGRenderer manages its own context, no need to call destroy
        // Note: NanoVG doesn't have a nvgDelete function, context is managed by LWJGL
        nvgContext = 0
        initialized = false
    }
}
package me.melinoe.utils.render

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.PoseStack
import me.melinoe.Melinoe
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.item.TrackingItemStackRenderState
import net.minecraft.client.renderer.state.gui.BlitRenderState
import net.minecraft.client.renderer.state.gui.GuiRenderState
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Matrix3x2f
import java.util.Objects
import java.util.OptionalDouble

/**
 * Special GUI renderer for item state previews.
 */
class ItemStateRenderer(vertexConsumers: MultiBufferSource.BufferSource) :
    PictureInPictureRenderer<ItemStateRenderer.State>(vertexConsumers) {
    
    private var textureView: GpuTextureView? = null
    private var lastState: State? = null
    
    private val mc get() = Melinoe.mc
    
    override fun renderToTexture(state: State, poseStack: PoseStack) {
        textureView = RenderSystem.outputColorTextureOverride
        lastState = state
        poseStack.scale(1f, -1f, -1f)
        
        if (state.itemStackRenderState.usesBlockLight()) {
            mc.gameRenderer.lighting.setupFor(Lighting.Entry.ITEMS_3D)
        } else {
            mc.gameRenderer.lighting.setupFor(Lighting.Entry.ITEMS_FLAT)
        }
        
        val dispatcher = mc.gameRenderer.featureRenderDispatcher
        state.itemStackRenderState.submit(
            poseStack,
            dispatcher.submitNodeStorage,
            0xF000F0,
            OverlayTexture.NO_OVERLAY,
            0
        )
        dispatcher.renderAllFeatures()
    }
    
    override fun blitTexture(element: State, state: GuiRenderState) {
        val texture = textureView ?: return // Early return if texture is null
        state.addBlitToCurrentLayer(
            BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(
                    texture,
                    RenderSystem.getDevice().createSampler(
                        AddressMode.CLAMP_TO_EDGE,
                        AddressMode.CLAMP_TO_EDGE,
                        FilterMode.NEAREST,
                        FilterMode.NEAREST,
                        1,
                        OptionalDouble.of(0.0)
                    )
                ),
                element.pose(),
                element.x0(),
                element.y0(),
                element.x1(),
                element.y1(),
                0.0f,
                1.0f,
                1.0f,
                0.0f,
                -1,
                element.scissorArea()
            )
        )
    }
    
    override fun textureIsReadyToBlit(state: State): Boolean =
        lastState != null && lastState == state
    
    override fun getTranslateY(height: Int, windowScaleFactor: Int): Float = height / 2f
    override fun getRenderStateClass(): Class<State> = State::class.java
    override fun getTextureLabel(): String = "item_state"
    
    data class State(
        val itemStackRenderState: TrackingItemStackRenderState,
        val pose: Matrix3x2f,
        val x: Int,
        val y: Int,
        val scissorArea: ScreenRectangle?
    ) : PictureInPictureRenderState {
        override fun scale(): Float = maxOf(pose.m00(), pose.m11()) * 16f
        override fun x0(): Int = x
        override fun y0(): Int = y
        override fun x1(): Int = x + scale().toInt()
        override fun y1(): Int = y + scale().toInt()
        override fun scissorArea(): ScreenRectangle? = scissorArea
        override fun bounds(): ScreenRectangle? = null
        override fun pose(): Matrix3x2f = pose
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is State) return false
            if (other.itemStackRenderState.modelIdentity != itemStackRenderState.modelIdentity) return false
            if (other.pose.m00() != pose.m00()) return false
            if (other.pose.m11() != pose.m11()) return false
            return true
        }
        
        override fun hashCode(): Int {
            return Objects.hash(
                itemStackRenderState.modelIdentity,
                pose.m00(),
                pose.m11()
            )
        }
    }
    
    companion object {
        /**
         * Submit an item state to be rendered as a special GUI element.
         */
        fun draw(context: GuiGraphicsExtractor, item: ItemStack, x: Int, y: Int) {
            if (item.isEmpty) return
            
            val tracking = TrackingItemStackRenderState()
            val mc = Melinoe.mc
            mc.itemModelResolver.updateForTopItem(
                tracking,
                item,
                ItemDisplayContext.GUI,
                mc.level,
                mc.player,
                0
            )
            
            val state = State(
                tracking,
                Matrix3x2f(context.pose()),
                x,
                y,
                context.scissorStack.peek()
            )
            context.guiRenderState.addPicturesInPictureState(state)
        }
    }
}
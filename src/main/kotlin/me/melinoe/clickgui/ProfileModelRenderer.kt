package me.melinoe.clickgui

import com.mojang.authlib.GameProfile
import me.melinoe.Melinoe.mc
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.RemotePlayer
import net.minecraft.world.entity.player.PlayerSkin
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

/**
 * Provides the rotating 3D player model shown in the profile sidebar.
 *
 * When viewing your own profile the live [mc.player] is used so the real skin and held items show.
 * For anyone else a lightweight [RemotePlayer] dummy is built. A plain dummy's [getSkin] resolves
 * through the tab-list ([AbstractClientPlayer.getPlayerInfo]), which is only populated for players
 * on the same server, so for offline players it would fall back to a default skin. To fix that we
 * resolve the full textured profile from the UUID via Mojang and override [getSkin] to read the
 * skin straight from the skin manager.
 *
 * The model is submitted through vanilla's inventory entity renderer, so it must be drawn from the
 * GUI render pass rather than inside the NanoVG picture-in-picture lambda.
 */
object ProfileModelRenderer {
    
    private var cached: AbstractClientPlayer? = null
    private var cachedKey: String? = null
    
    // Background profile resolution (textures aren't in the API response).
    @Volatile private var resolvedProfile: GameProfile? = null
    @Volatile private var resolvedKey: String? = null
    @Volatile private var resolvingKey: String? = null
    
    /**
     * Drops the cached dummy avatar. Call when the profile screen closes so the held [RemotePlayer]
     * (and its [net.minecraft.client.multiplayer.ClientLevel] reference) doesn't outlive the screen
     * or go stale across world reloads.
     */
    fun clear() {
        cached = null
        cachedKey = null
        resolvedProfile = null
        resolvedKey = null
        resolvingKey = null
    }
    
    /** All seven [net.minecraft.world.entity.player.PlayerModelPart] bits enabled (hat/jacket/sleeves/pants/cape). */
    private const val ALL_MODEL_PARTS: Byte = 0x7F
    
    /** A dummy avatar whose skin is resolved from the skin manager instead of the tab list. */
    private class ProfileAvatar(level: ClientLevel, profile: GameProfile) : RemotePlayer(level, profile) {
        private val skinLookup: Supplier<PlayerSkin> = mc.skinManager.createLookup(profile, true)
        
        init {
            // A fresh dummy has no model-customisation byte, so the skin's outer (overlay) layers
            // wouldn't render. Enable every part so hats/jackets/sleeves show like the real player.
            entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, ALL_MODEL_PARTS)
        }
        
        override fun getSkin(): PlayerSkin = skinLookup.get()
    }
    
    /**
     * Returns a renderable avatar for the given profile [id]/[username], or null while the world
     * isn't loaded or the skin profile is still resolving (the model appears once it's ready).
     */
    fun avatarFor(id: String?, username: String?): AbstractClientPlayer? {
        val level = mc.level ?: return null
        val name = username ?: return null
        
        val self = mc.player
        if (self != null && self.gameProfile.name.equals(name, ignoreCase = true)) return self
        
        val key = "$id|$name"
        cached?.let { if (cachedKey == key) return it }
        
        // Build the dummy once its textured profile has been resolved.
        if (resolvedKey == key) {
            resolvedProfile?.let { profile ->
                val avatar = ProfileAvatar(level, profile)
                cached = avatar
                cachedKey = key
                return avatar
            }
        }
        
        // Kick off resolution once per profile.
        if (resolvingKey != key) {
            resolvingKey = key
            val uuid = runCatching { UUID.fromString(id) }.getOrNull()
                ?: UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))
            CompletableFuture.runAsync {
                val resolved = runCatching { mc.services().profileResolver().fetchById(uuid).orElse(null) }
                    .getOrNull() ?: GameProfile(uuid, name)
                resolvedProfile = resolved
                resolvedKey = key
            }
        }
        return null
    }
    
    /**
     * Submits [avatar] as a 3D model that fits the box [x0],[y0]-[x1],[y1] (GUI-space pixels) and
     * follows the cursor at [mouseX]/[mouseY] (also GUI-space). [scale] controls model size.
     */
    fun render(
        context: GuiGraphicsExtractor,
        avatar: AbstractClientPlayer,
        x0: Int, y0: Int, x1: Int, y1: Int,
        scale: Int,
        mouseX: Float, mouseY: Float
    ) {
        InventoryScreen.extractEntityInInventoryFollowsMouse(
            context, x0, y0, x1, y1, scale, 0f, mouseX, mouseY, avatar
        )
    }
}

package me.melinoe.utils

import me.melinoe.Melinoe
import me.melinoe.utils.ItemShareCodec.encode
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.world.item.ItemStack
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*

/**
 * Serializes a full [ItemStack] (item id, count, and every data component) into a compact
 * Base64 string for relaying over the websocket, and reconstructs it on the other side
 *
 * The Base64 string is IDENTICAL to the item shared
 */
object ItemShareCodec {

    // Upper bound on a decoded item's NBT size, guarding against malicious payloads
    private const val MAX_DECODED_BYTES = 2L * 1024 * 1024

    private fun registryAccess() =
        Melinoe.mc.level?.registryAccess() ?: Melinoe.mc.connection?.registryAccess()

    /** Encodes a non-empty stack to a Base64 string, or null on failure */
    fun encode(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val registry = registryAccess() ?: return null
        val ops = registry.createSerializationContext(NbtOps.INSTANCE)

        val tag = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null) as? CompoundTag ?: return null

        return try {
            ByteArrayOutputStream().use { baos ->
                NbtIo.writeCompressed(tag, baos)
                Base64.getEncoder().encodeToString(baos.toByteArray())
            }
        } catch (e: Exception) {
            Melinoe.logger.error("[ItemShareCodec] Failed to encode item", e)
            null
        }
    }

    /** Decodes a Base64 string produced by [encode] back into an [ItemStack], or null on failure */
    fun decode(data: String): ItemStack? {
        val registry = registryAccess() ?: return null
        val ops = registry.createSerializationContext(NbtOps.INSTANCE)

        return try {
            val bytes = Base64.getDecoder().decode(data)
            val tag = ByteArrayInputStream(bytes).use { bais ->
                // Bound the decompressed size
                NbtIo.readCompressed(bais, NbtAccounter.create(MAX_DECODED_BYTES))
            }
            ItemStack.CODEC.parse(ops, tag).result().orElse(null)
        } catch (e: Exception) {
            Melinoe.logger.error("[ItemShareCodec] Failed to decode item", e)
            null
        }
    }
}
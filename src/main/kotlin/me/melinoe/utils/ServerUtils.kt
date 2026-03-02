package me.melinoe.utils

import me.melinoe.Melinoe
import me.melinoe.events.core.onReceive
import net.minecraft.Util
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket
import kotlin.math.min

/**
 * Server utilities for tracking TPS, Ping, and server detection.
 */
object ServerUtils {
    private var prevTime = 0L
    var averageTps = 20f
        private set

    var currentPing: Int = 0
        private set

    var averagePing: Int = 0
        private set

    /**
     * Check if the player is on Telos Realms server
     */
    fun isOnTelos(): Boolean {
        val mc = Melinoe.mc
        val connection = mc.connection ?: return false
        val serverData = connection.serverData ?: return false
        val serverAddress = serverData.ip.lowercase()
        return mc.level != null && !mc.isPaused && serverAddress.contains("telosrealms.com")
    }

    init {
        onReceive<ClientboundSetTimePacket> {
            if (prevTime != 0L)
                averageTps = (20000f / (System.currentTimeMillis() - prevTime + 1)).coerceIn(0f, 20f)

            prevTime = System.currentTimeMillis()
        }

        onReceive<ClientboundPongResponsePacket> {
            val mc = Melinoe.mc
            currentPing = (Util.getMillis() - time).toInt().coerceAtLeast(0)

            val pingLog = mc.debugOverlay.pingLogger

            val sampleSize = min(pingLog.size(), 20)

            if (sampleSize == 0) {
                averagePing = currentPing
                return@onReceive
            }

            var total = 0L
            for (i in 0 until sampleSize) {
                total += pingLog.get(i)
            }

            averagePing = (total / sampleSize).toInt()
        }
    }
}
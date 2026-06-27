package me.melinoe.features.impl.misc

import me.melinoe.Melinoe
import me.melinoe.events.ChatPacketEvent
import me.melinoe.events.TickEvent
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.network.ModWebSocket
import me.melinoe.utils.ChatManager.hideMessage
import me.melinoe.utils.ItemShareCodec
import me.melinoe.utils.ServerUtils
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.world.item.ItemStackTemplate
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Lets Melinoe users share their held item in chat by typing [item] anywhere in a message.
 * When such a message is sent, the held item is relayed over the mod websocket; the chat
 * message itself sends normally with the plain marker, which other Melinoe clients swap for
 * a hoverable item name. Non-mod players just see "[item]"
 */
object ItemShareModule : Module(
    name = "Item Share",
    category = Category.MISC,
    description = "Share your held item in chat as a hoverable tooltip for other Melinoe users"
) {
    private const val TOKEN = "[item]"
    private const val MAX_QUEUED_PER_SENDER = 16

    // How long to hold an [item] chat line waiting for its payload to arrive over the websocket
    private const val BUFFER_TIMEOUT_MS = 2000L

    // A cached payload is discarded if its [item] echo hasn't arrived within this window
    private const val STALE_PAYLOAD_MS = 10000L

    // Minecraft usernames: 3-16 chars of [A-Za-z0-9_]. Used to pull the author out of a chat message
    private val NAME_PATTERN = Regex("[A-Za-z0-9_]{3,16}")

    private var outgoingSeq = 0

    // Per-sender FIFO of pending item payloads (with arrival time), keyed by lowercase username
    private val pending = ConcurrentHashMap<String, ArrayDeque<Payload>>()
    
    private data class Payload(val data: String, val at: Long, val marker: String?)

    // [item] chat lines that arrived before their payload, rendered (or restored) on a later tick
    private val awaiting = ConcurrentLinkedQueue<BufferedShare>()

    private data class BufferedShare(val original: Component, val at: Long)

    init {
        on<ChatPacketEvent> {
            if (!enabled) return@on
            if (!ServerUtils.isOnTelos()) return@on
            if (!value.contains(TOKEN)) return@on
            renderSharedItem()
        }

        on<TickEvent.End> {
            if (enabled && awaiting.isNotEmpty()) processAwaiting()
        }
    }

    override fun onEnable() {
        super.onEnable()
        ModWebSocket.itemDataHandler = ::onItemData
    }

    override fun onDisable() {
        super.onDisable()
        ModWebSocket.itemDataHandler = null
        pending.clear()
        awaiting.clear()
    }

    /** Websocket thread: cache the payload under the sender's name, with their message text as marker */
    private fun onItemData(sender: String, seq: Int, data: String, text: String?) {
        // Our own shares are cached locally at send time (race-free), so ignore the copy
        val self = mc.user?.name
        if (self != null && sender.equals(self, ignoreCase = true)) return

        // text is the sender's original message
        enqueue(sender.lowercase(Locale.ROOT), data, marker = text)
    }

    /** Adds a payload to a sender's queue */
    private fun enqueue(senderKey: String, data: String, marker: String? = null) {
        val queue = pending.computeIfAbsent(senderKey) { ArrayDeque() }
        synchronized(queue) {
            queue.addLast(Payload(data, System.currentTimeMillis(), marker))
            while (queue.size > MAX_QUEUED_PER_SENDER) queue.removeFirst()
        }
    }

    /**
     * Called from [me.melinoe.mixin.mixins.ClientPlayNetworkHandlerMixin] at the moment a chat
     * message or command is actually transmitted
     */
    fun onOutgoingMessage(message: String) {
        if (!enabled) return
        if (!ServerUtils.isOnTelos()) return
        if (!message.contains(TOKEN)) return

        val player = mc.player ?: return
        val stack = player.mainHandItem
        // Nothing held -> the marker just sends as literal text for everyone
        if (stack.isEmpty) return

        val data = ItemShareCodec.encode(stack) ?: return
        val marker = message.trim()
        ModWebSocket.sendShareItem(outgoingSeq++, data, marker)

        // Cache under our own name so our echoed [item] line renders without waiting on the relay
        val self = mc.user?.name
        if (self != null) enqueue(self.lowercase(Locale.ROOT), data, marker = marker)
    }

    /**
     * Main thread: if the item is already cached, swap [item] for it now. Otherwise the chat
     * line beat the relay (the common case), hide it and change it later
     */
    private fun ChatPacketEvent.renderSharedItem() {
        hideMessage()
        val data = pollForLine(value)
        if (data != null) {
            renderInto(component, data)
        } else {
            // Payload not here yet, or sender isn't sharing
            awaiting.add(BufferedShare(component, System.currentTimeMillis()))
        }
    }

    /** Tick: render buffered lines whose payload has now arrived, or restore ones that timed out */
    private fun processAwaiting() {
        val now = System.currentTimeMillis()
        val it = awaiting.iterator()
        while (it.hasNext()) {
            val buffered = it.next()
            val data = pollForLine(buffered.original.string)
            when {
                data != null -> {
                    it.remove()
                    renderInto(buffered.original, data)
                }
                now - buffered.at > BUFFER_TIMEOUT_MS -> {
                    // Gave up waiting, show it unchanged
                    it.remove()
                    Melinoe.mc.execute { Melinoe.mc.gui.chat.addClientSystemMessage(buffered.original) }
                }
            }
        }
    }

    /** Decodes [data] and re-adds [original] with [item] replaced by the item's hover name */
    private fun renderInto(original: Component, data: String) {
        val stack = ItemShareCodec.decode(data)
        if (stack == null || stack.isEmpty) {
            Melinoe.mc.execute { Melinoe.mc.gui.chat.addClientSystemMessage(original) }
            return
        }
        val template = ItemStackTemplate.fromNonEmptyStack(stack)
        val replacement = trimName(stack.hoverName).withStyle { it.withHoverEvent(HoverEvent.ShowItem(template)) }
        val rebuilt = replaceToken(original, TOKEN, replacement)
        Melinoe.mc.execute { Melinoe.mc.gui.chat.addClientSystemMessage(rebuilt) }
    }

    /**
     * Pops the payload for whichever pending sender this chat line belongs to, or null if none
     * matches yet
     */
    private fun pollForLine(line: String): String? {
        val sender = matchPendingSender(line) ?: return null
        val queue = pending[sender] ?: return null
        val data = synchronized(queue) {
            // Drop stale payloads whose echo never arrived so they can't show as the wrong item
            val now = System.currentTimeMillis()
            while (true) {
                val head = queue.peekFirst() ?: break
                if (now - head.at > STALE_PAYLOAD_MS) queue.removeFirst() else break
            }
            val matched = queue.firstOrNull { !it.marker.isNullOrEmpty() && line.contains(it.marker) }
            if (matched != null) {
                queue.remove(matched)
                matched.data
            } else {
                queue.pollFirst()?.data
            }
        }
        // Intentionally leave emptied queues in the map
        return data
    }

    private fun matchPendingSender(line: String): String? {
        if (pending.isEmpty()) return null
        
        val self = mc.user?.name?.lowercase(Locale.ROOT)
        if (self != null && pending.containsKey(self) && line.contains('┅') && line.contains("To ")) {
            return self
        }

        val beforeColon = line.substringBefore(": ", "")
        if (beforeColon.isNotEmpty()) {
            val author = NAME_PATTERN.findAll(beforeColon).lastOrNull()?.value?.lowercase(Locale.ROOT)
            if (author != null && pending.containsKey(author)) return author
        }

        return pending.keys.firstOrNull { containsWholeWord(line, it) }
    }
    
    private fun containsWholeWord(line: String, name: String): Boolean {
        var from = 0
        while (true) {
            val idx = line.indexOf(name, from, ignoreCase = true)
            if (idx < 0) return false
            val before = idx == 0 || !line[idx - 1].isUsernameChar()
            val after = idx + name.length >= line.length || !line[idx + name.length].isUsernameChar()
            if (before && after) return true
            from = idx + 1
        }
    }

    private fun Char.isUsernameChar() = this == '_' || this.isLetterOrDigit()

    /**
     * Rebuilds an item's display name with leading/trailing whitespace trimmed
     */
    private fun trimName(name: Component): net.minecraft.network.chat.MutableComponent {
        data class Run(val style: Style, var text: String)
        val runs = mutableListOf<Run>()
        name.visit({ style: Style, text: String ->
            runs.add(Run(style, text))
            Optional.empty<Unit>()
        }, Style.EMPTY)

        // Trim whitespace-only runs from each end, then trim the partial runs at the edges
        var i = 0
        while (i < runs.size) {
            runs[i].text = runs[i].text.trimStart()
            if (runs[i].text.isNotEmpty()) break
            i++
        }
        var j = runs.size - 1
        while (j >= 0) {
            runs[j].text = runs[j].text.trimEnd()
            if (runs[j].text.isNotEmpty()) break
            j--
        }

        val result = Component.empty()
        for (run in runs) if (run.text.isNotEmpty()) result.append(Component.literal(run.text).withStyle(run.style))
        return result
    }

    /** Rebuilds [original], replacing each plain-text occurrence of [token] with [replacement] */
    private fun replaceToken(original: Component, token: String, replacement: Component): Component {
        val result = Component.empty()
        original.visit({ style: Style, text: String ->
            if (!text.contains(token)) {
                result.append(Component.literal(text).withStyle(style))
            } else {
                var idx = 0
                while (true) {
                    val found = text.indexOf(token, idx)
                    if (found < 0) {
                        if (idx < text.length) result.append(Component.literal(text.substring(idx)).withStyle(style))
                        break
                    }
                    if (found > idx) result.append(Component.literal(text.substring(idx, found)).withStyle(style))
                    result.append(replacement)
                    idx = found + token.length
                }
            }
            Optional.empty<Unit>()
        }, Style.EMPTY)
        return result
    }
}
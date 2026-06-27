package me.melinoe.utils.emoji

import me.melinoe.utils.getMelinoeGradient
import me.melinoe.utils.toNative
import net.minecraft.network.chat.*
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.network.chat.contents.TranslatableContents
import java.util.concurrent.ConcurrentHashMap

object EmojiReplacer {
    
    private val hoverCache = ConcurrentHashMap<String, Component>()
    
    private fun getHoverComponent(shortcode: String): Component {
        return hoverCache.getOrPut(shortcode) {
            ("<gray>This is a " + getMelinoeGradient() + " mod emoji and is only visible to other mod users.<br>It is shown as <yellow>$shortcode</yellow> for non-mod users.</gray>").toNative()
        }
    }
    
    fun replaceIn(input: Component): Component {
        return replaceTree(input)
    }
    
    private fun replaceInString(s: String?): String {
        if (s.isNullOrEmpty() || !s.contains(":")) return s ?: ""
        
        val sb = StringBuilder()
        var lastPos = 0
        var i = 0
        while (i < s.length) {
            if (s[i] != ':') { i++; continue }
            
            val maxSearch = minOf(i + 30, s.length)
            var closingColon = -1
            for (j in i + 1 until maxSearch) {
                if (s[j] == ':') { closingColon = j; break }
            }
            if (closingColon == -1) { i++; continue }
            
            val candidate = s.substring(i, closingColon + 1)
            val emoji = EmojiShortcodes.mappings[candidate]
            
            if (emoji != null) {
                sb.append(s, lastPos, i)
                sb.append(emoji)
                i = closingColon
                lastPos = i + 1
            }
            i++
        }
        sb.append(s, lastPos, s.length)
        return sb.toString()
    }
    
    private fun replaceTree(node: Component): Component {
        val result: MutableComponent
        val contents = node.contents
        
        if (contents is PlainTextContents) {
            val original = contents.text()
            val replaced = replaceInString(original)
            
            val containsEmojiChar = replaced.codePoints().anyMatch { it in 0xF400..0xF4FF || it in 0x1525E..0x152AB }
            
            result = if (replaced != original || containsEmojiChar) {
                replaceWithColorPreservation(original, baseStyle = node.style)
            } else {
                Component.literal(replaced).withStyle(node.style)
            }
        } else if (contents is TranslatableContents) {
            val args = contents.args
            
            val replacedArgs = Array(args.size) { i ->
                when (val a = args[i]) {
                    is Component -> replaceTree(a)
                    is String -> replaceInString(a)
                    else -> a ?: ""
                }
            }
            
            result = Component.translatable(contents.key, *replacedArgs).withStyle(node.style)
        } else {
            result = node.copy()
            result.siblings.clear()
        }
        
        for (sibling in node.siblings) {
            result.append(replaceTree(sibling))
        }
        return result
    }
    
    private fun replaceWithColorPreservation(original: String, baseStyle: Style): MutableComponent {
        val result = Component.empty()
        val expandedText = replaceInString(original)
        val currentText = StringBuilder()
        
        var pos = 0
        while (pos < expandedText.length) {
            val cp = expandedText.codePointAt(pos)
            val charCount = Character.charCount(cp)
            
            // Check for both Mod Emojis and Server Emojis
            if (cp in 0xF400..0xF4FF || cp in 0x1525E..0x152AB) {
                if (currentText.isNotEmpty()) {
                    result.append(Component.literal(currentText.toString()).withStyle(baseStyle))
                    currentText.setLength(0)
                }
                
                val emojiStr = String(Character.toChars(cp))
                val isModEmoji = cp in 0xF400..0xF4FF
                
                val emojiComponent = Component.literal(emojiStr).withStyle(baseStyle).withStyle { s ->
                    var newStyle = s.withColor(TextColor.fromRgb(0xFFFFFF))
                    
                    // Attach the cached MiniMessage HoverEvent exclusively for mod-provided emojis
                    if (isModEmoji) {
                        val shortcode = EmojiShortcodes.reverseMappings[emojiStr] ?: ":emoji:"
                        newStyle = newStyle.withHoverEvent(HoverEvent.ShowText(getHoverComponent(shortcode)))
                    }
                    newStyle
                }
                
                result.append(emojiComponent)
            } else {
                currentText.appendCodePoint(cp)
            }
            pos += charCount
        }
        
        if (currentText.isNotEmpty()) {
            result.append(Component.literal(currentText.toString()).withStyle(baseStyle))
        }
        return result
    }
}
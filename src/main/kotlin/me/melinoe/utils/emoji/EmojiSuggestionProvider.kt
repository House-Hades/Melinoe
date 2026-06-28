package me.melinoe.utils.emoji

import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.suggestion.Suggestion
import com.mojang.brigadier.suggestion.Suggestions
import net.minecraft.client.gui.components.EditBox
import java.util.concurrent.CompletableFuture

object EmojiSuggestionProvider {

    private val emojiTypingRegex = Regex("(?i):[a-z0-9_]*:?")

    // Commands whose message argument should accept emoji shortcodes (chat channels + messages)
    private val emojiCommandPrefixes = listOf(
        "/ac ", "/gc ", "/grc ", "/msg ", "/r ", "/reply "
    )

    private fun isEmojiCommand(text: String): Boolean =
        emojiCommandPrefixes.any { text.startsWith(it, ignoreCase = true) }

    // Walks backwards from cursor to find a valid opening colon
    private fun findOpeningColon(text: String, cursor: Int): Int {
        var start = cursor - 1
        var endsWithColon = false
        if (start >= 0 && text[start] == ':') { endsWithColon = true; start-- }
        while (start >= 0) {
            val c = text[start]
            if (c == ':') return start
            if (c == ' ') return if (endsWithColon) cursor - 1 else -1
            start--
        }
        return if (start < 0 && endsWithColon) cursor - 1 else -1
    }
    
    fun isTypingEmoji(field: EditBox?): Boolean {
        if (field == null) return false
        val text = field.value
        if (text.isEmpty()) return false
        
        val cursor = field.cursorPosition
        if (cursor <= 0 || cursor > text.length) return false
        
        if (text.startsWith("/") && !isEmojiCommand(text)) return false
        
        val start = findOpeningColon(text, cursor)
        if (start < 0) return false
        
        return text.substring(start, cursor).matches(emojiTypingRegex)
    }
    
    fun provideSuggestions(input: String?, cursor: Int): CompletableFuture<Suggestions> {
        if (input.isNullOrEmpty() || cursor <= 0 || cursor > input.length) return Suggestions.empty()
        
        val start = findOpeningColon(input, cursor)
        if (start < 0) return Suggestions.empty()
        
        val token = input.substring(start, cursor)
        var searchText = ""
        
        if (token.length > 1) {
            searchText = token.substring(1).lowercase()
            if (searchText.endsWith(":")) searchText = searchText.dropLast(1)
        }
        
        // Bucket arrays for sorting priority.
        val exactMatches = mutableListOf<Suggestion>()
        val startsWithMatches = mutableListOf<Suggestion>()
        val containsMatches = mutableListOf<Suggestion>()
        
        for (data in EmojiShortcodes.suggestionList) {
            if (data.isServerEmoji && !EmojiShortcodes.hasSupporterPerks) continue
            
            if (searchText.isEmpty()) {
                containsMatches.add(Suggestion(StringRange.between(start, cursor), data.suggestionString))
                continue
            }
            
            val cleanName = data.cleanName
            
            if (cleanName == searchText) {
                exactMatches.add(Suggestion(StringRange.between(start, cursor), data.suggestionString))
            } else if (cleanName.startsWith(searchText)) {
                startsWithMatches.add(Suggestion(StringRange.between(start, cursor), data.suggestionString))
            } else if (cleanName.contains(searchText)) {
                containsMatches.add(Suggestion(StringRange.between(start, cursor), data.suggestionString))
            }
        }
        
        exactMatches.sortBy { it.text }
        startsWithMatches.sortBy { it.text }
        containsMatches.sortBy { it.text }
        
        val allValidSuggestions = ArrayList<Suggestion>(exactMatches.size + startsWithMatches.size + containsMatches.size)
        allValidSuggestions.addAll(exactMatches)
        allValidSuggestions.addAll(startsWithMatches)
        allValidSuggestions.addAll(containsMatches)
        
        if (allValidSuggestions.isEmpty()) return Suggestions.empty()
        
        return CompletableFuture.completedFuture(
            Suggestions(StringRange.between(start, cursor), allValidSuggestions.take(200))
        )
    }
    
    fun mergeAndCheckPerks(serverSugs: Suggestions, modSugs: Suggestions, inputText: String): Suggestions {
        if (!EmojiShortcodes.hasSupporterPerks) {
            if (serverSugs.list.any { EmojiShortcodes.serverEmojis.containsKey(it.text) }) {
                EmojiShortcodes.hasSupporterPerks = true
            }
        }
        
        val finalModSugs = if (!EmojiShortcodes.hasSupporterPerks) {
            modSugs.list.filter { !it.text.contains("*") }
        } else {
            modSugs.list
        }
        
        val ourShortcodes = finalModSugs.map { it.text.substringBefore("*").substringBefore(" ") }.toSet()
        val merged = finalModSugs.toMutableList()
        
        val isCustomCmd = isEmojiCommand(inputText)

        for (s in serverSugs.list) {
            val cleanText = s.text
            
            // Clean out the generic <message> tooltip inside the suggestions box for our proxy commands
            if (isCustomCmd && cleanText.startsWith("<") && cleanText.endsWith(">")) continue
            
            // Discard server emojis since we already have them
            if (cleanText.startsWith(":")) continue
            
            if (!ourShortcodes.contains(cleanText) && !ourShortcodes.contains("$cleanText:")) {
                merged.add(s)
            }
        }
        
        return Suggestions(modSugs.range, merged)
    }
}
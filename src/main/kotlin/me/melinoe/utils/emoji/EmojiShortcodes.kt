package me.melinoe.utils.emoji

import net.minecraft.client.gui.components.EditBox
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

data class EmojiData(
    val shortcode: String,
    val cleanName: String,
    val character: String,
    val isServerEmoji: Boolean,
    val suggestionString: String
)

object EmojiShortcodes {
    // Unlocked automatically if the server's sync / packet suggests the emojis to the player.
    var hasSupporterPerks: Boolean = false
    
    val serverEmojis = mapOf(
        ":aha:" to "𕉞", ":ahahahaha:" to "𕉟", ":angrey:" to "𕉠", ":basketbol:" to "𕉡",
        ":batemanstare:" to "𕉢", ":black:" to "𕉣", ":bruh:" to "𕉤", ":cooldude:" to "𕉥",
        ":crazycry:" to "𕉦", ":crysomany:" to "𕉧", ":death:" to "𕉨", ":deepfriedvalorcry:" to "𕉩",
        ":despair:" to "𕉰", ":devastation:" to "𕉱", ":downvote:" to "𕉲", ":dudewtf:" to "𕉳",
        ":ecstatic:" to "𕉴", ":eyer:" to "𕉵", ":fuckinghell:" to "𕉶", ":hahaokaybro:" to "𕉷",
        ":hmm:" to "𕉸", ":holymoly:" to "𕉺", ":icaca:" to "𕉻", ":idied:" to "𕉼",
        ":iloveu:" to "𕉽", ":knifed:" to "𕉾", ":livid:" to "𕉿", ":luffy:" to "𕊀",
        ":meowerhour:" to "𕊁", ":monstrocry:" to "𕊂", ":mrsunglas:" to "𕊃", ":noober:" to "𕊃",
        ":nowayman:" to "𕊄", ":omfg:" to "𕊅", ":onyx:" to "𕊆", ":pain:" to "𕊇",
        ":ridiculous:" to "𕊈", ":shutup:" to "𕊉", ":skully:" to "𕊊", ":sorrow:" to "𕊋",
        ":spiderleft:" to "𕊌", ":spiderright:" to "𕊍", ":throwup:" to "𕊎", ":tolet:" to "𕊏",
        ":toother:" to "𕊐", ":valorangry:" to "𕊑", ":valorconfusion:" to "𕊒", ":valorcoolguy:" to "𕊓",
        ":valorcry:" to "𕊔", ":valordeath:" to "𕊕", ":valorgrr:" to "𕊖", ":valorhappy:" to "𕊗",
        ":valorhmm:" to "𕊘", ":valoridumdum:" to "𕊙", ":valorlook:" to "𕊚", ":valornocry:" to "𕊛",
        ":valornod:" to "𕊜", ":valorpat:" to "𕊝", ":valorpray:" to "𕊞", ":valorsad:" to "𕊟",
        ":valorsalute:" to "𕊠", ":valorsmirk:" to "𕊡", ":valortear:" to "𕊢", ":waaa:" to "𕊣",
        ":wayment:" to "𕊤", ":what:" to "𕊥", ":white:" to "𕊦", ":winker:" to "𕊧",
        ":wtfvalorcry:" to "𕊨", ":yes:" to "𕊩", ":youforgor:" to "𕊪", ":zamn:" to "𕊫"
    )
    
    val mappings: Map<String, String> by lazy { loadMappings() + serverEmojis }
    val reverseMappings: Map<String, String> by lazy { mappings.entries.associate { it.value to it.key } }
    
    val suggestionList: List<EmojiData> by lazy {
        mappings.map {
            val isServer = serverEmojis.containsKey(it.key)
            EmojiData(
                shortcode = it.key,
                cleanName = it.key.substring(1, it.key.length - 1).lowercase(),
                character = it.value,
                isServerEmoji = isServer,
                suggestionString = if (isServer) "${it.key}* ${it.value}" else "${it.key} ${it.value}"
            )
        }
    }
    
    val nativeToShortcode: Map<String, String> = mapOf(
        "😀" to ":grinning:", "😃" to ":smiley:", "😄" to ":smile:", "😁" to ":grin:",
        "😆" to ":laughing:", "😅" to ":sweat_smile:", "🤣" to ":rofl:", "😂" to ":joy:",
        "🙂" to ":slight_smile:", "🙃" to ":upside_down:", "😉" to ":wink:", "😊" to ":blush:",
        "😇" to ":innocent:", "🥰" to ":smiling_face_with_3_hearts:", "😍" to ":heart_eyes:",
        "😘" to ":kissing_heart:", "☺️" to ":relaxed:", "😚" to ":kissing_closed_eyes:",
        "🥲" to ":smiling_face_with_tear:", "😋" to ":yum:", "😛" to ":stuck_out_tongue:",
        "😜" to ":stuck_out_tongue_winking_eye:", "🤪" to ":zany_face:", "😝" to ":stuck_out_tongue_closed_eyes:",
        "🤑" to ":money_mouth:", "🤗" to ":hugging:", "🤭" to ":face_with_hand_over_mouth:",
        "🫢" to ":gasp:", "🫣" to ":face_with_peeking_eye:",
        "🤫" to ":shushing_face:", "🤔" to ":thinking:", "🫡" to ":saluting_face:",
        "🤐" to ":zipper_mouth:", "🤨" to ":face_with_raised_eyebrow:", "😐" to ":neutral_face:",
        "😑" to ":expressionless:", "😶" to ":no_mouth:", "🫥" to ":dotted_line_face:",
        "😏" to ":smirk:", "😒" to ":unamused:", "🙄" to ":rolling_eyes:", "😬" to ":grimacing:",
        "🤥" to ":lying_face:", "😌" to ":relieved:", "😔" to ":pensive:", "😪" to ":sleepy:",
        "🤤" to ":drooling_face:", "😴" to ":sleeping:", "😮‍💨" to ":face_exhaling:",
        "🤮" to ":face_vomiting:", "🤧" to ":sneezing_face:", "🥵" to ":hot_face:",
        "🥶" to ":cold_face:", "🥴" to ":woozy_face:", "😵" to ":dizzy_face:",
        "🤯" to ":exploding_head:", "🥳" to ":partying_face:", "😎" to ":sunglasses:",
        "🤓" to ":nerd:", "🧐" to ":face_with_monocle:", "🙁" to ":slight_frown:",
        "😮" to ":open_mouth:", "😲" to ":astonished:", "😳" to ":flushed:",
        "🥺" to ":pleading_face:", "🥹" to ":face_holding_back_tears:", "☹️" to ":frowning:",
        "😧" to ":anguished:", "😨" to ":fearful:", "😰" to ":cold_sweat:",
        "😥" to ":disappointed_relieved:", "😢" to ":cry:", "😭" to ":sob:",
        "😱" to ":scream:", "😣" to ":persevere:", "😓" to ":sweat:",
        "😫" to ":tired_face:", "🥱" to ":yawning_face:", "😡" to ":rage:",
        "😠" to ":angry:", "😈" to ":smiling_imp:", "💀" to ":skull:",
        "💩" to ":poop:", "🤡" to ":clown:", "👻" to ":ghost:", "👽" to ":alien:",
        "😺" to ":smiley_cat:", "😸" to ":smile_cat:", "😹" to ":joy_cat:",
        "😻" to ":heart_eyes_cat:", "😼" to ":smirk_cat:", "😽" to ":kissing_cat:",
        "🙀" to ":scream_cat:", "😿" to ":crying_cat_face:", "😾" to ":pouting_cat:",
        "🙈" to ":see_no_evil:", "🙉" to ":hear_no_evil:", "🙊" to ":speak_no_evil:",
        "💖" to ":sparkling_heart:", "💞" to ":revolving_hearts:", "💔" to ":broken_heart:",
        "❤️" to ":heart:", "🩷" to ":pink_heart:", "💙" to ":blue_heart:",
        "💜" to ":purple_heart:", "🤍" to ":white_heart:", "💯" to ":100:",
        "💥" to ":boom:", "💦" to ":sweat_drops:", "💬" to ":speech_balloon:",
        "💤" to ":zzz:", "👋" to ":wave:", "👌" to ":ok_hand:", "🤌" to ":pinched_fingers:",
        "🤏" to ":pinching_hand:", "✌️" to ":v:", "🫰" to ":finger_heart:",
        "🤙" to ":call_me:", "👈" to ":point_left:", "👉" to ":point_right:",
        "👆" to ":point_up_2:", "🖕" to ":middle_finger:", "👇" to ":point_down:",
        "☝️" to ":point_up:", "👍" to ":thumbsup:", "👎" to ":thumbsdown:",
        "✊" to ":fist:", "👊" to ":punch:", "🤛" to ":left_facing_fist:",
        "🤜" to ":right_facing_fist:", "👏" to ":clap:", "🙌" to ":raised_hands:",
        "🫶" to ":heart_hands:", "👐" to ":open_hands:", "🤲" to ":palms_up_together:",
        "🤝" to ":handshake:", "🙏" to ":pray:", "💪" to ":muscle:", "👀" to ":eyes:",
        "👅" to ":tongue:", "🫦" to ":biting_lip:", "🥀" to ":wilted_rose:",
        "🏆" to ":trophy:", "🥇" to ":first_place:", "🥈" to ":second_place:",
        "🥉" to ":third_place:", "✨" to ":sparkles:", "🎀" to ":ribbon:",
        "❤️‍🔥" to ":heart_on_fire:", "❤️‍🩹" to ":mending_heart:", "🔥" to ":fire:"
    )
    
    // Pre-calculated & cached for the TextInputHandler typing hook
    val sortedNatives: List<String> by lazy {
        nativeToShortcode.keys.sortedByDescending { it.length }
    }
    
    private val emojiStarts: BooleanArray by lazy {
        val arr = BooleanArray(65536)
        reverseMappings.keys.forEach { if (it.isNotEmpty()) arr[it[0].code] = true }
        arr
    }
    
    private val maxEmojiLength: Int by lazy { reverseMappings.keys.maxOfOrNull { it.length }?.coerceAtLeast(4) ?: 4 }
    
    private val artifactRegex = Regex("(:[a-zA-Z0-9_]+:)\\*?\\s+[\\uE000-\\uF8FF\\x{1525E}-\\x{152AB}]+")
    
    init {
        // Preload massive properties mapping dictionaries in a Daemon thread
        Thread({
            mappings.size
            reverseMappings.size
            suggestionList.size
            emojiStarts.size
            sortedNatives.size
        }, "Melinoe-Emoji-Preloader").apply {
            isDaemon = true
            start()
        }
    }
    
    private fun loadMappings(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            var stream = Thread.currentThread().contextClassLoader.getResourceAsStream("assets/melinoe/emoji/shortcodes.properties")
                ?: EmojiShortcodes::class.java.getResourceAsStream("/assets/melinoe/emoji/shortcodes.properties")
            
            stream?.use { s ->
                BufferedReader(InputStreamReader(s, StandardCharsets.UTF_8)).useLines { lines ->
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                        val eq = trimmed.indexOf('=')
                        if (eq > 0) {
                            val key = trimmed.substring(0, eq).trim()
                            val value = trimmed.substring(eq + 1).trim()
                            if (key.isNotEmpty() && value.isNotEmpty()) {
                                map[key] = unescapeJavaString(value)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }
    
    private fun unescapeJavaString(st: String): String {
        return buildString(st.length) {
            var i = 0
            while (i < st.length) {
                val ch = st[i]
                if (ch == '\\' && i + 1 < st.length && st[i + 1] == 'u' && i + 5 < st.length) {
                    try {
                        append(st.substring(i + 2, i + 6).toInt(16).toChar())
                        i += 6
                        continue
                    } catch (ignored: NumberFormatException) {}
                }
                append(ch)
                i++
            }
        }
    }
    
    fun isEmojiStart(c: Char): Boolean = emojiStarts[c.code]
    
    fun getEmojiBeforeCursor(text: String, cursor: Int): String? {
        for (len in maxEmojiLength downTo 1) {
            if (cursor - len >= 0) {
                val candidate = text.substring(cursor - len, cursor)
                if (reverseMappings.containsKey(candidate)) return candidate
            }
        }
        return null
    }
    
    fun replaceEmojiWithShortcodes(input: String?): String {
        if (input.isNullOrEmpty()) return input ?: ""
        return buildString(input.length) {
            var i = 0
            while (i < input.length) {
                val c = input[i]
                if (isEmojiStart(c)) {
                    var matched = false
                    for (len in maxEmojiLength downTo 1) {
                        if (i + len <= input.length) {
                            val candidate = input.substring(i, i + len)
                            val shortcode = reverseMappings[candidate]
                            if (shortcode != null) {
                                if (serverEmojis.containsKey(shortcode) && !hasSupporterPerks) break
                                append(shortcode)
                                i += len
                                matched = true
                                break
                            }
                        }
                    }
                    if (!matched) { append(c); i++ }
                } else {
                    append(c); i++
                }
            }
        }
    }
    
    /**
     * Converts every emoji glyph (mod + server) back into its `:shortcode:` form for copying
     */
    fun replaceEmojiWithShortcodesForCopy(input: String?): String {
        if (input.isNullOrEmpty()) return input ?: ""
        return buildString(input.length) {
            var i = 0
            while (i < input.length) {
                val c = input[i]
                if (isEmojiStart(c)) {
                    var matched = false
                    for (len in maxEmojiLength downTo 1) {
                        if (i + len <= input.length) {
                            val candidate = input.substring(i, i + len)
                            val shortcode = reverseMappings[candidate]
                            if (shortcode != null) {
                                append(shortcode)
                                i += len
                                matched = true
                                break
                            }
                        }
                    }
                    if (!matched) { append(c); i++ }
                } else {
                    append(c); i++
                }
            }
        }
    }

    fun processEditBox(input: EditBox) {
        val original = input.value
        if (original.isEmpty()) return
        
        var text = original
        var cursor = input.cursorPosition
        
        if (artifactRegex.containsMatchIn(text)) {
            val matcher = artifactRegex.toPattern().matcher(text)
            val sb = StringBuffer()
            while (matcher.find()) {
                val match = matcher.group()
                val replacement = matcher.group(1)
                matcher.appendReplacement(sb, replacement)
                
                if (cursor > matcher.start()) {
                    val diff = match.length - replacement.length
                    if (cursor >= matcher.end()) cursor -= diff
                    else cursor = matcher.start() + replacement.length
                }
            }
            matcher.appendTail(sb)
            text = sb.toString()
        }
        
        if (text.contains(":")) {
            val sb = java.lang.StringBuilder()
            var newCursor = cursor
            var i = 0
            while (i < text.length) {
                if (text[i] == ':') {
                    val maxSearch = minOf(i + 30, text.length)
                    var closingColon = -1
                    for (j in i + 1 until maxSearch) {
                        if (text[j] == ':') { closingColon = j; break }
                    }
                    if (closingColon != -1) {
                        val candidate = text.substring(i, closingColon + 1)
                        val emoji = mappings[candidate]
                        if (emoji != null) {
                            if (serverEmojis.containsKey(candidate) && !hasSupporterPerks) {
                                sb.append(text[i])
                                i++
                                continue
                            }
                            sb.append(emoji)
                            if (cursor > closingColon) {
                                newCursor -= (candidate.length - emoji.length)
                            } else if (cursor > i && cursor <= closingColon) {
                                newCursor = sb.length
                            }
                            i = closingColon + 1
                            continue
                        }
                    }
                }
                sb.append(text[i])
                i++
            }
            text = sb.toString()
            cursor = newCursor
        }
        
        if (text != original) {
            input.value = text
            input.cursorPosition = cursor
            input.setHighlightPos(cursor)
        }
    }
}
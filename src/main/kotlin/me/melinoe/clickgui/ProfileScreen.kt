package me.melinoe.clickgui

import me.melinoe.Melinoe
import me.melinoe.Melinoe.mc
import me.melinoe.features.impl.ClickGUIModule
import me.melinoe.network.Companions
import me.melinoe.network.PlayerClass
import me.melinoe.network.ProfileException
import me.melinoe.network.ProfileFetcher
import me.melinoe.network.TelosCharacter
import me.melinoe.network.TelosCharacterDetail
import me.melinoe.network.TelosProfile
import me.melinoe.utils.data.CompanionData
import me.melinoe.utils.data.SeasonPassData
import me.melinoe.utils.data.TelosItems
import me.melinoe.utils.handlers.schedule
import me.melinoe.utils.ui.animations.EaseOutAnimation
import me.melinoe.utils.ui.rendering.Gradient
import me.melinoe.utils.ui.rendering.NVGPIPRenderer
import me.melinoe.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt
import me.melinoe.utils.ui.mouseX as rawMouseX
import me.melinoe.utils.ui.mouseY as rawMouseY

/**
 * Full-screen tabbed profile viewer for Telos players, opened via /profile [username].
 *
 * Layout is a persistent left sidebar (player name + rotating 3D model) and a right pane with
 * five tabs. The Overview tab shows stat cards, a details list, the
 * season pass paginator and a sticker preview. The 3D model is submitted through the vanilla GUI
 * pass (see [ProfileModelRenderer]); everything else is drawn with NanoVG.
 */
class ProfileScreen private constructor(private val username: String) : Screen(Component.literal("Profile")) {
    
    companion object {
        /** Opens the profile screen for [username] on the render thread. */
        fun open(username: String) {
            schedule(0) { mc.setScreen(ProfileScreen(username)) }
        }
        
        // ==================== PALETTE (0xAARRGGBB) ====================
        private val BACKDROP = 0xC80A0A0C.toInt()
        private val CARD = 0xFF141418.toInt()
        private val PANEL = 0xFF1C1C22.toInt()
        private val PANEL_HI = 0xFF24242C.toInt()
        private val STROKE = 0xFF2C2C34.toInt()
        private val ACCENT = 0xFF7CFFB2.toInt()
        private val ACCENT_DIM = 0xFF2E8F78.toInt()
        private val TEXT = 0xFFFFFFFF.toInt()
        private val SUBTEXT = 0xFFB6B6C0.toInt()
        private val MUTE = 0xFF6E6E78.toInt()
        private val GOLD = 0xFFFFD700.toInt()
        private val SEASONAL = 0xFFFF7CCB.toInt()
        private val ERROR = 0xFFFF5555.toInt()
        
        private const val PAD = 16f
        private const val TILE = 30f
        // Larger tiles for the companions page so they fill more of the panel.
        private const val COMP_TILE = 46f
        private const val COMP_GAP = 8f
    }
    
    private enum class Tab(val label: String) {
        OVERVIEW("Overview"),
        TRANSCENDENCE("Transcendence"),
        CHARACTERS("Characters"),
        COMPANIONS("Companions"),
        STASH("Stash")
    }
    
    @Volatile private var profile: TelosProfile? = null
    @Volatile private var errorMsg: String? = null
    private var fetchStarted = false
    
    private val openAnim = EaseOutAnimation(350)
    private var tab = Tab.OVERVIEW
    
    // Per-tab scroll state and last measured content heights.
    private val scroll = FloatArray(Tab.entries.size)
    private val contentHeight = FloatArray(Tab.entries.size)
    
    // Derived/cached view data (built once the profile arrives).
    private var equipped: List<EquippedCompanion> = emptyList()
    private var unlockedIds: Set<String> = emptySet()
    private var equippedPetId: String? = null
    private var equippedMountId: String? = null
    private var equippedPetSkin: String? = null
    private var equippedMountSkin: String? = null
    private var unlockedPetCount = 0
    private var unlockedMountCount = 0
    private var maxedClasses = 0
    private var stashGroups: List<Pair<String, List<TelosItems.Resolved?>>> = emptyList()
    private var stashCount = 0
    private var classesSorted: List<PlayerClass> = emptyList()
    private var totalFame = 0L
    private var totalDeaths = 0L
    // Overview stat cards, formatted once when the profile loads (label, value, color).
    private var overviewCards: List<Triple<String, String, Int>> = emptyList()
    
    // Characters tab (fetched separately once the profile arrives).
    @Volatile private var characterList: List<TelosCharacter>? = null
    private var equippedCharacterId: String? = null
    private val charRects = ArrayList<Pair<FloatArray, String?>>()
    
    // Character detail view (opened by clicking a character row).
    private enum class DetailTab(val label: String) { OVERVIEW("Overview"), SKILLS("Skill Tree"), BACKPACK("Backpack") }
    private var viewingCharacterId: String? = null
    @Volatile private var characterDetail: TelosCharacterDetail? = null
    @Volatile private var detailError: String? = null
    @Volatile private var detailLoading = false
    private var detailTab = DetailTab.OVERVIEW
    // Cache fetched character details so re-opening a character is instant (written off-thread).
    private val characterDetailCache = java.util.concurrent.ConcurrentHashMap<String, TelosCharacterDetail>()
    private val detailTabRects = arrayOfNulls<FloatArray>(DetailTab.entries.size)
    private val detailScroll = FloatArray(DetailTab.entries.size)
    private val detailContentHeight = FloatArray(DetailTab.entries.size)
    private var backRect: FloatArray? = null
    
    private data class EquippedCompanion(val title: String, val item: TelosItems.Resolved, val level: String?, val skin: String?)
    
    // Season pass UI state.
    private var seasonPage = 0
    
    // Frame-local input state (NanoVG virtual coordinates).
    private var sx = 0f
    private var sy = 0f
    private var scaleNow = 1f
    private val tabRects = arrayOfNulls<FloatArray>(Tab.entries.size)
    private var bodyTop = 0f
    private var bodyBottom = 0f
    private var curScroll = 0f
    private var tooltip: Pair<String, Int>? = null
    
    // Screen-space (scroll-adjusted) season pass paginator hitboxes.
    private var spLeftRect: FloatArray? = null
    private var spRightRect: FloatArray? = null
    
    // Panel + sidebar geometry, recomputed each frame.
    private var panelX = 0f
    private var panelY = 0f
    private var panelW = 0f
    private var panelH = 0f
    private var sidebarW = 0f
    private var modelBoxX = 0f
    private var modelBoxY = 0f
    private var modelBoxW = 0f
    private var modelBoxH = 0f
    private var tabBarY = 0f
    private var tabBarH = 30f
    private var contentX = 0f
    private var contentY = 0f
    private var contentW = 0f
    private var contentH = 0f
    
    private val font get() = NVGRenderer.defaultFont
    
    override fun init() {
        openAnim.start()
        if (!fetchStarted) {
            fetchStarted = true
            ProfileFetcher.fetch(username).whenComplete { result, error ->
                if (error != null) {
                    val cause = error.cause ?: error
                    errorMsg = if (cause is ProfileException) cause.message else "Failed to fetch profile: ${cause.message}"
                    Melinoe.logger.error("[ProfileScreen] fetch failed for $username", cause)
                } else {
                    buildDerived(result)
                    profile = result
                    fetchCharacters(result)
                }
            }
        }
        super.init()
    }
    
    private fun fetchCharacters(p: TelosProfile) {
        val uuid = p.id ?: return
        equippedCharacterId = p.character
        ProfileFetcher.fetchCharacters(uuid).whenComplete { result, error ->
            if (error != null) {
                Melinoe.logger.error("[ProfileScreen] character fetch failed for $uuid", error.cause ?: error)
                characterList = emptyList()
            } else {
                // Only living characters; equipped first, then by fame.
                characterList = result
                    .filter { it.deathTime == null }
                    .sortedWith(compareByDescending<TelosCharacter> { it.id == equippedCharacterId }.thenByDescending { it.fame })
            }
        }
    }
    
    /** Opens the detail view for a character, fetching its full data (cached) with a loading state. */
    private fun openCharacter(id: String) {
        viewingCharacterId = id
        detailError = null
        detailTab = DetailTab.OVERVIEW
        detailScroll.fill(0f)
        
        val cached = characterDetailCache[id]
        if (cached != null) {
            characterDetail = cached
            detailLoading = false
            return
        }
        
        characterDetail = null
        detailLoading = true
        ProfileFetcher.fetchCharacter(id).whenComplete { result, error ->
            detailLoading = false
            if (error != null) {
                val cause = error.cause ?: error
                detailError = if (cause is ProfileException) cause.message else "Failed to load character."
                Melinoe.logger.error("[ProfileScreen] character detail fetch failed for $id", cause)
            } else {
                characterDetailCache[id] = result
                characterDetail = result
            }
        }
    }
    
    private fun closeCharacter() {
        viewingCharacterId = null
        characterDetail = null
        detailError = null
    }
    
    private fun buildDerived(p: TelosProfile) {
        // Companions
        val c: Companions? = p.companions
        if (c != null) {
            val list = mutableListOf<EquippedCompanion>()
            c.pet?.let { list.add(EquippedCompanion("Pet", TelosItems.resolve(it), it.substringAfter('/', "").ifEmpty { null }, prettySkin(c.petSkin))) }
            c.mount?.let { list.add(EquippedCompanion("Mount", TelosItems.resolve(it), it.substringAfter('/', "").ifEmpty { null }, prettySkin(c.mountSkin))) }
            equipped = list
            
            // Equipped companion ids, resolving the rank-specific starter variant from the "/N" suffix.
            equippedPetId = c.pet?.let(::equippedVariantId)
            equippedMountId = c.mount?.let(::equippedVariantId)
            equippedPetSkin = c.petSkin?.ifBlank { null }
            equippedMountSkin = c.mountSkin?.ifBlank { null }
            
            val ids = (c.unlocked ?: emptyList()).map { it.substringBefore('/') }.toMutableSet()
            equippedPetId?.let { ids.add(it) }
            equippedMountId?.let { ids.add(it) }
            unlockedIds = ids
            unlockedPetCount = ids.count { it.startsWith("pet-") }
            unlockedMountCount = ids.count { it.startsWith("mount-") }
        }
        
        // Stash: keep the full slot layout (with empty slots) for any page that has at least 1 item.
        val groups = mutableListOf<Pair<String, List<TelosItems.Resolved?>>>()
        var count = 0
        p.stash?.forEach { page ->
            val slots = page.items?.map { it?.key?.let { key -> TelosItems.resolve(key) } } ?: emptyList()
            val filled = slots.count { it != null }
            if (filled > 0) {
                count += filled
                val label = (if (page.seasonal) "Seasonal" else "Normal") + " — Page ${page.page + 1}"
                groups.add(label to slots)
            }
        }
        stashGroups = groups
        stashCount = count
        
        // Classes
        classesSorted = (p.classes ?: emptyList()).sortedByDescending { it.totalFame }
        totalFame = classesSorted.sumOf { it.totalFame }
        totalDeaths = classesSorted.sumOf { it.deaths.toLong() }
        maxedClasses = classesSorted.count { it.soulPoints >= maxSoulPointsPerClass }
        
        overviewCards = listOf(
            Triple("Playtime", duration(p.playTime), TEXT),
            Triple("Glory", commas(p.normalBalance), GOLD),
            Triple("Seasonal Glory", commas(p.seasonalBalance), SEASONAL),
            Triple("Total Fame", compact(totalFame), ACCENT),
            Triple("Deaths", commas(totalDeaths), TEXT),
            Triple("Classes", classesSorted.size.toString(), TEXT)
        )
    }
    
    /**
     * Maps an equipped companion key like "pet-starter/6" to the id of the variant it represents.
     * For starters the "/N" suffix is the rank (-> "pet-starter6"); other companions ignore it.
     */
    private fun equippedVariantId(raw: String): String {
        val base = raw.substringBefore('/')
        if (CompanionData.starterRank(base) == null) return base
        val rank = raw.substringAfter('/', "0").toIntOrNull() ?: 0
        return if (rank <= 0) base.removeSuffix("0") else "${base}$rank"
    }
    
    /** Whether a catalogue companion is unlocked (starters use the maxed-class rank rule). */
    private fun isCompanionUnlocked(id: String): Boolean {
        val rank = CompanionData.starterRank(id)
        return if (rank != null) rank <= maxedClasses else unlockedIds.contains(id)
    }
    
    /** Converts an equipped skin id ("skinpet-raaah") to its companion id ("pet-raaah"), or null. */
    private fun skinToCompanionId(skin: String): String? = when {
        skin.startsWith("skinpet-") -> "pet-" + skin.removePrefix("skinpet-")
        skin.startsWith("skinmount-") -> "mount-" + skin.removePrefix("skinmount-")
        else -> null
    }
    
    // ==================== LAYOUT ====================
    
    private fun computeLayout(vW: Float, vH: Float, anim: Float) {
        panelW = min(vW - 40f, 1060f).coerceAtLeast(520f)
        panelH = min(vH - 36f, 640f).coerceAtLeast(380f)
        panelX = (vW - panelW) / 2f
        panelY = (vH - panelH) / 2f + (1f - anim) * 12f
        
        sidebarW = (panelW * 0.255f).coerceIn(176f, 290f)
        
        val sbX = panelX + PAD
        val nameBoxY = panelY + PAD
        val nameBoxH = 44f
        
        modelBoxX = sbX
        modelBoxY = nameBoxY + nameBoxH + 10f
        modelBoxW = (panelX + sidebarW - 6f) - sbX
        modelBoxH = (panelY + panelH - PAD) - modelBoxY
        
        contentX = panelX + sidebarW + PAD
        val mainRight = panelX + panelW - PAD
        contentW = mainRight - contentX
        tabBarY = panelY + PAD
        contentY = tabBarY + tabBarH + 10f
        contentH = (panelY + panelH - PAD) - contentY
    }
    
    // ==================== RENDER ====================
    
    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val scale = ClickGUIModule.getStandardGuiScale()
        scaleNow = scale
        sx = rawMouseX / scale
        sy = rawMouseY / scale
        
        val vW = mc.window.screenWidth / scale
        val vH = mc.window.screenHeight / scale
        val anim = openAnim.get(0f, 1f)
        
        computeLayout(vW, vH, anim)
        
        NVGPIPRenderer.draw(context, 0, 0, context.guiWidth(), context.guiHeight()) {
            NVGRenderer.scale(scale, scale)
            NVGRenderer.rect(0f, 0f, vW, vH, BACKDROP)
            
            if (openAnim.isAnimating()) NVGRenderer.globalAlpha(anim)
            
            tooltip = null
            drawProfile()
            tooltip?.let { drawTooltip(it.first, it.second) }
            
            if (openAnim.isAnimating()) NVGRenderer.globalAlpha(1f)
        }
        
        // Player model is submitted through the vanilla GUI pass, on top of the NVG sidebar panel.
        submitModel(context, mouseX.toFloat(), mouseY.toFloat())
        
        super.extractRenderState(context, mouseX, mouseY, deltaTicks)
    }
    
    /** Submits the rotating 3D avatar, converting the model box from NVG to GUI coordinates. */
    private fun submitModel(context: GuiGraphicsExtractor, mouseX: Float, mouseY: Float) {
        val p = profile ?: return
        if (modelBoxW <= 0f || modelBoxH <= 0f) return
        val avatar = ProfileModelRenderer.avatarFor(p.id, p.username ?: username) ?: return
        
        val fx = scaleNow * context.guiWidth() / mc.window.screenWidth.toFloat()
        val fy = scaleNow * context.guiHeight() / mc.window.screenHeight.toFloat()
        
        // Leave headroom so the model sits inside the panel rather than touching the edges.
        val inset = 8f
        val bx = modelBoxX + inset
        val by = modelBoxY + inset
        val bw = modelBoxW - inset * 2
        val bh = modelBoxH - inset * 2
        
        val x0 = (bx * fx).toInt()
        val y0 = (by * fy).toInt()
        val x1 = ((bx + bw) * fx).toInt()
        val y1 = ((by + bh) * fy).toInt()
        val sizeScale = (bh * fy * 0.40f).toInt().coerceAtLeast(8)
        
        ProfileModelRenderer.render(context, avatar, x0, y0, x1, y1, sizeScale, mouseX, mouseY)
    }
    
    private fun drawProfile() {
        NVGRenderer.dropShadow(panelX, panelY, panelW, panelH, 26f, 2f, 16f)
        NVGRenderer.rect(panelX, panelY, panelW, panelH, CARD, 16f)
        NVGRenderer.hollowRect(panelX, panelY, panelW, panelH, 1f, STROKE, 16f)
        
        val p = profile
        when {
            errorMsg != null -> drawCentered(errorMsg!!, ERROR)
            p == null -> drawCentered("Fetching $username${dots()}", SUBTEXT)
            else -> {
                drawSidebar(p)
                if (viewingCharacterId != null) {
                    drawCharacterDetail()
                } else {
                    drawTabs()
                    drawContent(p)
                }
            }
        }
    }
    
    private fun drawCentered(text: String, color: Int) {
        val w = NVGRenderer.textWidth(text, 13f, font)
        NVGRenderer.text(text, panelX + (panelW - w) / 2f, panelY + panelH / 2f - 6f, 13f, color, font)
    }
    
    private fun drawSidebar(p: TelosProfile) {
        val sbX = panelX + PAD
        val name = p.username ?: username
        val colRight = panelX + sidebarW
        
        // Name plate.
        val nameY = panelY + PAD
        NVGRenderer.rect(sbX, nameY, modelBoxW, 44f, PANEL, 10f)
        val nameSize = fitText(name, modelBoxW - 16f, 18f, 12f)
        val nameW = NVGRenderer.textWidth(name, nameSize, font)
        NVGRenderer.text(name, sbX + (modelBoxW - nameW) / 2f, nameY + (44f - nameSize) / 2f, nameSize, ACCENT, font)
        
        // Model backdrop (the 3D model draws on top in the GUI pass).
        NVGRenderer.gradientRect(modelBoxX, modelBoxY, modelBoxW, modelBoxH, PANEL_HI, PANEL, Gradient.TopToBottom, 10f)
        NVGRenderer.hollowRect(modelBoxX, modelBoxY, modelBoxW, modelBoxH, 1f, STROKE, 10f)
        
        // Sidebar / main divider.
        NVGRenderer.line(colRight, panelY + PAD, colRight, panelY + panelH - PAD, 1f, STROKE)
    }
    
    private fun drawTabs() {
        val n = Tab.entries.size
        val gap = 6f
        val tw = (contentW - gap * (n - 1)) / n
        Tab.entries.forEachIndexed { i, t ->
            val tabX = contentX + (tw + gap) * i
            val active = t == tab
            val hovered = hover(tabX, tabBarY, tw, tabBarH)
            tabRects[i] = floatArrayOf(tabX, tabBarY, tw, tabBarH)
            
            val bg = if (active) PANEL_HI else if (hovered) PANEL else CARD
            NVGRenderer.rect(tabX, tabBarY, tw, tabBarH, bg, 8f)
            if (active) NVGRenderer.hollowRect(tabX, tabBarY, tw, tabBarH, 1f, ACCENT_DIM, 8f)
            
            val color = if (active) ACCENT else if (hovered) TEXT else SUBTEXT
            val size = fitText(t.label, tw - 8f, 11.5f, 8.5f)
            val lx = tabX + (tw - NVGRenderer.textWidth(t.label, size, font)) / 2f
            NVGRenderer.text(t.label, lx, tabBarY + (tabBarH - size) / 2f, size, color, font)
        }
    }
    
    private fun drawContent(p: TelosProfile) {
        bodyTop = contentY
        bodyBottom = contentY + contentH
        curScroll = scroll[tab.ordinal]
        
        NVGRenderer.pushScissor(contentX, contentY, contentW, contentH)
        NVGRenderer.push()
        NVGRenderer.translate(0f, -curScroll)
        
        val used = when (tab) {
            Tab.OVERVIEW -> drawOverview(contentX, contentY, contentW, p)
            Tab.TRANSCENDENCE -> drawTranscendence(contentX, contentY, contentW)
            Tab.CHARACTERS -> drawCharacters(contentX, contentY, contentW)
            Tab.COMPANIONS -> drawCompanions(contentX, contentY, contentW)
            Tab.STASH -> drawStash(contentX, contentY, contentW)
        }
        
        NVGRenderer.pop()
        NVGRenderer.popScissor()
        
        contentHeight[tab.ordinal] = used
        scroll[tab.ordinal] = scroll[tab.ordinal].coerceIn(0f, max(0f, used - contentH))
        
        if (used > contentH) {
            val thumbH = max(24f, contentH * (contentH / used))
            val thumbY = contentY + (contentH - thumbH) * (scroll[tab.ordinal] / (used - contentH))
            NVGRenderer.rect(panelX + panelW - 6f, thumbY, 3f, thumbH, STROKE, 1.5f)
        }
    }
    
    // ==================== OVERVIEW ====================
    
    private fun drawOverview(bx: Float, by: Float, bw: Float, p: TelosProfile): Float {
        var cy = by
        
        // Stat cards (3 per row, 2 rows), formatted once in buildDerived.
        val cols = 3
        val gap = 8f
        val cardW = (bw - gap * (cols - 1)) / cols
        val cardH = 50f
        overviewCards.forEachIndexed { i, (label, value, color) ->
            val cx = bx + (i % cols) * (cardW + gap)
            val ry = cy + (i / cols) * (cardH + gap)
            NVGRenderer.rect(cx, ry, cardW, cardH, PANEL, 8f)
            NVGRenderer.text(label.uppercase(), cx + 10f, ry + 8f, 8f, MUTE, font)
            NVGRenderer.text(value, cx + 10f, ry + 22f, 15f, color, font)
        }
        cy += 2 * (cardH + gap) + 8f
        
        // Other details.
        NVGRenderer.text("Other details", bx + 2f, cy, 11f, MUTE, font)
        cy += 18f
        val passLabel = p.seasonPass?.let { (if (it.premium) "Premium" else "Free") + "  •  ${compact(it.experience)} XP" } ?: "—"
        val passColor = if (p.seasonPass?.premium == true) GOLD else SUBTEXT
        cy = detailRow(bx, cy, bw, "Last Seen", relativeTime(p.lastPlayed), TEXT)
        cy = detailRow(bx, cy, bw, "Season Pass", passLabel, passColor)
        cy = detailRow(bx, cy, bw, "Rewards", "${p.rewards?.size ?: 0} unlocked", TEXT)
        cy = detailRow(bx, cy, bw, "Companions", "$unlockedPetCount pets  •  $unlockedMountCount mounts", TEXT)
        cy = detailRow(bx, cy, bw, "Stash", "$stashCount items stored", TEXT)
        cy = detailRow(bx, cy, bw, "Character Slots", "${p.characterSlots}", TEXT)
        cy += 8f
        
        // Season pass + sticker preview.
        val previewW = (bw * 0.34f).coerceIn(150f, 240f)
        val spW = bw - previewW - 12f
        val sectionH = seasonSectionHeight(spW)
        drawSeasonPass(bx, cy, spW, sectionH, p)
        drawStickerPreview(bx + spW + 12f, cy, previewW, sectionH, p)
        cy += sectionH
        
        return cy - by
    }
    
    private fun detailRow(bx: Float, y: Float, bw: Float, label: String, value: String, valueColor: Int): Float {
        val h = 22f
        NVGRenderer.text(label, bx + 2f, y + 5f, 11f, SUBTEXT, font)
        NVGRenderer.text(value, bx + bw - 2f - NVGRenderer.textWidth(value, 11f, font), y + 5f, 11f, valueColor, font)
        NVGRenderer.line(bx + 2f, y + h - 0.5f, bx + bw - 2f, y + h - 0.5f, 0.5f, 0xFF202028.toInt())
        return y + h
    }
    
    /** Height of the season pass box for a given width (header + 2-row grid + paginator). */
    private fun seasonSectionHeight(spW: Float): Float {
        val tile = seasonTileSize(spW)
        return 26f + (2 * tile + 8f) + 40f
    }
    
    private fun seasonTileSize(spW: Float): Float {
        val inner = spW - 12f * 2
        return (inner - 8f * 4) / 5f
    }
    
    private fun drawSeasonPass(x: Float, y: Float, w: Float, h: Float, p: TelosProfile) {
        NVGRenderer.rect(x, y, w, h, PANEL, 10f)
        NVGRenderer.hollowRect(x, y, w, h, 1f, STROKE, 10f)
        
        val xp = p.seasonPass?.experience ?: 0L
        val premium = p.seasonPass?.premium ?: false
        
        NVGRenderer.text("SEASON PASS", x + 12f, y + 9f, 9f, MUTE, font)
        val passTag = if (premium) "Premium" else "Free"
        NVGRenderer.text(passTag, x + w - 12f - NVGRenderer.textWidth(passTag, 9f, font), y + 9f, 9f, if (premium) GOLD else SUBTEXT, font)
        
        val tile = seasonTileSize(w)
        val gridTop = y + 26f
        val page = SeasonPassData.page(seasonPage)
        
        for (i in 0 until SeasonPassData.PER_PAGE) {
            val col = i % 5
            val row = i / 5
            val tx = x + 12f + col * (tile + 8f)
            val ty = gridTop + row * (tile + 8f)
            val reward = page.getOrNull(i)
            if (reward == null) {
                NVGRenderer.rect(tx, ty, tile, tile, 0xFF15151A.toInt(), 6f)
                continue
            }
            drawSeasonTile(reward, tx, ty, tile, xp, premium)
        }
        
        // Paginator: arrows + page/xp/premium info.
        val arrowY = gridTop + 2 * (tile + 8f) + 4f
        val arrowSize = 22f
        val leftX = x + 12f
        val rightX = x + w - 12f - arrowSize
        drawArrow(leftX, arrowY, arrowSize, false, hover(leftX, arrowY - curScroll, arrowSize, arrowSize))
        drawArrow(rightX, arrowY, arrowSize, true, hover(rightX, arrowY - curScroll, arrowSize, arrowSize))
        spLeftRect = floatArrayOf(leftX, arrowY - curScroll, arrowSize, arrowSize)
        spRightRect = floatArrayOf(rightX, arrowY - curScroll, arrowSize, arrowSize)
        
        val info = "Page ${seasonPage + 1}/${SeasonPassData.PAGES}   •   ${compact(xp)} XP   •   $passTag"
        val infoW = NVGRenderer.textWidth(info, 10f, font)
        NVGRenderer.text(info, x + (w - infoW) / 2f, arrowY + (arrowSize - 10f) / 2f, 10f, SUBTEXT, font)
    }
    
    private fun drawSeasonTile(reward: SeasonPassData.Reward, tx: Float, ty: Float, tile: Float, xp: Long, premium: Boolean) {
        val unlocked = SeasonPassData.isUnlocked(reward, xp, premium)
        val accent = if (reward.premium) GOLD else ACCENT
        val border = if (unlocked) blend(accent, STROKE, 0.7f) else STROKE
        
        NVGRenderer.rect(tx, ty, tile, tile, blend(accent, 0xFF15151A.toInt(), if (unlocked) 0.18f else 0.06f), 6f)
        NVGRenderer.hollowRect(tx, ty, tile, tile, 1f, border, 6f)
        
        val pad = 4f
        if (!NVGRenderer.texturedRect(reward.texture, tx + pad, ty + pad, tile - pad * 2, tile - pad * 2)) {
            val initial = reward.displayName.firstOrNull()?.uppercase() ?: "?"
            val s = (tile - pad * 2) * 0.7f
            NVGRenderer.text(initial, tx + (tile - NVGRenderer.textWidth(initial, s, font)) / 2f, ty + tile * 0.16f, s, accent, font)
        }
        
        if (!unlocked) NVGRenderer.rect(tx, ty, tile, tile, 0x99101014.toInt(), 6f)
        
        if (visibleHover(tx, ty, tile, tile)) {
            NVGRenderer.hollowRect(tx, ty, tile, tile, 1.5f, accent, 6f)
            val req = "${reward.displayName}  •  Tier ${reward.tier}  •  ${if (unlocked) "Unlocked" else "${compact(reward.xpRequired)} XP"}"
            tooltip = req to accent
        }
    }
    
    private fun drawArrow(x: Float, y: Float, size: Float, right: Boolean, hovered: Boolean) {
        val color = if (hovered) ACCENT else SUBTEXT
        NVGRenderer.rect(x, y, size, size, if (hovered) PANEL_HI else PANEL, 6f)
        val glyph = if (right) "›" else "‹"
        val gs = 16f
        NVGRenderer.text(glyph, x + (size - NVGRenderer.textWidth(glyph, gs, font)) / 2f, y + (size - gs) / 2f - 1f, gs, color, font)
    }
    
    /** Bottom-right panel: the player's currently equipped sticker (name + large texture). */
    private fun drawStickerPreview(x: Float, y: Float, w: Float, h: Float, p: TelosProfile) {
        NVGRenderer.rect(x, y, w, h, PANEL, 10f)
        NVGRenderer.hollowRect(x, y, w, h, 1f, STROKE, 10f)
        
        val raw = p.sticker
        if (raw.isNullOrBlank()) {
            val hint = "No sticker equipped"
            NVGRenderer.text(hint, x + (w - NVGRenderer.textWidth(hint, 10f, font)) / 2f, y + h / 2f - 5f, 10f, MUTE, font)
            return
        }
        
        val id = raw.substringAfter(':', raw)
        val name = title(id)
        
        // Name + underline.
        val nameSize = fitText(name, w - 20f, 13f, 9f)
        val nameW = NVGRenderer.textWidth(name, nameSize, font)
        val nameX = x + (w - nameW) / 2f
        NVGRenderer.text(name, nameX, y + 12f, nameSize, TEXT, font)
        NVGRenderer.line(nameX, y + 12f + nameSize + 2f, nameX + nameW, y + 12f + nameSize + 2f, 1f, ACCENT_DIM)
        
        // Large sticker texture.
        val imgSize = min(w - 36f, h - 56f).coerceAtLeast(24f)
        val imgX = x + (w - imgSize) / 2f
        val imgY = y + 38f
        if (!NVGRenderer.texturedRect("telos:material/sticker/${id}.png", imgX, imgY, imgSize, imgSize)) {
            NVGRenderer.rect(imgX, imgY, imgSize, imgSize, 0xFF15151A.toInt(), 8f)
            val initial = name.firstOrNull()?.uppercase() ?: "?"
            NVGRenderer.text(initial, imgX + (imgSize - NVGRenderer.textWidth(initial, imgSize * 0.5f, font)) / 2f, imgY + imgSize * 0.22f, imgSize * 0.5f, MUTE, font)
        }
    }
    
    // ==================== TRANSCENDENCE ====================
    
    /** Soul point cap per class and across all six classes. */
    private val maxSoulPointsPerClass = 3_000_000L
    private val maxSoulPointsTotal = 18_000_000L
    
    /** Fixed class order so each archetype always occupies the same grid slot. */
    private val classOrder = listOf("KNIGHT", "ASSASSIN", "NECROMANCER", "SAMURAI", "HUNTRESS", "ARCANIST")
    
    private fun drawTranscendence(bx: Float, by: Float, bw: Float): Float {
        var cy = by
        
        if (classesSorted.isEmpty()) {
            return drawEmptyState(bx, by, bw, "Transcendence", "No class data.")
        }
        
        // Header: total soul points across all classes out of the cap.
        val totalSp = classesSorted.sumOf { it.soulPoints }
        NVGRenderer.text("Total Soul Points", bx + 2f, cy, 11f, SUBTEXT, font)
        val totalLabel = "${commas(totalSp)} / ${commas(maxSoulPointsTotal)}"
        NVGRenderer.text(totalLabel, bx + bw - 2f - NVGRenderer.textWidth(totalLabel, 11f, font), cy, 11f, ACCENT, font)
        cy += 18f
        val barH = 8f
        NVGRenderer.rect(bx + 2f, cy, bw - 4f, barH, PANEL_HI, 4f)
        val totalFrac = (totalSp.toDouble() / maxSoulPointsTotal).coerceIn(0.0, 1.0).toFloat()
        if (totalFrac > 0f) NVGRenderer.gradientRect(bx + 2f, cy, (bw - 4f) * totalFrac, barH, ACCENT, ACCENT_DIM, Gradient.LeftToRight, 4f)
        cy += barH + 14f
        
        // Grid of class cards - 3x2 when there's width for it, otherwise 2x3.
        val byType = classesSorted.associateBy { it.type?.uppercase() }
        val gap = 10f
        val cols = if ((bw - gap * 2) / 3f >= 200f) 3 else 2
        val rows = if (cols == 3) 2 else 3
        val cardW = (bw - gap * (cols - 1)) / cols
        val gridTop = cy
        val avail = (by + contentH) - gridTop - gap * (rows - 1)
        val cardH = (avail / rows).coerceAtLeast(212f)
        
        classOrder.forEachIndexed { i, type ->
            val cx = bx + (i % cols) * (cardW + gap)
            val ry = gridTop + (i / cols) * (cardH + gap)
            drawClassCard(cx, ry, cardW, cardH, type, byType[type])
        }
        
        return (gridTop + rows * cardH + (rows - 1) * gap) - by
    }
    
    private fun drawClassCard(x: Float, y: Float, w: Float, h: Float, type: String, c: PlayerClass?) {
        NVGRenderer.rect(x, y, w, h, PANEL, 10f)
        NVGRenderer.hollowRect(x, y, w, h, 1f, STROKE, 10f)
        
        val pad = 12f
        val name = title(type)
        NVGRenderer.text(name, x + pad, y + 11f, 14f, TEXT, font)
        
        if (c == null) {
            NVGRenderer.text("No data", x + pad, y + h / 2f - 5f, 11f, MUTE, font)
            return
        }
        
        // Transcendence level badge (top-right).
        val badge = "Transcendence ${c.transcendenceLevel}/10"
        val badgeW = NVGRenderer.textWidth(badge, 9f, font) + 12f
        val maxed = c.transcendenceLevel >= 10
        NVGRenderer.rect(x + w - pad - badgeW, y + 10f, badgeW, 15f, if (maxed) GOLD else ACCENT_DIM, 7f)
        NVGRenderer.text(badge, x + w - pad - badgeW + 6f, y + 12.5f, 9f, 0xFF0A0A0C.toInt(), font)
        
        // Class portrait (sprite frame chosen by soul points) fills the space above the stats.
        val bottomH = 100f
        val portraitTop = y + 32f
        val portraitH = ((y + h - 12f - bottomH) - portraitTop).coerceAtLeast(44f)
        drawClassPortrait(type, c.soulPoints, x + w / 2f, portraitTop, portraitH)
        
        // Soul points progress toward the per-class cap.
        var cy = portraitTop + portraitH + 10f
        NVGRenderer.text("Soul Points", x + pad, cy, 9f, MUTE, font)
        cy += 13f
        val barH = 6f
        val barW = w - pad * 2
        NVGRenderer.rect(x + pad, cy, barW, barH, PANEL_HI, 3f)
        val frac = (c.soulPoints.toDouble() / maxSoulPointsPerClass).coerceIn(0.0, 1.0).toFloat()
        if (frac > 0f) NVGRenderer.gradientRect(x + pad, cy, barW * frac, barH, ACCENT, ACCENT_DIM, Gradient.LeftToRight, 3f)
        cy += barH + 4f
        val spText = "${commas(c.soulPoints)} / ${commas(maxSoulPointsPerClass)}"
        NVGRenderer.text(spText, x + pad, cy, 9f, SUBTEXT, font)
        val toMax = (maxSoulPointsPerClass - c.soulPoints).coerceAtLeast(0L)
        val toMaxText = if (toMax == 0L) "Maxed" else "${compact(toMax)} to max"
        NVGRenderer.text(toMaxText, x + w - pad - NVGRenderer.textWidth(toMaxText, 9f, font), cy, 9f, if (toMax == 0L) GOLD else MUTE, font)
        cy += 16f
        
        // Remaining stat rows.
        cy = classStat(x + pad, cy, w - pad * 2, "Playtime", duration(c.playTime), TEXT)
        cy = classStat(x + pad, cy, w - pad * 2, "Highest Fame", compact(c.maxFame), ACCENT)
        cy = classStat(x + pad, cy, w - pad * 2, "Total Fame", compact(c.totalFame), TEXT)
        classStat(x + pad, cy, w - pad * 2, "Deaths", commas(c.deaths.toLong()), TEXT)
    }
    
    private fun classStat(x: Float, y: Float, w: Float, label: String, value: String, valueColor: Int): Float {
        NVGRenderer.text(label, x, y, 10f, SUBTEXT, font)
        NVGRenderer.text(value, x + w - NVGRenderer.textWidth(value, 10f, font), y, 10f, valueColor, font)
        return y + 15f
    }
    
    /**
     * Draws the class portrait, centred at [centerX] starting at [topY] with height [h]. The sprite
     * sheet has four 16x32 frames; the frame shown steps up with soul points (<1M, <2M, <3M, maxed).
     */
    private fun drawClassPortrait(type: String, soulPoints: Long, centerX: Float, topY: Float, h: Float) {
        val frame = when {
            soulPoints < 1_000_000L -> 0
            soulPoints < 2_000_000L -> 1
            soulPoints < maxSoulPointsPerClass -> 2
            else -> 3
        }
        val w = h * 2f
        NVGRenderer.texturedSubRect(
            "melinoe:profile/${type.lowercase()}_profile.png",
            centerX - w / 2f, topY, w, h,
            frame / 4f, 0f, (frame + 1) / 4f, 1f
        )
    }
    
    /** Draws a profile sprite [frame] (0-3), centred at [centerX], starting at [topY], height [h]. */
    private fun drawProfileSprite(type: String, frame: Int, centerX: Float, topY: Float, h: Float) {
        val w = h * 2f
        NVGRenderer.texturedSubRect(
            "melinoe:profile/${type.lowercase()}_profile.png",
            centerX - w / 2f, topY, w, h,
            frame / 4f, 0f, (frame + 1) / 4f, 1f
        )
    }
    
    // ==================== CHARACTERS ====================
    
    /** Character level from fame: fame = floor(37.5 * (level-1)^2)  ->  level = floor(1 + sqrt(fame/37.5)). */
    private fun characterLevel(fame: Long): Int = floor(1.0 + sqrt(fame / 37.5)).toInt()
    
    /** Sprite frame for a character's level: <50, <100, <200, then maxed. */
    private fun levelFrame(level: Int): Int = when {
        level < 50 -> 0
        level < 100 -> 1
        level < 200 -> 2
        else -> 3
    }
    
    private fun rulesetLabel(ch: TelosCharacter): String = rulesetLabelOf(ch.ruleset, ch.group)
    
    /** Maps a ruleset string (e.g. "GROUP_HARDCORE_IRONMAN") + group to a readable mode label. */
    private fun rulesetLabelOf(ruleset: String?, group: Any?): String {
        val r = ruleset?.uppercase().orEmpty()
        return when {
            "IRONMAN" in r && "GROUP" in r -> "Group Ironman"
            "IRONMAN" in r -> "Ironman"
            "SEASONAL" in r -> "Seasonal"
            group != null -> "Group Ironman"
            r == "DEFAULT" || r.isEmpty() -> "Normal"
            else -> title(ruleset!!)
        }
    }
    
    private fun rulesetColor(label: String): Int = when (label) {
        "Seasonal" -> SEASONAL
        "Ironman", "Group Ironman" -> 0xFFB3590E.toInt()
        else -> SUBTEXT
    }
    
    private fun drawCharacters(bx: Float, by: Float, bw: Float): Float {
        val chars = characterList
        if (chars == null) return drawEmptyState(bx, by, bw, "Characters", "Loading characters${dots()}")
        if (chars.isEmpty()) return drawEmptyState(bx, by, bw, "Characters", "No living characters.")
        
        var cy = by
        val rowH = 60f
        charRects.clear()
        chars.forEach { ch ->
            drawCharacterRow(bx, cy, bw, rowH, ch)
            charRects.add(floatArrayOf(bx, cy - curScroll, bw, rowH) to ch.id)
            cy += rowH + 8f
        }
        return cy - by
    }
    
    private fun drawCharacterRow(bx: Float, y: Float, bw: Float, h: Float, ch: TelosCharacter) {
        val equipped = ch.id == equippedCharacterId
        val hovered = visibleHover(bx, y, bw, h)
        
        NVGRenderer.rect(bx, y, bw, h, if (hovered) PANEL_HI else PANEL, 8f)
        val borderColor = if (equipped) GOLD else STROKE
        NVGRenderer.hollowRect(bx, y, bw, h, if (equipped) 1.5f else 1f, borderColor, 8f)
        
        // Portrait (frame chosen by level).
        val level = characterLevel(ch.fame)
        val portraitH = h - 14f
        val portraitW = portraitH * 2f
        drawProfileSprite(ch.type ?: "", levelFrame(level), bx + 8f + portraitW / 2f, y + 7f, portraitH)
        
        val tx = bx + 8f + portraitW + 12f
        // Line 1: class + level (+ equipped tag).
        val typeName = title(ch.type ?: "Unknown")
        NVGRenderer.text(typeName, tx, y + 10f, 14f, TEXT, font)
        var lx = tx + NVGRenderer.textWidth(typeName, 14f, font) + 8f
        val lvlText = "Lv. $level"
        NVGRenderer.text(lvlText, lx, y + 12f, 11f, ACCENT, font)
        lx += NVGRenderer.textWidth(lvlText, 11f, font) + 8f
        if (equipped) {
            val tag = "EQUIPPED"
            val tw = NVGRenderer.textWidth(tag, 8f, font) + 12f
            NVGRenderer.rect(lx, y + 10f, tw, 14f, GOLD, 7f)
            NVGRenderer.text(tag, lx + 6f, y + 12.5f, 8f, 0xFF0A0A0C.toInt(), font)
        }
        
        // Line 2: ruleset, fame, playtime.
        val label = rulesetLabel(ch)
        NVGRenderer.text(label, tx, y + 32f, 10f, rulesetColor(label), font)
        val sub = "  •  ${compact(ch.fame)} fame  •  ${duration(ch.playTime)}"
        NVGRenderer.text(sub, tx + NVGRenderer.textWidth(label, 10f, font), y + 32f, 10f, SUBTEXT, font)
        
        // Equipment on the right: helmet, chest, legs, boots, offhand, mainhand.
        val inv = ch.inventory
        val items = listOf(inv?.helmet, inv?.chestplate, inv?.leggings, inv?.boots, inv?.offHand, inv?.mainHand)
        val itemSize = h - 22f
        val itemGap = 5f
        var ix = bx + bw - 12f - (items.size * itemSize + (items.size - 1) * itemGap)
        val iy = y + (h - itemSize) / 2f
        items.forEach { item ->
            val key = item?.key
            if (key != null) drawTile(TelosItems.resolve(key), ix, iy, itemSize) else drawEmptyTile(ix, iy, itemSize)
            ix += itemSize + itemGap
        }
    }
    
    // ==================== CHARACTER DETAIL ====================
    
    /** Header (back arrow + sub-tabs) and body for a single character's detail view. */
    private fun drawCharacterDetail() {
        val gap = 6f
        val arrowW = 30f
        val mainRight = contentX + contentW
        
        // Back arrow.
        backRect = floatArrayOf(contentX, tabBarY, arrowW, tabBarH)
        val backHover = hover(contentX, tabBarY, arrowW, tabBarH)
        NVGRenderer.rect(contentX, tabBarY, arrowW, tabBarH, if (backHover) PANEL_HI else PANEL, 8f)
        val glyph = "‹"
        NVGRenderer.text(glyph, contentX + (arrowW - NVGRenderer.textWidth(glyph, 18f, font)) / 2f, tabBarY + (tabBarH - 18f) / 2f - 1f, 18f, if (backHover) ACCENT else SUBTEXT, font)
        
        // Sub-tabs fill the remaining width.
        val tabsX = contentX + arrowW + gap
        val n = DetailTab.entries.size
        val tw = (mainRight - tabsX - gap * (n - 1)) / n
        DetailTab.entries.forEachIndexed { i, t ->
            val tabX = tabsX + (tw + gap) * i
            val active = t == detailTab
            val hovered = hover(tabX, tabBarY, tw, tabBarH)
            detailTabRects[i] = floatArrayOf(tabX, tabBarY, tw, tabBarH)
            NVGRenderer.rect(tabX, tabBarY, tw, tabBarH, if (active) PANEL_HI else if (hovered) PANEL else CARD, 8f)
            if (active) NVGRenderer.hollowRect(tabX, tabBarY, tw, tabBarH, 1f, ACCENT_DIM, 8f)
            val color = if (active) ACCENT else if (hovered) TEXT else SUBTEXT
            val size = fitText(t.label, tw - 8f, 11.5f, 8.5f)
            NVGRenderer.text(t.label, tabX + (tw - NVGRenderer.textWidth(t.label, size, font)) / 2f, tabBarY + (tabBarH - size) / 2f, size, color, font)
        }
        
        val d = characterDetail
        when {
            detailLoading -> drawCenteredInContent("Loading character${dots()}", SUBTEXT)
            detailError != null -> drawCenteredInContent(detailError!!, ERROR)
            d != null -> drawDetailBody(d)
        }
    }
    
    private fun drawCenteredInContent(text: String, color: Int) {
        val w = NVGRenderer.textWidth(text, 13f, font)
        NVGRenderer.text(text, contentX + (contentW - w) / 2f, contentY + contentH / 2f - 6f, 13f, color, font)
    }
    
    private fun drawDetailBody(d: TelosCharacterDetail) {
        bodyTop = contentY
        bodyBottom = contentY + contentH
        curScroll = detailScroll[detailTab.ordinal]
        
        NVGRenderer.pushScissor(contentX, contentY, contentW, contentH)
        NVGRenderer.push()
        NVGRenderer.translate(0f, -curScroll)
        
        val used = when (detailTab) {
            DetailTab.OVERVIEW -> drawDetailOverview(contentX, contentY, contentW, d)
            DetailTab.SKILLS -> drawSkillTree(contentX, contentY, contentW, d)
            DetailTab.BACKPACK -> drawBackpack(contentX, contentY, contentW, d)
        }
        
        NVGRenderer.pop()
        NVGRenderer.popScissor()
        
        detailContentHeight[detailTab.ordinal] = used
        detailScroll[detailTab.ordinal] = detailScroll[detailTab.ordinal].coerceIn(0f, max(0f, used - contentH))
        if (used > contentH) {
            val thumbH = max(24f, contentH * (contentH / used))
            val thumbY = contentY + (contentH - thumbH) * (detailScroll[detailTab.ordinal] / (used - contentH))
            NVGRenderer.rect(panelX + panelW - 6f, thumbY, 3f, thumbH, STROKE, 1.5f)
        }
    }
    
    private fun drawDetailOverview(bx: Float, by: Float, bw: Float, d: TelosCharacterDetail): Float {
        var cy = by
        val inv = d.inventory ?: emptyList()
        fun slot(i: Int): TelosItems.Resolved? = inv.getOrNull(i)?.key?.let { TelosItems.resolve(it) }
        
        // Equipped gear.
        NVGRenderer.text("EQUIPPED", bx + 2f, cy, 9f, MUTE, font)
        cy += 15f
        val equipped = listOf(
            "Main" to slot(d.hotBarSlot), "Off" to slot(40),
            "Helmet" to slot(39), "Chest" to slot(38), "Legs" to slot(37), "Boots" to slot(36)
        )
        val eqSize = 44f
        val eqGap = 10f
        equipped.forEachIndexed { i, (label, item) ->
            val ex = bx + i * (eqSize + eqGap)
            if (item != null) drawTile(item, ex, cy, eqSize) else drawEmptyTile(ex, cy, eqSize)
            NVGRenderer.text(label, ex + (eqSize - NVGRenderer.textWidth(label, 8f, font)) / 2f, cy + eqSize + 3f, 8f, MUTE, font)
        }
        cy += eqSize + 18f
        
        // Stats in two columns.
        val t = d.type
        val level = characterLevel(d.fame)
        val rs = rulesetLabelOf(d.ruleset, d.group)
        val colGap = 16f
        val colW = (bw - colGap) / 2f
        val left = listOf(
            Triple("Level", level.toString(), ACCENT),
            Triple("Mode", rs, rulesetColor(rs)),
            Triple("Playtime", duration(d.playTime), TEXT),
            Triple("Fame", commas(d.fame), TEXT),
            Triple("Total Fame", compact(d.totalFame), TEXT)
        )
        val right = listOf(
            Triple("Highest Fame", compact(d.highestFame), ACCENT),
            Triple("Soul Points", commas(t?.soulPoints ?: 0L), TEXT),
            Triple("Transcendence", "${t?.transcendenceLevel ?: 0}/10", TEXT),
            Triple("Deaths", commas((t?.deaths ?: 0).toLong()), TEXT),
            Triple("Potions", d.potions.toString(), TEXT)
        )
        var ly = cy
        left.forEach { ly = detailRow(bx, ly, colW, it.first, it.second, it.third) }
        var ry = cy
        right.forEach { ry = detailRow(bx + colW + colGap, ry, colW, it.first, it.second, it.third) }
        cy = max(ly, ry) + 12f
        
        // Inventory grid (slots 0-35).
        NVGRenderer.text("INVENTORY", bx + 2f, cy, 9f, MUTE, font)
        cy += 15f
        cy = drawSlotGrid(bx, cy, bw, (0 until 36).map { inv.getOrNull(it) })
        return cy - by
    }
    
    /** Renders a fixed 9-column grid of item slots; empty slots are blank tiles. */
    private fun drawSlotGrid(bx: Float, startY: Float, bw: Float, slots: List<me.melinoe.network.StashItem?>): Float {
        val cols = 9
        val gap = 6f
        val tile = min(48f, (bw - gap * (cols - 1)) / cols)
        slots.forEachIndexed { i, item ->
            val tx = bx + (i % cols) * (tile + gap)
            val ty = startY + (i / cols) * (tile + gap)
            val key = item?.key
            if (key != null) drawTile(TelosItems.resolve(key), tx, ty, tile) else drawEmptyTile(tx, ty, tile)
        }
        val rows = ceil(slots.size / cols.toFloat()).toInt()
        return startY + rows * (tile + gap)
    }
    
    /**
     * Skill tree laid out as in the reference (rows of 3, 2, 3, 2, 2, 1). The selection is a path of
     * six decisions read bottom-up: decision 0 is the single start node (always 1); each later value
     * is the column taken (0 = left ... ). The two sub-arrays are the two trees; the chosen one is the
     * one with any non-null value. The taken node in each row is highlighted and the path is traced.
     */
    private fun drawSkillTree(bx: Float, by: Float, bw: Float, d: TelosCharacterDetail): Float {
        val branches = d.type?.skillTreeSelection ?: emptyList()
        val active = branches.firstOrNull { row -> row.any { it != null } }
        NVGRenderer.text("Path taken is highlighted.", bx + 2f, by, 9.5f, MUTE, font)
        
        val rowCounts = listOf(3, 2, 3, 2, 2, 1)
        val node = 46f
        val gap = 16f
        val top = by + 22f
        
        // Resolve each row's nodes and which one was taken (row r top-down maps to decision 5-r).
        data class Node(val x: Float, val y: Float, val taken: Boolean)
        val rows = ArrayList<List<Node>>()
        var cy = top
        rowCounts.forEachIndexed { r, count ->
            val decision = rowCounts.size - 1 - r
            val takenCol = when {
                active == null -> -1
                count == 1 -> 0 // single start node is always taken
                else -> active.getOrNull(decision)?.coerceIn(0, count - 1) ?: -1
            }
            val rowW = count * node + (count - 1) * gap
            val rx = bx + (bw - rowW) / 2f
            rows.add((0 until count).map { c -> Node(rx + c * (node + gap), cy, c == takenCol) })
            cy += node + gap
        }
        
        // Trace the path between taken nodes of consecutive rows (drawn behind the nodes).
        for (r in 0 until rows.size - 1) {
            val a = rows[r].firstOrNull { it.taken } ?: continue
            val b = rows[r + 1].firstOrNull { it.taken } ?: continue
            NVGRenderer.line(a.x + node / 2f, a.y + node / 2f, b.x + node / 2f, b.y + node / 2f, 2f, ACCENT_DIM)
        }
        
        rows.flatten().forEach { drawSkillNode(it.x, it.y, node, it.taken) }
        return cy - by
    }
    
    private fun drawSkillNode(x: Float, y: Float, size: Float, taken: Boolean) {
        NVGRenderer.rect(x, y, size, size, if (taken) blend(ACCENT, 0xFF15151A.toInt(), 0.25f) else PANEL, 8f)
        NVGRenderer.hollowRect(x, y, size, size, if (taken) 2f else 1f, if (taken) ACCENT else STROKE, 8f)
    }
    
    private fun drawBackpack(bx: Float, by: Float, bw: Float, d: TelosCharacterDetail): Float {
        val bp = d.backpack ?: emptyList()
        val count = bp.count { it?.key != null }
        NVGRenderer.text("BACKPACK — $count items", bx + 2f, by, 9f, MUTE, font)
        if (bp.isEmpty()) {
            NVGRenderer.text("Backpack is empty.", bx + 2f, by + 18f, 12f, MUTE, font)
            return 36f
        }
        return drawSlotGrid(bx, by + 16f, bw, bp) - by
    }
    
    // ==================== OTHER TABS ====================
    
    private fun drawEmptyState(bx: Float, by: Float, bw: Float, title: String, subtitle: String): Float {
        val cy = by + contentH / 2f - 20f
        NVGRenderer.text(title, bx + (bw - NVGRenderer.textWidth(title, 14f, font)) / 2f, cy, 14f, SUBTEXT, font)
        NVGRenderer.text(subtitle, bx + (bw - NVGRenderer.textWidth(subtitle, 10.5f, font)) / 2f, cy + 22f, 10.5f, MUTE, font)
        return contentH
    }
    
    private fun drawCompanions(bx: Float, by: Float, bw: Float): Float {
        var cy = by
        
        // Equipped mount + pet, with equipped skins to their right.
        cy = drawEquippedHeader(bx, cy, bw) + 16f
        
        cy = drawCompanionSection(bx, cy, bw, "Mounts", CompanionData.mounts, equippedMountId)
        cy += 16f
        cy = drawCompanionSection(bx, cy, bw, "Pets", CompanionData.pets, equippedPetId)
        return cy - by
    }
    
    private fun equippedOf(title: String): EquippedCompanion? = equipped.firstOrNull { it.title == title }
    
    private fun companionRarity(id: String): CompanionData.Rarity? =
        CompanionData.mounts.firstOrNull { it.id == id }?.rarity
            ?: CompanionData.pets.firstOrNull { it.id == id }?.rarity
    
    /** Display name for a companion; the starter pet is "Gelato" and the starter mount is "Rolo". */
    private fun companionName(id: String): String {
        if (CompanionData.starterRank(id) != null) return if (id.startsWith("mount-")) "Rolo" else "Gelato"
        return TelosItems.resolve(id).displayName
    }
    
    /** Equipped mount + pet cards on the left and a panel of equipped skins on the right. */
    private fun drawEquippedHeader(bx: Float, by: Float, bw: Float): Float {
        val h = 58f
        val gap = 12f
        val cardW = (bw - gap) / 2f
        drawEquippedCard(bx, by, cardW, h, "Mount", equippedOf("Mount"), equippedMountId, equippedMountSkin)
        drawEquippedCard(bx + cardW + gap, by, cardW, h, "Pet", equippedOf("Pet"), equippedPetId, equippedPetSkin)
        return by + h
    }
    
    /**
     * One equipped box: the companion icon + name on the left, and an equipped-skin slot
     * (label + icon) on the right, all inside the same bordered box.
     */
    private fun drawEquippedCard(x: Float, y: Float, w: Float, h: Float, title: String, eq: EquippedCompanion?, equippedId: String?, skinRaw: String?) {
        NVGRenderer.rect(x, y, w, h, PANEL, 8f)
        NVGRenderer.hollowRect(x, y, w, h, 1f, STROKE, 8f)
        
        // Skin slot on the right.
        val slot = h - 18f
        val slotX = x + w - 12f - slot
        val slotY = y + (h - slot) / 2f
        NVGRenderer.rect(slotX, slotY, slot, slot, PANEL_HI, 6f)
        NVGRenderer.hollowRect(slotX, slotY, slot, slot, 1f, STROKE, 6f)
        val skinResolved = skinRaw?.let { skinToCompanionId(it) }?.let { TelosItems.resolve(it) }
        if (skinResolved != null) drawIcon(skinResolved, slotX + 4f, slotY + 4f, slot - 8f)
        val skinLabel = "SKIN"
        val labelW = NVGRenderer.textWidth(skinLabel, 9f, font)
        val labelX = slotX - 8f - labelW
        NVGRenderer.text(skinLabel, labelX, y + h / 2f - 4.5f, 9f, MUTE, font)
        if (skinRaw != null && skinResolved != null && visibleHover(slotX, slotY, slot, slot)) {
            val skinName = prettySkin(skinRaw) ?: skinResolved.displayName
            tooltip = skinName to (companionRarity(equippedId ?: "")?.color ?: ACCENT)
        }
        
        // Left: companion icon + name + level.
        if (eq == null || equippedId == null) {
            NVGRenderer.text(title.uppercase(), x + 12f, y + 10f, 8f, MUTE, font)
            NVGRenderer.text("None equipped", x + 12f, y + 28f, 11f, MUTE, font)
            return
        }
        val resolved = TelosItems.resolve(equippedId)
        drawIcon(resolved, x + 11f, y + (h - 36f) / 2f, 36f)
        val tx = x + 56f
        NVGRenderer.text(title.uppercase(), tx, y + 11f, 8f, MUTE, font)
        val name = companionName(equippedId)
        val nameColor = companionRarity(equippedId)?.color ?: resolved.rarityColor
        val nameMaxW = (labelX - 6f) - tx
        val nameSize = fitText(name, nameMaxW.coerceAtLeast(40f), 13f, 9f)
        NVGRenderer.text(name, tx, y + 23f, nameSize, nameColor, font)
        val rank = CompanionData.starterRank(equippedId)
        val levelText = if (rank != null) "(${rank}/${CompanionData.STARTER_COUNT - 1})" else eq.level?.let { "Lv. $it" }
        levelText?.let { NVGRenderer.text(it, tx, y + 40f, 9.5f, SUBTEXT, font) }
    }
    
    /** A companion section (Mounts/Pets) with one labelled row block per rarity. */
    private fun drawCompanionSection(bx: Float, by: Float, bw: Float, title: String, list: List<CompanionData.Companion>, equippedId: String?): Float {
        var cy = by
        val unlockedCount = list.count { isCompanionUnlocked(it.id) } - 7
        
        NVGRenderer.text(title.uppercase(), bx + 2f, cy, 11f, TEXT, font)
        val count = "$unlockedCount / ${list.size - 7}"
        NVGRenderer.text(count, bx + bw - 2f - NVGRenderer.textWidth(count, 11f, font), cy, 11f, MUTE, font)
        cy += 18f
        
        CompanionData.Rarity.entries.forEach { rarity ->
            val group = list.filter { it.rarity == rarity }
            if (group.isEmpty()) return@forEach
            NVGRenderer.rect(bx + 2f, cy + 1f, 8f, 8f, rarity.color, 2f)
            NVGRenderer.text(rarity.display, bx + 14f, cy, 9.5f, rarity.color, font)
            cy += 14f
            cy = drawCompanionRow(bx, cy, bw, group, equippedId) + 10f
        }
        return cy
    }
    
    /** A wrapping row of companion tiles. */
    private fun drawCompanionRow(bx: Float, startY: Float, bw: Float, list: List<CompanionData.Companion>, equippedId: String?): Float {
        if (list.isEmpty()) return startY
        val cols = max(1, ((bw + COMP_GAP) / (COMP_TILE + COMP_GAP)).toInt())
        list.forEachIndexed { i, comp ->
            val tx = bx + (i % cols) * (COMP_TILE + COMP_GAP)
            val ty = startY + (i / cols) * (COMP_TILE + COMP_GAP)
            drawCompanionTile(comp, tx, ty, COMP_TILE, comp.id == equippedId)
        }
        val rows = ceil(list.size / cols.toFloat()).toInt()
        return startY + rows * (COMP_TILE + COMP_GAP) - COMP_GAP
    }
    
    private fun drawCompanionTile(comp: CompanionData.Companion, x: Float, y: Float, size: Float, equipped: Boolean) {
        val resolved = TelosItems.resolve(comp.id)
        val rc = comp.rarity.color
        val unlocked = isCompanionUnlocked(comp.id)
        
        NVGRenderer.rect(x, y, size, size, blend(rc, 0xFF15151A.toInt(), if (unlocked) 0.30f else 0.10f), 7f)
        NVGRenderer.hollowRect(x, y, size, size, 1f, if (unlocked) rc else blend(rc, CARD, 0.4f), 7f)
        drawIcon(resolved, x + 4f, y + 4f, size - 8f)
        if (!unlocked) NVGRenderer.rect(x, y, size, size, 0x99101014.toInt(), 7f)
        if (equipped) NVGRenderer.hollowRect(x, y, size, size, 2f, GOLD, 7f)
        
        if (visibleHover(x, y, size, size)) {
            NVGRenderer.hollowRect(x, y, size, size, 2f, ACCENT, 7f)
            val state = if (equipped) "Equipped" else if (unlocked) "Unlocked" else "Locked"
            val rank = CompanionData.starterRank(comp.id)
            val name = companionName(comp.id) + if (rank != null) " (${rank}/${CompanionData.STARTER_COUNT - 1})" else ""
            tooltip = "$name  •  $state" to rc
        }
    }
    
    /**
     * Each stash page is a fixed 5-row x 9-column grid (empty slots render as blank tiles). Pages
     * are laid out two-up when there's room to do so without shrinking tiles too far; otherwise a
     * single centred column is used so the page doesn't look empty.
     */
    private fun drawStash(bx: Float, by: Float, bw: Float): Float {
        var cy = by
        val summary = "$stashCount items  •  ${stashGroups.size} pages used"
        NVGRenderer.text(summary, bx + 2f, cy, 9f, MUTE, font)
        cy += 16f
        
        if (stashGroups.isEmpty()) {
            NVGRenderer.text("Stash is empty.", bx + 2f, cy + 2f, 12f, MUTE, font)
            return (cy + 20f) - by
        }
        
        val cols = 9
        val rows = 5
        val gap = 6f
        val colGap = 18f
        
        // Tile size if two pages were placed side by side; only do so if it stays reasonably large.
        val twoUpTile = ((bw - colGap) / 2f - gap * (cols - 1)) / cols
        val twoUp = twoUpTile >= 30f
        val tile = if (twoUp) min(48f, twoUpTile) else min(60f, (bw - gap * (cols - 1)) / cols)
        val gridW = cols * tile + (cols - 1) * gap
        val perRow = if (twoUp) 2 else 1
        val pageH = 15f + rows * (tile + gap) + 14f
        
        stashGroups.chunked(perRow).forEach { rowPages ->
            val rowW = rowPages.size * gridW + (rowPages.size - 1) * colGap
            val startX = bx + (bw - rowW) / 2f
            rowPages.forEachIndexed { ci, (label, slots) ->
                drawStashPage(startX + ci * (gridW + colGap), cy, label, slots, cols, rows, tile, gap)
            }
            cy += pageH
        }
        return cy - by
    }
    
    private fun drawStashPage(px: Float, py: Float, label: String, slots: List<TelosItems.Resolved?>, cols: Int, rows: Int, tile: Float, gap: Float) {
        NVGRenderer.text(label, px + 2f, py, 10f, SUBTEXT, font)
        val gridTop = py + 15f
        for (i in 0 until cols * rows) {
            val tx = px + (i % cols) * (tile + gap)
            val ty = gridTop + (i / cols) * (tile + gap)
            val item = slots.getOrNull(i)
            if (item != null) drawTile(item, tx, ty, tile) else drawEmptyTile(tx, ty, tile)
        }
    }
    
    // ==================== TILES ====================
    
    private fun drawEmptyTile(x: Float, y: Float, size: Float) {
        NVGRenderer.rect(x, y, size, size, 0xFF15151A.toInt(), 6f)
        NVGRenderer.hollowRect(x, y, size, size, 1f, STROKE, 6f)
    }
    
    private fun drawTile(item: TelosItems.Resolved, x: Float, y: Float, size: Float = TILE) {
        NVGRenderer.rect(x, y, size, size, blend(item.rarityColor, 0xFF15151A.toInt(), 0.82f), 6f)
        NVGRenderer.hollowRect(x, y, size, size, 1f, blend(item.rarityColor, CARD, 0.45f), 6f)
        drawIcon(item, x + 3f, y + 3f, size - 6f)
        
        if (visibleHover(x, y, size, size)) {
            NVGRenderer.hollowRect(x, y, size, size, 1.5f, ACCENT, 6f)
            val rarity = item.rarity?.name?.lowercase()?.replaceFirstChar { it.uppercaseChar() }
            tooltip = (if (rarity != null) "${item.displayName}  ($rarity)" else item.displayName) to item.rarityColor
        }
    }
    
    private fun drawIcon(item: TelosItems.Resolved, x: Float, y: Float, size: Float) {
        if (!NVGRenderer.texturedRect(item.textureResource, x, y, size, size)) {
            val initial = item.displayName.firstOrNull()?.uppercase() ?: "?"
            NVGRenderer.text(initial, x + (size - NVGRenderer.textWidth(initial, size * 0.7f, font)) / 2f, y + size * 0.12f, size * 0.7f, item.rarityColor, font)
        }
    }
    
    private fun drawTooltip(text: String, color: Int) {
        val tw = NVGRenderer.textWidth(text, 11f, font)
        val w = tw + 16f
        val h = 22f
        var tx = sx + 12f
        var ty = sy + 12f
        val vW = mc.window.screenWidth / scaleNow
        if (tx + w > vW) tx = sx - w - 12f
        if (ty + h > mc.window.screenHeight / scaleNow) ty = sy - h - 12f
        NVGRenderer.dropShadow(tx, ty, w, h, 10f, 1f, 6f)
        NVGRenderer.rect(tx, ty, w, h, PANEL_HI, 6f)
        NVGRenderer.hollowRect(tx, ty, w, h, 1f, blend(color, STROKE, 0.5f), 6f)
        NVGRenderer.text(text, tx + 8f, ty + 5.5f, 11f, TEXT, font)
    }
    
    // ==================== INPUT ====================
    
    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        val scale = ClickGUIModule.getStandardGuiScale()
        val mx = rawMouseX / scale
        val my = rawMouseY / scale
        
        // Character detail view captures input on its own header.
        if (viewingCharacterId != null) {
            backRect?.let { if (inRect(mx, my, it)) { closeCharacter(); return true } }
            DetailTab.entries.forEachIndexed { i, t ->
                detailTabRects[i]?.let { if (inRect(mx, my, it)) { detailTab = t; return true } }
            }
            return super.mouseClicked(mouseButtonEvent, bl)
        }
        
        Tab.entries.forEachIndexed { i, t ->
            tabRects[i]?.let { r ->
                if (inRect(mx, my, r)) {
                    tab = t
                    return true
                }
            }
        }
        
        if (tab == Tab.OVERVIEW) {
            spLeftRect?.let { if (inRect(mx, my, it)) { seasonPage = (seasonPage - 1 + SeasonPassData.PAGES) % SeasonPassData.PAGES; return true } }
            spRightRect?.let { if (inRect(mx, my, it)) { seasonPage = (seasonPage + 1) % SeasonPassData.PAGES; return true } }
        }
        
        if (tab == Tab.CHARACTERS) {
            for ((rect, id) in charRects) {
                if (id != null && inRect(mx, my, rect)) {
                    openCharacter(id)
                    return true
                }
            }
        }
        
        return super.mouseClicked(mouseButtonEvent, bl)
    }
    
    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (viewingCharacterId != null) {
            val maxScroll = max(0f, detailContentHeight[detailTab.ordinal] - contentH)
            if (maxScroll > 0f) {
                detailScroll[detailTab.ordinal] = (detailScroll[detailTab.ordinal] - verticalAmount.sign.toFloat() * 28f).coerceIn(0f, maxScroll)
                return true
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }
        val maxScroll = max(0f, contentHeight[tab.ordinal] - contentH)
        if (maxScroll > 0f) {
            scroll[tab.ordinal] = (scroll[tab.ordinal] - verticalAmount.sign.toFloat() * 28f).coerceIn(0f, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }
    
    override fun isPauseScreen(): Boolean = false
    
    override fun removed() {
        ProfileModelRenderer.clear()
        super.removed()
    }
    
    // ==================== HELPERS ====================
    
    private fun inRect(mx: Float, my: Float, r: FloatArray): Boolean =
        mx in r[0]..(r[0] + r[2]) && my in r[1]..(r[1] + r[3])
    
    private fun hover(x: Float, y: Float, w: Float, h: Float): Boolean = sx in x..(x + w) && sy in y..(y + h)
    
    private fun visibleHover(x: Float, y: Float, w: Float, h: Float): Boolean {
        val screenY = y - curScroll
        return sx in x..(x + w) && (sy + curScroll) in y..(y + h) && screenY + h >= bodyTop && screenY <= bodyBottom
    }
    
    /** Largest font size in [maxSize] down to [minSize] that fits [text] within [maxWidth]. */
    private fun fitText(text: String, maxWidth: Float, maxSize: Float, minSize: Float): Float {
        var size = maxSize
        while (size > minSize && NVGRenderer.textWidth(text, size, font) > maxWidth) size -= 0.5f
        return size
    }
    
    private fun dots(): String = ".".repeat(((System.currentTimeMillis() / 400) % 4).toInt())
    
    private fun prettySkin(skin: String?): String? {
        if (skin.isNullOrBlank()) return null
        return skin.removePrefix("skinpet-").removePrefix("skinmount-").removePrefix("skin-")
            .replace('_', ' ').split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
    }
    
    private fun commas(value: Long): String {
        val neg = value < 0
        val digits = abs(value).toString().reversed().chunked(3).joinToString(",").reversed()
        return if (neg) "-$digits" else digits
    }
    
    private fun compact(value: Long): String = when {
        value >= 1_000_000_000 -> "%.1fB".format(value / 1_000_000_000.0)
        value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
        value >= 1_000 -> "%.1fK".format(value / 1_000.0)
        else -> value.toString()
    }
    
    private fun duration(millis: Long): String {
        if (millis <= 0) return "0m"
        val totalMinutes = millis / 60_000
        val days = totalMinutes / 1440
        val hours = (totalMinutes % 1440) / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
    
    private fun relativeTime(epochMillis: Long): String {
        if (epochMillis <= 0) return "Never"
        val diff = System.currentTimeMillis() - epochMillis
        if (diff < 0) return "Just now"
        val minutes = diff / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "Just now"
        }
    }
    
    private fun title(raw: String): String =
        raw.lowercase().split(' ', '_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
    
    private fun blend(a: Int, b: Int, t: Float): Int {
        val ar = (a shr 16) and 0xFF; val ag = (a shr 8) and 0xFF; val ab = a and 0xFF
        val br = (b shr 16) and 0xFF; val bg = (b shr 8) and 0xFF; val bb = b and 0xFF
        val r = (ar * t + br * (1 - t)).toInt()
        val g = (ag * t + bg * (1 - t)).toInt()
        val bl = (ab * t + bb * (1 - t)).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }
}

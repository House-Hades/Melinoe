package me.melinoe.clickgui

import me.melinoe.Melinoe
import me.melinoe.Melinoe.mc
import me.melinoe.features.impl.ClickGUIModule
import me.melinoe.network.*
import me.melinoe.utils.Color
import me.melinoe.utils.data.*
import me.melinoe.utils.handlers.schedule
import me.melinoe.utils.render.hollowFill
import me.melinoe.utils.ui.animations.EaseOutAnimation
import me.melinoe.utils.ui.rendering.Gradient
import me.melinoe.utils.ui.rendering.NVGPIPRenderer
import me.melinoe.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.*
import me.melinoe.utils.ui.mouseX as rawMouseX
import me.melinoe.utils.ui.mouseY as rawMouseY

/**
 * Full-screen tabbed profile viewer for Telos players, opened via /profile [username].
 *
 * Layout is a persistent left sidebar (player name + rotating 3D model) and a right pane with
 * five tabs. The Overview tab shows stat cards, a details list, the
 * season pass paginator and a sticker preview. The 3D model is submitted through the vanilla GUI
 * pass (see [ProfileModelRenderer]); everything else is drawn with NanoVG.
 *
 * All transitions go through the MOTION SYSTEM section: ease-out entrances with per-item stagger,
 * exponential glides for hover/scroll/tab-pill, and everything snapping to its end state under
 * the reduce-motion setting.
 */
class ProfileScreen private constructor(private val username: String) : Screen(Component.literal("Profile")) {
    
    companion object {
        /** Opens the profile screen for [username] on the render thread. */
        fun open(username: String) {
            schedule(0) { mc.setScreen(ProfileScreen(username)) }
        }
        
        // ==================== PALETTE (0xAARRGGBB) ====================
        // "Mint dream" theme built on the Melinoe brand gradient (#B8FFE1 -> #7CFFB2 -> #2E8F78):
        // deep teal-tinted surfaces with soft mint glows, intentionally distinct from the ClickGUI.
        private val BACKDROP = 0xD21C1C1E.toInt()
        private val CARD = 0xFF1C1C1E.toInt()
        private val PANEL = 0xFF13211A.toInt()
        private val PANEL_HI = 0xFF1B2F24.toInt()
        private val STROKE = 0xFF26433A.toInt()
        private val ACCENT = 0xFF7CFFB2.toInt()
        private val ACCENT_SOFT = 0xFFB8FFE1.toInt()
        private val ACCENT_DIM = 0xFF2E8F78.toInt()
        private val TEXT = 0xFFF0FFF7.toInt()
        private val SUBTEXT = 0xFFA8C9B9.toInt()
        private val MUTE = 0xFF8FB0A2.toInt()  // lifted for WCAG AA on dark panels (~7:1)
        private val GOLD = 0xFFFFD700.toInt()
        private val SEASONAL = 0xFFFF7CCB.toInt()
        private val IRONMAN = 0xFFB3590E.toInt()  // hardcore/ironman ruleset tag
        private val ERROR = 0xFFFF6B7A.toInt()
        private val INK = 0xFF06140C.toInt()      // dark text on bright accent fills
        private val WELL = 0xFF0A130E.toInt()     // sunken slots and empty tiles
        private val LOCK = 0xA608120C.toInt()     // locked-tile dim overlay
        private val ROW_LINE = 0xFF1A2C22.toInt() // hairline under detail rows
        
        private const val PAD = 16f
        private const val TILE = 30f
        // Larger tiles for the companions page so they fill more of the panel.
        private const val COMP_TILE = 46f
        private const val COMP_GAP = 8f
        
        // Motion tokens: one shared rhythm for every transition in this screen.
        private const val ENTER_DUR = 0.22f  // content entrance duration (seconds)
        private const val STAGGER = 0.035f   // per-item delay in staggered grids/lists
        private const val SMOOTH = 14f       // exponential approach speed for hover/scroll/pill glides
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
    private var stashGroups: List<Pair<String, List<StashItem?>>> = emptyList()
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
    // Item tooltip (name + trait lore). Rendered through the vanilla GUI pass with the Minecraft
    // font so the Telos stat glyphs in the trait descriptions render. Reset each frame.
    private var itemTip: ItemTip? = null

    private data class ItemTip(val title: String, val titleColor: Int, val lines: List<List<TraitData.Run>>)
    
    // Screen-space (scroll-adjusted) season pass paginator hitboxes.
    private var spLeftRect: FloatArray? = null
    private var spRightRect: FloatArray? = null
    
    // Retry button hitboxes (set while an error state is on screen).
    private var retryRect: FloatArray? = null
    private var detailRetryRect: FloatArray? = null
    
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
    
    // ==================== FLOURISHES ====================
    
    private val openedAt = System.currentTimeMillis()
    
    /** Seconds since the screen opened; drives all ambient animation. */
    private fun time(): Float = (System.currentTimeMillis() - openedAt) / 1000f
    
    /** When on, ambient motion holds still (decoration stays, movement stops) for readability. */
    private val reduceMotion get() = ClickGUIModule.reduceMotion
    
    // ==================== MOTION SYSTEM ====================
    // Unified transitions: ease-out entrances (~220ms), exponential glides for hover/scroll/pill.
    // Everything snaps to its end state when reduce-motion is on.
    
    private var dt = 0.016f
    private var lastFrameMs = System.currentTimeMillis()
    private var alphaNow = 1f
    
    // Transition timestamps (seconds on the time() clock; -10 = "long settled").
    private var tabSwitchedAt = 0f
    private var tabDir = 0
    private var detailTabSwitchedAt = -10f
    private var detailTabDir = 0
    private var detailOpenedAt = -10f
    private var seasonFlipAt = -10f
    private var seasonDir = 0
    private var profileShown = false
    private var scrollActiveAt = -10f
    private var lastTooltipText: String? = null
    private var tooltipAt = -10f
    
    // Scroll targets; the visible scroll values ease toward these each frame.
    private val scrollTarget = FloatArray(Tab.entries.size)
    private val detailScrollTarget = FloatArray(DetailTab.entries.size)
    
    private fun easeOutCubic(t: Float): Float {
        val c = 1f - t.coerceIn(0f, 1f)
        return 1f - c * c * c
    }
    
    /** Eased 0..1 entrance progress for an element [delay]s into a transition that began [since]s ago. */
    private fun enter(since: Float, delay: Float = 0f, dur: Float = ENTER_DUR): Float =
        if (reduceMotion) 1f else easeOutCubic((since - delay) / dur)
    
    private val hoverFracs = HashMap<String, Float>()
    
    /** Frame-smoothed hover fraction for [key]; eases toward the hovered state (~150ms feel). */
    private fun hoverFrac(key: String, hovered: Boolean): Float {
        val target = if (hovered) 1f else 0f
        if (reduceMotion) { hoverFracs[key] = target; return target }
        val cur = hoverFracs.getOrDefault(key, 0f)
        var next = cur + (target - cur) * min(1f, dt * SMOOTH)
        if (abs(next - target) < 0.01f) next = target
        hoverFracs[key] = next
        return next
    }
    
    /** Animated geometry of an active-tab pill that glides between tabs (shared-element continuity). */
    private class Pill { var x = 0f; var w = 0f; var set = false }
    private val tabPill = Pill()
    private val detailPill = Pill()
    
    private fun tickPill(p: Pill, tx: Float, tw: Float) {
        if (!p.set || reduceMotion) { p.x = tx; p.w = tw; p.set = true; return }
        val k = min(1f, dt * SMOOTH)
        p.x += (tx - p.x) * k
        p.w += (tw - p.w) * k
    }
    
    /** Multiplies the current global alpha by [a]; returns the previous value for [popAlpha]. */
    private fun pushAlpha(a: Float): Float {
        val prev = alphaNow
        alphaNow = prev * a.coerceIn(0f, 1f)
        NVGRenderer.globalAlpha(alphaNow)
        return prev
    }
    
    private fun popAlpha(prev: Float) {
        alphaNow = prev
        NVGRenderer.globalAlpha(prev)
    }
    
    /** Eases scroll position [i] of [cur] toward its target, clamped to the scrollable range. */
    private fun smoothScroll(cur: FloatArray, target: FloatArray, i: Int, fullHeight: Float) {
        val maxScroll = max(0f, fullHeight - contentH)
        target[i] = target[i].coerceIn(0f, maxScroll)
        if (reduceMotion) { cur[i] = target[i]; return }
        val d = target[i] - cur[i]
        if (abs(d) < 0.4f) { cur[i] = target[i]; return }
        cur[i] += d * min(1f, dt * SMOOTH)
        scrollActiveAt = time()
    }
    
    /** Scrollbar thumb that brightens while scrolling and settles back when idle. */
    private fun drawScrollThumb(used: Float, cur: Float) {
        if (used <= contentH) return
        val thumbH = max(24f, contentH * (contentH / used))
        val thumbY = contentY + (contentH - thumbH) * (cur / (used - contentH))
        val idle = ((time() - scrollActiveAt - 0.6f) / 0.4f).coerceIn(0f, 1f)
        NVGRenderer.rect(panelX + panelW - 6f, thumbY, 3f, thumbH, withAlpha(ACCENT_DIM, 1f - 0.65f * idle), 1.5f)
    }
    
    private fun switchTab(t: Tab) {
        if (t == tab) return
        tabDir = if (t.ordinal > tab.ordinal) 1 else -1
        tab = t
        tabSwitchedAt = time()
    }
    
    private fun switchDetailTab(t: DetailTab) {
        if (t == detailTab) return
        detailTabDir = if (t.ordinal > detailTab.ordinal) 1 else -1
        detailTab = t
        detailTabSwitchedAt = time()
    }
    
    private fun flipSeason(dir: Int) {
        seasonPage = (seasonPage + dir + SeasonPassData.PAGES) % SeasonPassData.PAGES
        seasonDir = dir
        seasonFlipAt = time()
    }
    
    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24)
    
    /** Soft halo around a rounded rect, layered outwards with falling alpha. */
    private fun glow(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float, strength: Float = 1f) {
        for (i in 1..3) {
            NVGRenderer.hollowRect(x - i, y - i, w + i * 2f, h + i * 2f, 1.5f, withAlpha(color, 0.12f * strength * (4 - i) / 3f), radius + i)
        }
    }
    
    /** Horizontal three-stop brand gradient fill (#B8FFE1 -> #7CFFB2 -> #2E8F78). */
    private fun brandGradient(x: Float, y: Float, w: Float, h: Float, radius: Float, alpha: Float = 1f) {
        val half = w / 2f
        NVGRenderer.gradientRect(x, y, half + radius, h, withAlpha(ACCENT_SOFT, alpha), withAlpha(ACCENT, alpha), Gradient.LeftToRight, radius)
        NVGRenderer.gradientRect(x + half - radius, y, half + radius, h, withAlpha(ACCENT, alpha), withAlpha(ACCENT_DIM, alpha), Gradient.LeftToRight, radius)
    }
    
    /** A four-point twinkle; [phase] keeps neighbouring sparkles out of sync. */
    private fun sparkle(cx: Float, cy: Float, size: Float, color: Int, phase: Float, baseAlpha: Float = 1f) {
        val tw = if (reduceMotion) 0.7f else sin(time() * 2.1f + phase) * 0.5f + 0.5f
        if (tw < 0.06f) return
        val s = size * (0.55f + 0.45f * tw)
        val a = withAlpha(color, baseAlpha * (0.2f + 0.8f * tw))
        val th = s * 0.26f
        NVGRenderer.push()
        NVGRenderer.translate(cx, cy)
        NVGRenderer.rotate(if (reduceMotion) 0.7853982f else time() * 0.5f + phase)
        NVGRenderer.rect(-s / 2f, -th / 2f, s, th, a, th / 2f)
        NVGRenderer.rect(-th / 2f, -s / 2f, th, s, a, th / 2f)
        NVGRenderer.pop()
        NVGRenderer.circle(cx, cy, s * 0.14f, withAlpha(ACCENT_SOFT, baseAlpha * tw))
    }
    
    /** Small rotated-square bullet used before section headers. */
    private fun diamond(cx: Float, cy: Float, size: Float, color: Int) {
        NVGRenderer.push()
        NVGRenderer.translate(cx, cy)
        NVGRenderer.rotate(0.7853982f)
        NVGRenderer.rect(-size / 2f, -size / 2f, size, size, color, size * 0.25f)
        NVGRenderer.pop()
    }
    
    /** Tiny padlock centred at ([cx],[cy]); [s] is the overall lock height. */
    private fun drawLock(cx: Float, cy: Float, s: Float, color: Int) {
        val bodyW = s * 0.82f
        val bodyH = s * 0.54f
        val bodyTop = cy - bodyH / 2f + s * 0.14f
        val shackleW = bodyW * 0.62f
        val shackleH = s * 0.5f
        // Shackle: a rounded pill whose lower half is covered by the body, leaving an inverted U.
        NVGRenderer.hollowRect(cx - shackleW / 2f, bodyTop - shackleH * 0.66f, shackleW, shackleH, s * 0.12f, color, shackleW / 2f)
        NVGRenderer.rect(cx - bodyW / 2f, bodyTop, bodyW, bodyH, color, s * 0.14f)
        NVGRenderer.circle(cx, bodyTop + bodyH * 0.46f, s * 0.08f, withAlpha(INK, 0.9f))
    }
    
    /** Persistent "locked" badge: dark backing disc + padlock, centred on a tile. */
    private fun drawLockBadge(x: Float, y: Float, size: Float) {
        val cx = x + size / 2f
        val cy = y + size / 2f
        val s = (size * 0.4f).coerceIn(11f, 20f)
        NVGRenderer.circle(cx, cy, s * 0.64f, withAlpha(INK, 0.7f))
        drawLock(cx, cy, s * 0.62f, ACCENT_SOFT)
    }
    
    /** Rounded pill progress bar with brand-gradient fill and a travelling shimmer. */
    private fun shimmerBar(x: Float, y: Float, w: Float, h: Float, frac: Float) {
        NVGRenderer.rect(x, y, w, h, WELL, h / 2f)
        NVGRenderer.hollowRect(x, y, w, h, 1f, withAlpha(STROKE, 0.85f), h / 2f)
        if (frac <= 0f) return
        // The fill sweeps up to its real value shortly after the tab lands.
        val reveal = enter(time() - tabSwitchedAt, 0.08f, 0.5f)
        if (reveal <= 0f) return
        val fw = (w * (frac * reveal).coerceIn(0f, 1f)).coerceAtLeast(h)
        brandGradient(x, y, fw, h, h / 2f)
        glow(x, y, fw, h, ACCENT, h / 2f, 0.7f)
        // Shimmer band sweeping the filled portion (kept fully inside, so no clipping needed).
        val band = (fw * 0.30f).coerceIn(h, fw)
        val travel = fw - band
        val phase = (time() * 0.5f) % 1.8f
        if (!reduceMotion && travel > 2f && phase <= 1f) {
            val tx = x + travel * phase
            val white = 0xFFFFFFFF.toInt()
            NVGRenderer.gradientRect(tx, y, band / 2f, h, withAlpha(white, 0f), withAlpha(white, 0.30f), Gradient.LeftToRight, h / 2f)
            NVGRenderer.gradientRect(tx + band / 2f, y, band / 2f, h, withAlpha(white, 0.30f), withAlpha(white, 0f), Gradient.LeftToRight, h / 2f)
        }
    }
    
    
    override fun init() {
        openAnim.start()
        startFetch()
        super.init()
    }
    
    /** Kicks off the profile fetch once; safe to call repeatedly (no-op while a fetch is live). */
    private fun startFetch() {
        if (fetchStarted) return
        fetchStarted = true
        ProfileFetcher.loadDefinitions()
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
    
    /** Clears the failed-fetch state and tries the profile request again. */
    private fun retryFetch() {
        errorMsg = null
        profile = null
        profileShown = false
        characterList = null
        equippedCharacterId = null
        fetchStarted = false
        startFetch()
    }
    
    /** Re-requests the currently-open character after a detail fetch failure (bypasses the cache). */
    private fun retryDetail() {
        val id = viewingCharacterId ?: return
        detailError = null
        characterDetailCache.remove(id)
        openCharacter(id)
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
        detailScrollTarget.fill(0f)
        detailOpenedAt = time()
        detailTabSwitchedAt = -10f
        detailPill.set = false
        
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
            c.pet?.let { list.add(EquippedCompanion("Pet", TelosItems.resolve(it), null, prettySkin(c.petSkin))) }
            c.mount?.let { list.add(EquippedCompanion("Mount", TelosItems.resolve(it), null, prettySkin(c.mountSkin))) }
            equipped = list

            // Equipped companion ids are full keys now ("pet/tenebris", "mount/onyx").
            equippedPetId = c.pet?.ifBlank { null }
            equippedMountId = c.mount?.ifBlank { null }
            equippedPetSkin = c.petSkin?.ifBlank { null }
            equippedMountSkin = c.mountSkin?.ifBlank { null }

            val ids = (c.unlocked ?: emptyList()).toMutableSet()
            equippedPetId?.let { ids.add(it) }
            equippedMountId?.let { ids.add(it) }
            unlockedIds = ids
            unlockedPetCount = ids.count { it.startsWith("pet/") }
            unlockedMountCount = ids.count { it.startsWith("mount/") }
        }
        
        // Stash: keep the full slot layout (with empty slots) for any page that has at least 1 item.
        val groups = mutableListOf<Pair<String, List<StashItem?>>>()
        var count = 0
        p.stash?.forEach { page ->
            val slots = page.items ?: emptyList()
            val filled = slots.count { it?.key != null }
            if (filled > 0) {
                count += filled
                val label = (if (page.seasonal) "Seasonal" else "Normal") + "  •  Page ${page.page + 1}"
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
            Triple("Glory", commas(p.normalBalance.toLong()), GOLD),
            Triple("Seasonal Glory", commas(p.seasonalBalance.toLong()), SEASONAL),
            Triple("Total Fame", compact(totalFame), ACCENT),
            Triple("Deaths", commas(totalDeaths), TEXT),
            Triple("Classes", classesSorted.size.toString(), TEXT)
        )
    }
    
    /** Whether a catalogue companion is unlocked (starters use the maxed-class rank rule). */
    private fun isCompanionUnlocked(id: String): Boolean {
        val rank = CompanionData.starterRank(id)
        return if (rank != null) rank <= maxedClasses else unlockedIds.contains(id)
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
        val now = System.currentTimeMillis()
        dt = ((now - lastFrameMs) / 1000f).coerceIn(0f, 0.1f)
        lastFrameMs = now
        
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
            // The dim backdrop fades up with the panel rather than landing fully dark on frame one.
            NVGRenderer.rect(0f, 0f, vW, vH, withAlpha(BACKDROP, 0.82f * anim))
            drawAmbient(vW, vH)
            
            val opening = openAnim.isAnimating()
            alphaNow = if (opening) anim else 1f
            if (opening) {
                NVGRenderer.globalAlpha(anim)
                // Settle in from 97% scale around the panel centre for a soft ease-out landing.
                val pcx = panelX + panelW / 2f
                val pcy = panelY + panelH / 2f
                val s = 0.97f + 0.03f * anim
                NVGRenderer.push()
                NVGRenderer.translate(pcx, pcy)
                NVGRenderer.scale(s, s)
                NVGRenderer.translate(-pcx, -pcy)
            }
            
            tooltip = null
            itemTip = null
            drawProfile()
            if (tooltip == null) lastTooltipText = null
            tooltip?.let { drawTooltip(it.first, it.second) }
            
            if (opening) {
                NVGRenderer.pop()
                NVGRenderer.globalAlpha(1f)
            }
        }
        
        // Player model is submitted through the vanilla GUI pass, on top of the NVG sidebar panel.
        submitModel(context, mouseX.toFloat(), mouseY.toFloat())
        // The item tooltip is drawn here too so the trait glyphs use the Minecraft font.
        drawItemTooltip(context, mouseX, mouseY)

        super.extractRenderState(context, mouseX, mouseY, deltaTicks)
    }
    
    private fun drawItemTooltip(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val tip = itemTip ?: return
        val f = mc.font
        val pad = 4
        val lineH = f.lineHeight + 1

        var w = f.width(tip.title)
        for (line in tip.lines) {
            var lw = 0
            for (r in line) lw += f.width(r.text)
            if (lw > w) w = lw
        }
        val boxW = w + pad * 2
        val bodyH = if (tip.lines.isEmpty()) 0 else 3 + tip.lines.size * lineH
        val boxH = f.lineHeight + bodyH + pad * 2

        var x = mouseX + 12
        var y = mouseY + 12
        if (x + boxW > context.guiWidth()) x = (mouseX - boxW - 12).coerceAtLeast(2)
        if (y + boxH > context.guiHeight()) y = (mouseY - boxH - 12).coerceAtLeast(2)

        // Backing panel with a faint accent border.
        context.fill(x, y, x + boxW, y + boxH, 0xF00E1A12.toInt())
        context.hollowFill(x, y, boxW, boxH, 1, Color(blend(tip.titleColor, STROKE, 0.5f)))

        context.text(f, tip.title, x + pad, y + pad, tip.titleColor, true)
        var ly = y + pad + f.lineHeight + 2
        for (line in tip.lines) {
            var lx = x + pad
            for (r in line) {
                context.text(f, r.text, lx, ly, r.color, true)
                lx += f.width(r.text)
            }
            ly += lineH
        }
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
    
    /** Scattered twinkles drifting across the backdrop. */
    private fun drawAmbient(vW: Float, vH: Float) {
        for (i in 0 until 14) {
            val px = (((sin(i * 12.9898f) * 43758.547f) % 1f + 1f) % 1f) * vW
            val py = (((sin(i * 78.233f) * 12543.123f) % 1f + 1f) % 1f) * vH
            sparkle(px, py, 7f + (i % 3) * 3f, if (i % 4 == 0) ACCENT_SOFT else ACCENT, i * 1.7f, 0.5f)
        }
    }
    
    private fun drawProfile() {
        NVGRenderer.dropShadow(panelX, panelY, panelW, panelH, 30f, 3f, 20f)
        NVGRenderer.rect(panelX, panelY, panelW, panelH, CARD, 20f)
        glow(panelX, panelY, panelW, panelH, ACCENT, 20f, 0.9f)
        NVGRenderer.hollowRect(panelX, panelY, panelW, panelH, 1f, STROKE, 20f)
        // Brand gradient hairline along the top edge, with a soft mint sheen falling away below it.
        brandGradient(panelX + 24f, panelY, panelW - 48f, 2f, 1f, 0.9f)
        NVGRenderer.gradientRect(panelX, panelY, panelW, 110f, withAlpha(ACCENT, 0.045f), withAlpha(ACCENT, 0f), Gradient.TopToBottom, 20f)
        
        val p = profile
        // First frame with data: run the content entrance so loading -> profile crossfades in.
        if (p != null && !profileShown) {
            profileShown = true
            tabSwitchedAt = time()
            tabDir = 0
        }
        when {
            errorMsg != null -> retryRect = drawRetry(panelX + panelW / 2f, panelY + panelH / 2f, errorMsg!!)
            p == null -> drawLoading()
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
    
    /** Centred error message with a Retry pill below it; returns the pill's hitbox. */
    private fun drawRetry(cx: Float, cy: Float, msg: String): FloatArray {
        val mw = NVGRenderer.textWidth(msg, 13f, font)
        NVGRenderer.text(msg, cx - mw / 2f, cy - 22f, 13f, ERROR, font)
        
        val label = "Retry"
        val ph = 26f
        val pw = NVGRenderer.textWidth(label, 12f, font) + 28f
        val px = cx - pw / 2f
        val py = cy + 4f
        val r = ph / 2f
        val f = hoverFrac("retry", hover(px, py, pw, ph))
        NVGRenderer.rect(px, py, pw, ph, PANEL_HI, r)
        NVGRenderer.hollowRect(px, py, pw, ph, 1f, STROKE, r)
        if (f > 0.02f) {
            brandGradient(px, py, pw, ph, r, f)
            glow(px, py, pw, ph, ACCENT, r, 0.8f * f)
        }
        NVGRenderer.text(label, cx - NVGRenderer.textWidth(label, 12f, font) / 2f, py + (ph - 12f) / 2f, 12f, blend(INK, SUBTEXT, f), font)
        
        val hint = "Press R to try again"
        NVGRenderer.text(hint, cx - NVGRenderer.textWidth(hint, 9.5f, font) / 2f, py + ph + 8f, 9.5f, MUTE, font)
        return floatArrayOf(px, py, pw, ph)
    }
    
    /** Loading state: orbiting mint motes above the status text, twinkles either side. */
    private fun drawLoading() {
        val cx = panelX + panelW / 2f
        val cy = panelY + panelH / 2f
        val t = time()
        for (i in 0 until 3) {
            val ang = (if (reduceMotion) 0f else t * 3.2f) + i * 2.0944f
            val pulse = if (reduceMotion) 1f else 0.6f + 0.4f * sin(ang + 1.5f)
            NVGRenderer.circle(cx + cos(ang) * 18f, cy - 26f + sin(ang) * 8f, 3.2f * pulse, withAlpha(if (i == 0) ACCENT_SOFT else ACCENT, 0.85f))
        }
        val text = "Fetching $username's profile${dots()}"
        val w = NVGRenderer.textWidth(text, 13f, font)
        NVGRenderer.text(text, cx - w / 2f, cy - 6f, 13f, SUBTEXT, font)
        sparkle(cx - w / 2f - 14f, cy, 8f, ACCENT, 0.4f)
        sparkle(cx + w / 2f + 14f, cy, 8f, ACCENT, 2.6f)
    }
    
    private fun drawSidebar(p: TelosProfile) {
        val sbX = panelX + PAD
        val name = p.username ?: username
        val colRight = panelX + sidebarW
        
        // Name capsule with twinkles flanking the name.
        val nameY = panelY + PAD
        NVGRenderer.rect(sbX, nameY, modelBoxW, 44f, PANEL, 22f)
        NVGRenderer.hollowRect(sbX, nameY, modelBoxW, 44f, 1f, STROKE, 22f)
        glow(sbX, nameY, modelBoxW, 44f, ACCENT, 22f, 0.5f)
        val nameSize = fitText(name, modelBoxW - 56f, 18f, 12f)
        val shownName = ellipsize(name, modelBoxW - 56f, nameSize)
        val nameW = NVGRenderer.textWidth(shownName, nameSize, font)
        NVGRenderer.textShadow(shownName, sbX + (modelBoxW - nameW) / 2f, nameY + (44f - nameSize) / 2f, nameSize, ACCENT, font)
        sparkle(sbX + (modelBoxW - nameW) / 2f - 14f, nameY + 22f, 9f, ACCENT_SOFT, 0.9f)
        sparkle(sbX + (modelBoxW + nameW) / 2f + 14f, nameY + 22f, 9f, ACCENT_SOFT, 3.4f)
        
        // Model stage: tinted backdrop, glowing floor and rising motes (the 3D model draws on top).
        NVGRenderer.gradientRect(modelBoxX, modelBoxY, modelBoxW, modelBoxH, PANEL_HI, PANEL, Gradient.TopToBottom, 14f)
        NVGRenderer.hollowRect(modelBoxX, modelBoxY, modelBoxW, modelBoxH, 1f, STROKE, 14f)
        NVGRenderer.push()
        NVGRenderer.translate(modelBoxX + modelBoxW / 2f, modelBoxY + modelBoxH * 0.86f)
        NVGRenderer.scale(1f, 0.32f)
        NVGRenderer.circle(0f, 0f, modelBoxW * 0.40f, withAlpha(ACCENT, 0.10f))
        NVGRenderer.circle(0f, 0f, modelBoxW * 0.26f, withAlpha(ACCENT, 0.14f))
        NVGRenderer.pop()
        drawStageMotes()
        
        // Sidebar / main divider.
        NVGRenderer.line(colRight, panelY + PAD, colRight, panelY + panelH - PAD, 1f, STROKE)
    }
    
    /** Slow mint motes drifting up through the model stage. */
    private fun drawStageMotes() {
        if (reduceMotion) return
        val t = time()
        for (i in 0 until 7) {
            val seed = i * 1.618f
            val cycle = 7f + (i % 3) * 2.5f
            val prog = ((t / cycle + seed) % 1f + 1f) % 1f
            val mx = modelBoxX + 10f + ((sin(seed * 57.29f) * 0.5f + 0.5f) % 1f) * (modelBoxW - 20f) + sin(t * 0.8f + seed) * 6f
            val my = modelBoxY + modelBoxH - 12f - prog * (modelBoxH - 24f)
            val fade = (1f - prog) * min(1f, prog * 6f)
            if (i % 2 == 0) sparkle(mx, my, 6f, ACCENT, seed * 2f, fade * 0.8f)
            else NVGRenderer.circle(mx, my, 1.6f, withAlpha(ACCENT_SOFT, fade * 0.7f))
        }
    }
    
    private fun drawTabs() {
        val n = Tab.entries.size
        val gap = 6f
        val tw = (contentW - gap * (n - 1)) / n
        val r = tabBarH / 2f
        
        // Inactive plates first (hover eases in), then the gliding active pill, then labels on top.
        Tab.entries.forEachIndexed { i, t ->
            val tabX = contentX + (tw + gap) * i
            tabRects[i] = floatArrayOf(tabX, tabBarY, tw, tabBarH)
            if (t == tab) return@forEachIndexed
            val f = hoverFrac("tab$i", hover(tabX, tabBarY, tw, tabBarH))
            NVGRenderer.rect(tabX, tabBarY, tw, tabBarH, blend(PANEL_HI, PANEL, f), r)
            NVGRenderer.hollowRect(tabX, tabBarY, tw, tabBarH, 1f, blend(ACCENT_DIM, STROKE, f), r)
        }
        
        tickPill(tabPill, contentX + (tw + gap) * tab.ordinal, tw)
        brandGradient(tabPill.x, tabBarY, tabPill.w, tabBarH, r)
        glow(tabPill.x, tabBarY, tabPill.w, tabBarH, ACCENT, r)
        
        Tab.entries.forEachIndexed { i, t ->
            val tabX = contentX + (tw + gap) * i
            // The label darkens by however much of its tab the pill currently covers.
            val overlap = (min(tabX + tw, tabPill.x + tabPill.w) - max(tabX, tabPill.x)).coerceIn(0f, tw) / tw
            val base = if (hover(tabX, tabBarY, tw, tabBarH)) ACCENT_SOFT else SUBTEXT
            val color = blend(INK, base, overlap)
            val size = fitText(t.label, tw - 12f, 11.5f, 8.5f)
            val lx = tabX + (tw - NVGRenderer.textWidth(t.label, size, font)) / 2f
            NVGRenderer.text(t.label, lx, tabBarY + (tabBarH - size) / 2f, size, color, font)
        }
    }
    
    private fun drawContent(p: TelosProfile) {
        bodyTop = contentY
        bodyBottom = contentY + contentH
        val ti = tab.ordinal
        smoothScroll(scroll, scrollTarget, ti, contentHeight[ti])
        curScroll = scroll[ti]
        
        // New tab content fades in and slides from the direction it was approached.
        val e = enter(time() - tabSwitchedAt)
        val prevA = pushAlpha(e)
        
        NVGRenderer.pushScissor(contentX, contentY, contentW, contentH)
        NVGRenderer.push()
        NVGRenderer.translate((1f - e) * 18f * tabDir, -curScroll)
        
        val used = when (tab) {
            Tab.OVERVIEW -> drawOverview(contentX, contentY, contentW, p)
            Tab.TRANSCENDENCE -> drawTranscendence(contentX, contentY, contentW)
            Tab.CHARACTERS -> drawCharacters(contentX, contentY, contentW)
            Tab.COMPANIONS -> drawCompanions(contentX, contentY, contentW)
            Tab.STASH -> drawStash(contentX, contentY, contentW)
        }
        
        NVGRenderer.pop()
        NVGRenderer.popScissor()
        popAlpha(prevA)
        
        contentHeight[ti] = used
        scrollTarget[ti] = scrollTarget[ti].coerceIn(0f, max(0f, used - contentH))
        scroll[ti] = scroll[ti].coerceIn(0f, max(0f, used - contentH))
        
        drawScrollThumb(used, scroll[ti])
    }
    
    // ==================== OVERVIEW ====================
    
    private fun drawOverview(bx: Float, by: Float, bw: Float, p: TelosProfile): Float {
        var cy = by
        
        // Stat cards (3 per row, 2 rows), formatted once in buildDerived.
        // Each card enters with a small stagger and rise; hover eases in and lifts the card.
        val cols = 3
        val gap = 8f
        val cardW = (bw - gap * (cols - 1)) / cols
        val cardH = 50f
        val sinceTab = time() - tabSwitchedAt
        overviewCards.forEachIndexed { i, (label, value, color) ->
            val cx = bx + (i % cols) * (cardW + gap)
            val ryBase = cy + (i / cols) * (cardH + gap)
            val ce = enter(sinceTab, i * STAGGER, 0.18f)
            val hf = hoverFrac("ov$i", visibleHover(cx, ryBase, cardW, cardH))
            val ry = ryBase + (1f - ce) * 8f - hf * 1.5f
            val prev = pushAlpha(ce)
            NVGRenderer.rect(cx, ry, cardW, cardH, blend(PANEL_HI, PANEL, hf), 12f)
            NVGRenderer.hollowRect(cx, ry, cardW, cardH, 1f, blend(ACCENT_DIM, STROKE, hf), 12f)
            if (hf > 0.02f) glow(cx, ry, cardW, cardH, ACCENT, 12f, 0.6f * hf)
            // Hairline along the card top, tinted in the stat's own color.
            NVGRenderer.gradientRect(cx + 12f, ry, cardW * 0.45f, 2f, withAlpha(color, 0.9f), withAlpha(color, 0f), Gradient.LeftToRight, 1f)
            diamond(cx + 13f, ry + 12f, 5f, withAlpha(color, 0.9f))
            NVGRenderer.text(label.uppercase(), cx + 21f, ry + 7f, 9f, MUTE, font)
            NVGRenderer.text(value, cx + 12f, ry + 22f, 15f, color, font)
            popAlpha(prev)
        }
        cy += 2 * (cardH + gap) + 8f
        
        // Other details.
        diamond(bx + 5f, cy + 5f, 6f, ACCENT_DIM)
        NVGRenderer.text("Other details", bx + 14f, cy, 11f, SUBTEXT, font)
        cy += 18f
        val passLabel = p.seasonPass?.let { (if (it.premium) "Premium" else "Free") + "  •  ${compact(it.experience)} XP" } ?: "None"
        val passColor = if (p.seasonPass?.premium == true) GOLD else SUBTEXT
        cy = detailRow(bx, cy, bw, "Last Seen", relativeTime(p.lastPlayed), TEXT)
        cy = detailRow(bx, cy, bw, "Season Pass", passLabel, passColor)
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
        NVGRenderer.line(bx + 2f, y + h - 0.5f, bx + bw - 2f, y + h - 0.5f, 0.5f, ROW_LINE)
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
        NVGRenderer.rect(x, y, w, h, PANEL, 12f)
        NVGRenderer.hollowRect(x, y, w, h, 1f, STROKE, 12f)
        
        val xp = p.seasonPass?.experience ?: 0L
        val premium = p.seasonPass?.premium ?: false
        
        NVGRenderer.text("SEASON PASS", x + 12f, y + 9f, 9f, MUTE, font)
        val passTag = if (premium) "Premium" else "Free"
        NVGRenderer.text(passTag, x + w - 12f - NVGRenderer.textWidth(passTag, 9f, font), y + 9f, 9f, if (premium) GOLD else SUBTEXT, font)
        
        val tile = seasonTileSize(w)
        val gridTop = y + 26f
        val page = SeasonPassData.page(seasonPage)
        
        // The grid slides in from the flip direction and fades up on page change.
        val fe = enter(time() - seasonFlipAt, 0f, 0.20f)
        val flipOff = (1f - fe) * 14f * seasonDir
        val prevA = pushAlpha(0.25f + 0.75f * fe)
        for (i in 0 until SeasonPassData.PER_PAGE) {
            val col = i % 5
            val row = i / 5
            val tx = x + 12f + col * (tile + 8f) + flipOff
            val ty = gridTop + row * (tile + 8f)
            val reward = page.getOrNull(i)
            if (reward == null) {
                NVGRenderer.rect(tx, ty, tile, tile, WELL, 6f)
                continue
            }
            drawSeasonTile(reward, tx, ty, tile, xp, premium)
        }
        popAlpha(prevA)
        
        // Paginator: arrows + page/xp/premium info.
        val arrowY = gridTop + 2 * (tile + 8f) + 4f
        val arrowSize = 22f
        val leftX = x + 12f
        val rightX = x + w - 12f - arrowSize
        drawArrow(leftX, arrowY, arrowSize, false, hoverFrac("spl", hover(leftX, arrowY - curScroll, arrowSize, arrowSize)))
        drawArrow(rightX, arrowY, arrowSize, true, hoverFrac("spr", hover(rightX, arrowY - curScroll, arrowSize, arrowSize)))
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
        
        NVGRenderer.rect(tx, ty, tile, tile, blend(accent, WELL, if (unlocked) 0.18f else 0.06f), 6f)
        NVGRenderer.hollowRect(tx, ty, tile, tile, 1f, border, 6f)
        
        val pad = 4f
        if (!NVGRenderer.texturedRect(reward.texture, tx + pad, ty + pad, tile - pad * 2, tile - pad * 2)) {
            val initial = reward.displayName.firstOrNull()?.uppercase() ?: "?"
            val s = (tile - pad * 2) * 0.7f
            NVGRenderer.text(initial, tx + (tile - NVGRenderer.textWidth(initial, s, font)) / 2f, ty + tile * 0.16f, s, accent, font)
        }
        
        if (!unlocked) {
            NVGRenderer.rect(tx, ty, tile, tile, LOCK, 6f)
            drawLockBadge(tx, ty, tile)
        }
        
        val hovered = visibleHover(tx, ty, tile, tile)
        val hf = hoverFrac("sp${reward.tier}${reward.premium}", hovered)
        if (hf > 0.02f) {
            NVGRenderer.hollowRect(tx, ty, tile, tile, 1.5f, withAlpha(accent, hf), 6f)
            glow(tx, ty, tile, tile, accent, 6f, 0.8f * hf)
        }
        if (hovered) {
            tooltip = "${reward.displayName}  •  Tier ${reward.tier}  •  ${if (unlocked) "Unlocked" else "${compact(reward.xpRequired)} XP"}" to accent
        }
    }
    
    /** Paginator arrow whose brand-gradient hover state eases in with fraction [f]. */
    private fun drawArrow(x: Float, y: Float, size: Float, right: Boolean, f: Float) {
        val r = size / 2f
        NVGRenderer.rect(x, y, size, size, PANEL_HI, r)
        NVGRenderer.hollowRect(x, y, size, size, 1f, STROKE, r)
        if (f > 0.02f) {
            brandGradient(x, y, size, size, r, f)
            glow(x, y, size, size, ACCENT, r, 0.8f * f)
        }
        val glyph = if (right) "›" else "‹"
        val gs = 16f
        NVGRenderer.text(glyph, x + (size - NVGRenderer.textWidth(glyph, gs, font)) / 2f, y + (size - gs) / 2f - 1f, gs, blend(INK, SUBTEXT, f), font)
    }
    
    /** Bottom-right panel: the player's currently equipped sticker (name + large texture). */
    private fun drawStickerPreview(x: Float, y: Float, w: Float, h: Float, p: TelosProfile) {
        NVGRenderer.rect(x, y, w, h, PANEL, 12f)
        NVGRenderer.hollowRect(x, y, w, h, 1f, STROKE, 12f)
        
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
        val shownName = ellipsize(name, w - 20f, nameSize)
        val nameW = NVGRenderer.textWidth(shownName, nameSize, font)
        val nameX = x + (w - nameW) / 2f
        NVGRenderer.text(shownName, nameX, y + 12f, nameSize, TEXT, font)
        brandGradient(nameX, y + 12f + nameSize + 3f, nameW, 1.5f, 0.75f)
        
        // Large sticker texture.
        val imgSize = min(w - 36f, h - 56f).coerceAtLeast(24f)
        val imgX = x + (w - imgSize) / 2f
        val imgY = y + 38f
        if (!NVGRenderer.texturedRect("telos:material/sticker/${id}.png", imgX, imgY, imgSize, imgSize)) {
            NVGRenderer.rect(imgX, imgY, imgSize, imgSize, WELL, 6f)
            val initial = name.firstOrNull()?.uppercase() ?: "?"
            NVGRenderer.text(initial, imgX + (imgSize - NVGRenderer.textWidth(initial, imgSize * 0.5f, font)) / 2f, imgY + imgSize * 0.22f, imgSize * 0.5f, MUTE, font)
        }
    }
    
    // ==================== TRANSCENDENCE ====================
    
    /** Soul point caps and class order, sourced from the remotely-updatable [ClassData]. */
    private val maxSoulPointsPerClass get() = ClassData.maxSoulPointsPerClass
    private val maxSoulPointsTotal get() = ClassData.maxSoulPointsTotal
    private val classOrder get() = ClassData.classOrder
    
    private fun drawTranscendence(bx: Float, by: Float, bw: Float): Float {
        var cy = by
        
        if (classesSorted.isEmpty()) {
            return drawEmptyState(bx, by, bw, "Transcendence", "No class data.")
        }
        
        // Header: total soul points across all classes out of the cap.
        val totalSp = classesSorted.sumOf { it.soulPoints }
        diamond(bx + 5f, cy + 5f, 6f, ACCENT_DIM)
        NVGRenderer.text("Total Soul Points", bx + 14f, cy, 11f, SUBTEXT, font)
        val totalLabel = "${commas(totalSp)} / ${commas(maxSoulPointsTotal)}"
        NVGRenderer.text(totalLabel, bx + bw - 2f - NVGRenderer.textWidth(totalLabel, 11f, font), cy, 11f, ACCENT, font)
        cy += 18f
        val barH = 9f
        val totalFrac = (totalSp.toDouble() / maxSoulPointsTotal).coerceIn(0.0, 1.0).toFloat()
        shimmerBar(bx + 2f, cy, bw - 4f, barH, totalFrac)
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
        
        val sinceTab = time() - tabSwitchedAt
        classOrder.forEachIndexed { i, type ->
            val cx = bx + (i % cols) * (cardW + gap)
            val ce = enter(sinceTab, i * STAGGER, 0.18f)
            val ry = gridTop + (i / cols) * (cardH + gap) + (1f - ce) * 8f
            val prev = pushAlpha(ce)
            drawClassCard(cx, ry, cardW, cardH, type, byType[type])
            popAlpha(prev)
        }
        
        return (gridTop + rows * cardH + (rows - 1) * gap) - by
    }
    
    private fun drawClassCard(x: Float, y: Float, w: Float, h: Float, type: String, c: PlayerClass?) {
        NVGRenderer.rect(x, y, w, h, PANEL, 12f)
        NVGRenderer.hollowRect(x, y, w, h, 1f, STROKE, 12f)
        
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
        if (maxed) NVGRenderer.rect(x + w - pad - badgeW, y + 10f, badgeW, 15f, GOLD, 7.5f)
        else brandGradient(x + w - pad - badgeW, y + 10f, badgeW, 15f, 7.5f)
        NVGRenderer.text(badge, x + w - pad - badgeW + 6f, y + 12.5f, 9f, INK, font)
        
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
        val frac = (c.soulPoints.toDouble() / maxSoulPointsPerClass).coerceIn(0.0, 1.0).toFloat()
        shimmerBar(x + pad, cy, barW, barH, frac)
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
        drawProfileSprite(type, frame, centerX, topY, h)
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
        "Ironman", "Group Ironman" -> IRONMAN
        else -> SUBTEXT
    }
    
    private fun drawCharacters(bx: Float, by: Float, bw: Float): Float {
        val chars = characterList
        if (chars == null) return drawEmptyState(bx, by, bw, "Characters", "Loading characters${dots()}")
        if (chars.isEmpty()) return drawEmptyState(bx, by, bw, "Characters", "No living characters.")
        
        var cy = by
        val rowH = 60f
        val sinceTab = time() - tabSwitchedAt
        charRects.clear()
        chars.forEachIndexed { i, ch ->
            val ce = enter(sinceTab, i * 0.03f, 0.18f)
            val prev = pushAlpha(ce)
            drawCharacterRow(bx, cy + (1f - ce) * 8f, bw, rowH, ch)
            popAlpha(prev)
            charRects.add(floatArrayOf(bx, cy - curScroll, bw, rowH) to ch.id)
            cy += rowH + 8f
        }
        return cy - by
    }
    
    private fun drawCharacterRow(bx: Float, yBase: Float, bw: Float, h: Float, ch: TelosCharacter) {
        val equipped = ch.id == equippedCharacterId
        val hf = hoverFrac("ch${ch.id}", visibleHover(bx, yBase, bw, h))
        val y = yBase - hf * 1.5f
        
        NVGRenderer.rect(bx, y, bw, h, blend(PANEL_HI, PANEL, hf), 12f)
        val borderColor = if (equipped) GOLD else blend(ACCENT_DIM, STROKE, hf)
        NVGRenderer.hollowRect(bx, y, bw, h, if (equipped) 1.5f else 1f, borderColor, 12f)
        if (hf > 0.02f) glow(bx, y, bw, h, if (equipped) GOLD else ACCENT, 12f, 0.6f * hf)
        
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
            NVGRenderer.text(tag, lx + 6f, y + 12.5f, 8f, INK, font)
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
            if (key != null) drawTile(TelosItems.resolve(key), ix, iy, itemSize, item) else drawEmptyTile(ix, iy, itemSize)
            ix += itemSize + itemGap
        }
    }
    
    // ==================== CHARACTER DETAIL ====================
    
    /** Header (back arrow + sub-tabs) and body for a single character's detail view. */
    private fun drawCharacterDetail() {
        val gap = 6f
        val arrowW = 30f
        val mainRight = contentX + contentW
        val r = tabBarH / 2f
        
        // The whole detail view fades in over the list it replaced.
        val eOpen = enter(time() - detailOpenedAt)
        val prevOpen = pushAlpha(eOpen)
        
        // Back arrow.
        backRect = floatArrayOf(contentX, tabBarY, arrowW, tabBarH)
        val bf = hoverFrac("back", hover(contentX, tabBarY, arrowW, tabBarH))
        NVGRenderer.rect(contentX, tabBarY, arrowW, tabBarH, PANEL, r)
        NVGRenderer.hollowRect(contentX, tabBarY, arrowW, tabBarH, 1f, STROKE, r)
        if (bf > 0.02f) {
            brandGradient(contentX, tabBarY, arrowW, tabBarH, r, bf)
            glow(contentX, tabBarY, arrowW, tabBarH, ACCENT, r, 0.8f * bf)
        }
        val glyph = "‹"
        NVGRenderer.text(glyph, contentX + (arrowW - NVGRenderer.textWidth(glyph, 18f, font)) / 2f, tabBarY + (tabBarH - 18f) / 2f - 1f, 18f, blend(INK, SUBTEXT, bf), font)
        
        // Sub-tabs fill the remaining width; the active pill glides between them.
        val tabsX = contentX + arrowW + gap
        val n = DetailTab.entries.size
        val tw = (mainRight - tabsX - gap * (n - 1)) / n
        DetailTab.entries.forEachIndexed { i, t ->
            val tabX = tabsX + (tw + gap) * i
            detailTabRects[i] = floatArrayOf(tabX, tabBarY, tw, tabBarH)
            if (t == detailTab) return@forEachIndexed
            val f = hoverFrac("dtab$i", hover(tabX, tabBarY, tw, tabBarH))
            NVGRenderer.rect(tabX, tabBarY, tw, tabBarH, blend(PANEL_HI, PANEL, f), r)
            NVGRenderer.hollowRect(tabX, tabBarY, tw, tabBarH, 1f, blend(ACCENT_DIM, STROKE, f), r)
        }
        
        tickPill(detailPill, tabsX + (tw + gap) * detailTab.ordinal, tw)
        brandGradient(detailPill.x, tabBarY, detailPill.w, tabBarH, r)
        glow(detailPill.x, tabBarY, detailPill.w, tabBarH, ACCENT, r)
        
        DetailTab.entries.forEachIndexed { i, t ->
            val tabX = tabsX + (tw + gap) * i
            val overlap = (min(tabX + tw, detailPill.x + detailPill.w) - max(tabX, detailPill.x)).coerceIn(0f, tw) / tw
            val base = if (hover(tabX, tabBarY, tw, tabBarH)) ACCENT_SOFT else SUBTEXT
            val size = fitText(t.label, tw - 8f, 11.5f, 8.5f)
            NVGRenderer.text(t.label, tabX + (tw - NVGRenderer.textWidth(t.label, size, font)) / 2f, tabBarY + (tabBarH - size) / 2f, size, blend(INK, base, overlap), font)
        }
        
        val d = characterDetail
        when {
            detailLoading -> drawCenteredInContent("Loading character${dots()}", SUBTEXT)
            detailError != null -> detailRetryRect = drawRetry(contentX + contentW / 2f, contentY + contentH / 2f, detailError!!)
            d != null -> drawDetailBody(d)
        }
        popAlpha(prevOpen)
    }
    
    private fun drawCenteredInContent(text: String, color: Int) {
        val w = NVGRenderer.textWidth(text, 13f, font)
        NVGRenderer.text(text, contentX + (contentW - w) / 2f, contentY + contentH / 2f - 6f, 13f, color, font)
    }
    
    private fun drawDetailBody(d: TelosCharacterDetail) {
        bodyTop = contentY
        bodyBottom = contentY + contentH
        val ti = detailTab.ordinal
        smoothScroll(detailScroll, detailScrollTarget, ti, detailContentHeight[ti])
        curScroll = detailScroll[ti]
        
        // Sub-tab content slides from the direction it was approached.
        val e = enter(time() - detailTabSwitchedAt)
        val prevA = pushAlpha(e)
        
        NVGRenderer.pushScissor(contentX, contentY, contentW, contentH)
        NVGRenderer.push()
        NVGRenderer.translate((1f - e) * 16f * detailTabDir, -curScroll)
        
        val used = when (detailTab) {
            DetailTab.OVERVIEW -> drawDetailOverview(contentX, contentY, contentW, d)
            DetailTab.SKILLS -> drawSkillTree(contentX, contentY, contentW, d)
            DetailTab.BACKPACK -> drawBackpack(contentX, contentY, contentW, d)
        }
        
        NVGRenderer.pop()
        NVGRenderer.popScissor()
        popAlpha(prevA)
        
        detailContentHeight[ti] = used
        detailScrollTarget[ti] = detailScrollTarget[ti].coerceIn(0f, max(0f, used - contentH))
        detailScroll[ti] = detailScroll[ti].coerceIn(0f, max(0f, used - contentH))
        
        drawScrollThumb(used, detailScroll[ti])
    }
    
    private fun drawDetailOverview(bx: Float, by: Float, bw: Float, d: TelosCharacterDetail): Float {
        var cy = by
        val inv = d.inventory ?: emptyList()
        fun slot(i: Int): StashItem? = inv.getOrNull(i)?.takeIf { it.key != null }

        // Equipped gear.
        diamond(bx + 5f, cy + 4f, 5f, ACCENT_DIM)
        NVGRenderer.text("EQUIPPED", bx + 13f, cy, 9f, MUTE, font)
        cy += 15f
        val equipped = listOf(
            "Main" to slot(d.hotBarSlot), "Off" to slot(40),
            "Helmet" to slot(39), "Chest" to slot(38), "Legs" to slot(37), "Boots" to slot(36)
        )
        val eqSize = 44f
        val eqGap = 10f
        equipped.forEachIndexed { i, (label, item) ->
            val ex = bx + i * (eqSize + eqGap)
            val key = item?.key
            if (key != null) drawTile(TelosItems.resolve(key), ex, cy, eqSize, item) else drawEmptyTile(ex, cy, eqSize)
            NVGRenderer.text(label, ex + (eqSize - NVGRenderer.textWidth(label, 9f, font)) / 2f, cy + eqSize + 3f, 9f, MUTE, font)
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
        diamond(bx + 5f, cy + 4f, 5f, ACCENT_DIM)
        NVGRenderer.text("INVENTORY", bx + 13f, cy, 9f, MUTE, font)
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
            if (key != null) drawTile(TelosItems.resolve(key), tx, ty, tile, item) else drawEmptyTile(tx, ty, tile)
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
        val active = branches.firstOrNull { row -> row?.any { it != null } == true }
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
        NVGRenderer.rect(x, y, size, size, if (taken) blend(ACCENT, WELL, 0.25f) else PANEL, 12f)
        NVGRenderer.hollowRect(x, y, size, size, if (taken) 2f else 1f, if (taken) ACCENT else STROKE, 12f)
        if (taken) glow(x, y, size, size, ACCENT, 12f, if (reduceMotion) 0.85f else 0.7f + 0.3f * sin(time() * 2.5f))
    }
    
    private fun drawBackpack(bx: Float, by: Float, bw: Float, d: TelosCharacterDetail): Float {
        val bp = d.backpack ?: emptyList()
        val count = bp.count { it?.key != null }
        diamond(bx + 5f, by + 4f, 5f, ACCENT_DIM)
        NVGRenderer.text("BACKPACK  •  $count items", bx + 13f, by, 9f, MUTE, font)
        if (bp.isEmpty()) {
            NVGRenderer.text("Backpack is empty.", bx + 2f, by + 18f, 12f, MUTE, font)
            return 36f
        }
        return drawSlotGrid(bx, by + 16f, bw, bp) - by
    }
    
    // ==================== OTHER TABS ====================
    
    private fun drawEmptyState(bx: Float, by: Float, bw: Float, title: String, subtitle: String): Float {
        val cy = by + contentH / 2f - 20f
        val tw = NVGRenderer.textWidth(title, 14f, font)
        sparkle(bx + (bw - tw) / 2f - 16f, cy + 7f, 9f, ACCENT, 1.2f)
        sparkle(bx + (bw + tw) / 2f + 16f, cy + 7f, 9f, ACCENT, 3.9f)
        NVGRenderer.text(title, bx + (bw - tw) / 2f, cy, 14f, SUBTEXT, font)
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
        if (CompanionData.starterRank(id) != null) return if (id.startsWith("mount/")) "Rolo" else "Gelato"
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
        NVGRenderer.rect(x, y, w, h, PANEL, 12f)
        NVGRenderer.hollowRect(x, y, w, h, 1f, STROKE, 12f)
        
        // Skin slot on the right.
        val slot = h - 18f
        val slotX = x + w - 12f - slot
        val slotY = y + (h - slot) / 2f
        NVGRenderer.rect(slotX, slotY, slot, slot, PANEL_HI, 6f)
        NVGRenderer.hollowRect(slotX, slotY, slot, slot, 1f, STROKE, 6f)
        val skinResolved = skinRaw?.let { TelosItems.resolve(it) }
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
            NVGRenderer.text(title.uppercase(), x + 12f, y + 10f, 9f, MUTE, font)
            NVGRenderer.text("None equipped", x + 12f, y + 28f, 11f, MUTE, font)
            return
        }
        val resolved = TelosItems.resolve(equippedId)
        drawIcon(resolved, x + 11f, y + (h - 36f) / 2f, 36f)
        val tx = x + 56f
        NVGRenderer.text(title.uppercase(), tx, y + 11f, 9f, MUTE, font)
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
        
        diamond(bx + 5f, cy + 5f, 6f, ACCENT)
        NVGRenderer.text(title.uppercase(), bx + 14f, cy, 11f, TEXT, font)
        val count = "$unlockedCount / ${list.size - 7}"
        NVGRenderer.text(count, bx + bw - 2f - NVGRenderer.textWidth(count, 11f, font), cy, 11f, MUTE, font)
        cy += 18f
        
        CompanionData.Rarity.entries.forEach { rarity ->
            val group = list.filter { it.rarity == rarity }
            if (group.isEmpty()) return@forEach
            diamond(bx + 6f, cy + 5f, 7f, rarity.color)
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
        
        NVGRenderer.rect(x, y, size, size, blend(rc, WELL, if (unlocked) 0.30f else 0.10f), 6f)
        NVGRenderer.hollowRect(x, y, size, size, 1f, if (unlocked) rc else blend(rc, CARD, 0.4f), 6f)
        drawIcon(resolved, x + 4f, y + 4f, size - 8f)
        if (!unlocked) {
            NVGRenderer.rect(x, y, size, size, LOCK, 6f)
            drawLockBadge(x, y, size)
        }
        if (equipped) NVGRenderer.hollowRect(x, y, size, size, 2f, GOLD, 6f)
        
        val hovered = visibleHover(x, y, size, size)
        val hf = hoverFrac("c${comp.id}", hovered)
        if (hf > 0.02f) {
            NVGRenderer.hollowRect(x, y, size, size, 2f, withAlpha(ACCENT, hf), 6f)
            glow(x, y, size, size, ACCENT, 6f, 0.8f * hf)
        }
        if (hovered) {
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
    
    private fun drawStashPage(px: Float, py: Float, label: String, slots: List<StashItem?>, cols: Int, rows: Int, tile: Float, gap: Float) {
        NVGRenderer.text(label, px + 2f, py, 10f, SUBTEXT, font)
        val gridTop = py + 15f
        for (i in 0 until cols * rows) {
            val tx = px + (i % cols) * (tile + gap)
            val ty = gridTop + (i / cols) * (tile + gap)
            val item = slots.getOrNull(i)
            val key = item?.key
            if (key != null) drawTile(TelosItems.resolve(key), tx, ty, tile, item) else drawEmptyTile(tx, ty, tile)
        }
    }
    
    // ==================== TILES ====================
    
    private fun drawEmptyTile(x: Float, y: Float, size: Float) {
        NVGRenderer.rect(x, y, size, size, WELL, 6f)
        NVGRenderer.hollowRect(x, y, size, size, 1f, STROKE, 6f)
    }
    
    private fun drawTile(item: TelosItems.Resolved, x: Float, y: Float, size: Float = TILE, stack: StashItem? = null) {
        NVGRenderer.rect(x, y, size, size, blend(item.rarityColor, WELL, 0.82f), 6f)
        NVGRenderer.hollowRect(x, y, size, size, 1f, blend(item.rarityColor, CARD, 0.45f), 6f)
        drawIcon(item, x + 3f, y + 3f, size - 6f)

        val hovered = visibleHover(x, y, size, size)
        val hf = hoverFrac("t${x.toInt()},${y.toInt()}", hovered)
        if (hf > 0.02f) {
            NVGRenderer.hollowRect(x, y, size, size, 1.5f, withAlpha(ACCENT, hf), 6f)
            glow(x, y, size, size, ACCENT, 6f, 0.8f * hf)
        }
        if (hovered) {
            val rarity = item.rarity?.name?.lowercase()?.replaceFirstChar { it.uppercaseChar() }
            val title = if (rarity != null) "${item.displayName}  ($rarity)" else item.displayName
            itemTip = ItemTip(title, item.rarityColor, stack?.let { traitLines(it) } ?: emptyList())
        }
    }

    /**
     * Builds the trait lore lines for an item, one per slot (lowest slot first). Each line is a
     * tier badge (D/C/B/A/S) followed by the trait's coloured description.
     */
    private fun traitLines(stack: StashItem): List<List<TraitData.Run>> =
        stack.traitKeys()
            .mapNotNull { TraitData.get(it) }
            .sortedBy { it.slot }
            .map { t -> listOf(TraitData.Run("${TraitData.tierLetter(t.tier)}  ", TraitData.tierColor(t.tier))) + t.runs() }
    
    private fun drawIcon(item: TelosItems.Resolved, x: Float, y: Float, size: Float) {
        if (!NVGRenderer.texturedRect(item.textureResource, x, y, size, size)) {
            val initial = item.displayName.firstOrNull()?.uppercase() ?: "?"
            NVGRenderer.text(initial, x + (size - NVGRenderer.textWidth(initial, size * 0.7f, font)) / 2f, y + size * 0.12f, size * 0.7f, item.rarityColor, font)
        }
    }
    
    private fun drawTooltip(text: String, color: Int) {
        // Quick fade-and-rise when the tooltip first appears or its target changes.
        if (text != lastTooltipText) {
            lastTooltipText = text
            tooltipAt = time()
        }
        val a = enter(time() - tooltipAt, 0f, 0.14f)
        val tw = NVGRenderer.textWidth(text, 11f, font)
        val w = tw + 16f
        val h = 22f
        var tx = sx + 12f
        var ty = sy + 12f + (1f - a) * 3f
        val vW = mc.window.screenWidth / scaleNow
        if (tx + w > vW) tx = sx - w - 12f
        if (ty + h > mc.window.screenHeight / scaleNow) ty = sy - h - 12f
        val prev = pushAlpha(a)
        NVGRenderer.dropShadow(tx, ty, w, h, 10f, 1f, 11f)
        NVGRenderer.rect(tx, ty, w, h, PANEL_HI, 11f)
        NVGRenderer.hollowRect(tx, ty, w, h, 1f, blend(color, STROKE, 0.6f), 11f)
        glow(tx, ty, w, h, color, 11f, 0.6f)
        NVGRenderer.text(text, tx + 8f, ty + 5.5f, 11f, TEXT, font)
        popAlpha(prev)
    }
    
    // ==================== INPUT ====================
    
    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        val scale = ClickGUIModule.getStandardGuiScale()
        val mx = rawMouseX / scale
        val my = rawMouseY / scale
        
        // Failed profile fetch: only the Retry pill is interactive.
        if (errorMsg != null) {
            retryRect?.let { if (inRect(mx, my, it)) { retryFetch(); return true } }
            return super.mouseClicked(mouseButtonEvent, bl)
        }
        
        // Character detail view captures input on its own header.
        if (viewingCharacterId != null) {
            if (detailError != null) {
                detailRetryRect?.let { if (inRect(mx, my, it)) { retryDetail(); return true } }
            }
            backRect?.let { if (inRect(mx, my, it)) { closeCharacter(); return true } }
            DetailTab.entries.forEachIndexed { i, t ->
                detailTabRects[i]?.let { if (inRect(mx, my, it)) { switchDetailTab(t); return true } }
            }
            return super.mouseClicked(mouseButtonEvent, bl)
        }
        
        Tab.entries.forEachIndexed { i, t ->
            tabRects[i]?.let { r ->
                if (inRect(mx, my, r)) {
                    switchTab(t)
                    return true
                }
            }
        }
        
        if (tab == Tab.OVERVIEW) {
            spLeftRect?.let { if (inRect(mx, my, it)) { flipSeason(-1); return true } }
            spRightRect?.let { if (inRect(mx, my, it)) { flipSeason(1); return true } }
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
        // Wheel input moves a target; the visible scroll glides toward it each frame.
        if (viewingCharacterId != null) {
            val ti = detailTab.ordinal
            val maxScroll = max(0f, detailContentHeight[ti] - contentH)
            if (maxScroll > 0f) {
                detailScrollTarget[ti] = (detailScrollTarget[ti] - verticalAmount.sign.toFloat() * 56f).coerceIn(0f, maxScroll)
                return true
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }
        val ti = tab.ordinal
        val maxScroll = max(0f, contentHeight[ti] - contentH)
        if (maxScroll > 0f) {
            scrollTarget[ti] = (scrollTarget[ti] - verticalAmount.sign.toFloat() * 56f).coerceIn(0f, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }
    
    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        val key = keyEvent.key
        
        // Retry whenever an error is showing.
        if (errorMsg != null && (key == GLFW.GLFW_KEY_R || key == GLFW.GLFW_KEY_ENTER)) { retryFetch(); return true }
        
        if (viewingCharacterId != null) {
            if (detailError != null && (key == GLFW.GLFW_KEY_R || key == GLFW.GLFW_KEY_ENTER)) { retryDetail(); return true }
            when (key) {
                GLFW.GLFW_KEY_BACKSPACE -> { closeCharacter(); return true }
                GLFW.GLFW_KEY_LEFT -> { cycleDetailTab(-1); return true }
                GLFW.GLFW_KEY_RIGHT -> { cycleDetailTab(1); return true }
                in GLFW.GLFW_KEY_1..GLFW.GLFW_KEY_3 -> { switchDetailTab(DetailTab.entries[key - GLFW.GLFW_KEY_1]); return true }
            }
            return super.keyPressed(keyEvent)
        }
        
        when (key) {
            GLFW.GLFW_KEY_LEFT -> { cycleTab(-1); return true }
            GLFW.GLFW_KEY_RIGHT -> { cycleTab(1); return true }
            in GLFW.GLFW_KEY_1..GLFW.GLFW_KEY_5 -> { switchTab(Tab.entries[key - GLFW.GLFW_KEY_1]); return true }
        }
        // On Overview, Page Up/Down flip the season pass without clashing with tab cycling.
        if (tab == Tab.OVERVIEW) when (key) {
            GLFW.GLFW_KEY_PAGE_UP -> { flipSeason(-1); return true }
            GLFW.GLFW_KEY_PAGE_DOWN -> { flipSeason(1); return true }
        }
        return super.keyPressed(keyEvent)
    }
    
    /** Cycles with wrap-around; the slide direction follows the arrow that was pressed. */
    private fun cycleTab(dir: Int) {
        tab = Tab.entries[(tab.ordinal + dir + Tab.entries.size) % Tab.entries.size]
        tabDir = dir
        tabSwitchedAt = time()
    }
    
    private fun cycleDetailTab(dir: Int) {
        detailTab = DetailTab.entries[(detailTab.ordinal + dir + DetailTab.entries.size) % DetailTab.entries.size]
        detailTabDir = dir
        detailTabSwitchedAt = time()
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
    
    /** Truncates [text] with a trailing ellipsis so it fits [maxWidth] at [size]. */
    private fun ellipsize(text: String, maxWidth: Float, size: Float): String {
        if (NVGRenderer.textWidth(text, size, font) <= maxWidth) return text
        var s = text
        while (s.isNotEmpty() && NVGRenderer.textWidth("$s…", size, font) > maxWidth) s = s.dropLast(1)
        return if (s.isEmpty()) "…" else "$s…"
    }
    
    private fun dots(): String = ".".repeat(((System.currentTimeMillis() / 400) % 4).toInt())
    
    private fun prettySkin(skin: String?): String? {
        if (skin.isNullOrBlank()) return null
        return skin.substringAfterLast('/').removePrefix("skin-")
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

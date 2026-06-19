package me.melinoe.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import me.melinoe.Melinoe
import me.melinoe.utils.data.ItemDefinitions
import me.melinoe.utils.data.TraitData
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture

/**
 * Fetches Telos Realms player profiles from the Melinoe API
 */
object ProfileFetcher {
    
    private const val PLAYER_URL = "https://melinoe.magnetite.dev/api/telos/player/"
    private const val CHARACTER_URL = "https://melinoe.magnetite.dev/api/telos/character/"
    private const val TRAITS_URL = "https://melinoe.magnetite.dev/api/telos/global/definitions/item-traits"
    private const val ITEMS_URL = "https://melinoe.magnetite.dev/api/telos/global/definitions/items"

    /** Where the cached definition payloads live (config/melinoe/data/definitions/). */
    private val defsDir: File get() = File(File(Melinoe.configFile, "data"), "definitions")
    
    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }
    
    private val gson = Gson()
    
    /**
     * Fetches a player's profile by username in the background. The returned future fails if
     * the player isn't found or the request errors.
     */
    fun fetch(username: String): CompletableFuture<TelosProfile> {
        return CompletableFuture.supplyAsync {
            val encoded = URLEncoder.encode(username, StandardCharsets.UTF_8)
            val authed = authedGet("${PLAYER_URL}username/$encoded?condensed=false")
            
            when (authed.statusCode()) {
                200 -> {
                    val profile = gson.fromJson(authed.body(), TelosProfile::class.java)
                        ?: throw ProfileException("Received empty response from the API.")
                    if (profile.username == null) {
                        throw ProfileException("No profile data found for \"$username\".")
                    }
                    profile
                }
                401 -> throw ProfileException("Authentication was rejected. Please try again.")
                404 -> throw ProfileException("Player \"$username\" was not found.")
                else -> {
                    Melinoe.logger.warn("[ProfileFetcher] Unexpected status ${authed.statusCode()} for $username")
                    throw ProfileException("The API returned an error (HTTP ${authed.statusCode()}).")
                }
            }
        }
    }
    
    /**
     * Fetches a player's characters by UUID in the background. Returns an empty list on a 404 so a
     * missing character list doesn't fail the whole profile view.
     */
    fun fetchCharacters(uuid: String): CompletableFuture<List<TelosCharacter>> {
        return CompletableFuture.supplyAsync {
            val encoded = URLEncoder.encode(uuid, StandardCharsets.UTF_8)
            val authed = authedGet("$PLAYER_URL$encoded/characters")
            
            when (authed.statusCode()) {
                200 -> gson.fromJson(authed.body(), Array<TelosCharacter>::class.java)?.toList() ?: emptyList()
                404 -> emptyList()
                else -> {
                    Melinoe.logger.warn("[ProfileFetcher] Unexpected status ${authed.statusCode()} for characters of $uuid")
                    throw ProfileException("The API returned an error (HTTP ${authed.statusCode()}).")
                }
            }
        }
    }
    
    /**
     * Fetches a single character's full data by character UUID in the background.
     * Endpoint: .../character/<id>?condensed=false
     */
    fun fetchCharacter(id: String): CompletableFuture<TelosCharacterDetail> {
        return CompletableFuture.supplyAsync {
            val encoded = URLEncoder.encode(id, StandardCharsets.UTF_8)
            val authed = authedGet("${CHARACTER_URL}$encoded?condensed=false")
            
            when (authed.statusCode()) {
                200 -> gson.fromJson(authed.body(), TelosCharacterDetail::class.java)
                    ?: throw ProfileException("Received empty response from the API.")
                404 -> throw ProfileException("Character not found.")
                else -> {
                    Melinoe.logger.warn("[ProfileFetcher] Unexpected status ${authed.statusCode()} for character $id")
                    throw ProfileException("The API returned an error (HTTP ${authed.statusCode()}).")
                }
            }
        }
    }
    
    /**
     * Loads the item and item-trait definitions once per session in the background. Each is read
     * from its local cache while that cache is still fresh
     */
    fun loadDefinitions(): CompletableFuture<Void> = CompletableFuture.runAsync {
        if (!TraitData.loaded) loadDefinition("item-traits.json", TRAITS_URL) { TraitData.load(it) }
        if (!ItemDefinitions.loaded) loadDefinition("items.json", ITEMS_URL) { ItemDefinitions.load(it) }
    }

    /**
     * Applies [apply] to a definition payload, preferring a fresh local cache and otherwise fetching
     * from [url] and re-caching it.
     */
    private fun loadDefinition(fileName: String, url: String, apply: (String) -> Boolean) {
        val file = File(defsDir, fileName)
        try {
            if (file.isFile && System.currentTimeMillis() < expiry(file.lastModified())) {
                val cached = runCatching { file.readText() }.getOrNull()
                if (cached != null && apply(cached)) {
                    Melinoe.logger.info("[ProfileFetcher] Loaded $fileName from cache.")
                    return
                }
            }
            val authed = authedGet(url)
            if (authed.statusCode() == 200) {
                if (apply(authed.body())) {
                    runCatching { defsDir.mkdirs(); file.writeText(authed.body()) }
                        .onFailure { Melinoe.logger.warn("[ProfileFetcher] Failed to cache $fileName: ${it.message}") }
                    Melinoe.logger.info("[ProfileFetcher] Fetched $fileName from API.")
                }
            } else {
                Melinoe.logger.warn("[ProfileFetcher] $fileName returned HTTP ${authed.statusCode()}")
            }
        } catch (e: Exception) {
            Melinoe.logger.warn("[ProfileFetcher] Failed to load $fileName: ${e.message}")
        }
    }

    /** The instant a cache written at [fetchedAt] goes stale: the first 01:00 GMT strictly after it. */
    private fun expiry(fetchedAt: Long): Long {
        val fetched = Instant.ofEpochMilli(fetchedAt)
        var oneAm = fetched.atZone(ZoneOffset.UTC).toLocalDate().atTime(1, 0).toInstant(ZoneOffset.UTC)
        if (!oneAm.isAfter(fetched)) oneAm = oneAm.plus(Duration.ofDays(1))
        return oneAm.toEpochMilli()
    }

    /** GETs [url] with the cached token, refreshing once on a 401. */
    private fun authedGet(url: String): HttpResponse<String> {
        val response = httpGet(url, MojangAuth.getToken())
        return if (response.statusCode() == 401) {
            MojangAuth.invalidate()
            httpGet(url, MojangAuth.getToken(forceRefresh = true))
        } else {
            response
        }
    }
    
    private fun httpGet(url: String, token: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}

/**
 * Thrown when a profile cannot be fetched or parsed. The message is safe to show to the user.
 */
class ProfileException(message: String) : RuntimeException(message)

/**
 * A Telos player profile from the API. Only the fields the profile viewer uses are listed;
 * anything else in the response is ignored by Gson.
 */
data class TelosProfile(
    val id: String? = null,
    val username: String? = null,
    val lastPlayed: Long = 0,
    val playTime: Long = 0,
    val normalBalance: Double = 0.0,
    val seasonalBalance: Double = 0.0,
    val sticker: String? = null,
    val companions: Companions? = null,
    val rewards: List<String>? = null,
    val character: String? = null,
    val characterSlots: Int = 0,
    val seasonPass: SeasonPass? = null,
    val stash: List<StashPage>? = null,
    val classes: List<PlayerClass>? = null
)

data class Companions(
    val pet: String? = null,
    val petSkin: String? = null,
    val mount: String? = null,
    val mountSkin: String? = null,
    val unlocked: List<String>? = null
)

data class SeasonPass(
    val premium: Boolean = false,
    val experience: Long = 0,
    val redeemed: List<Long>? = null
)

data class StashPage(
    val seasonal: Boolean = false,
    val page: Int = 0,
    val items: List<StashItem?>? = null
)

data class StashItem(
    val key: String? = null,
    val uuid: String? = null,
    val features: List<ItemFeature>? = null
) {
    /** The trait keys carried by this item (empty when it has no traits feature). */
    fun traitKeys(): List<String> =
        features?.firstOrNull { it.type == "traits" }?.traits ?: emptyList()
}

/** A feature attached to an item; only the "traits" type is consumed currently. */
data class ItemFeature(
    val type: String? = null,
    val traits: List<String>? = null
)

data class PlayerClass(
    val type: String? = null,
    val soulPoints: Long = 0,
    val transcendenceLevel: Int = 0,
    val maxFame: Long = 0,
    val totalFame: Long = 0,
    val playTime: Long = 0,
    val deaths: Int = 0,
    @SerializedName("skillTreeSelection") val skillTreeSelection: List<List<Int?>>? = null
)

/**
 * A single Telos character from the `/player/<uuid>/characters` endpoint. [deathTime] is null
 * while the character is alive.
 */
data class TelosCharacter(
    val id: String? = null,
    val playTime: Long = 0,
    val type: String? = null,
    val ruleset: String? = null,
    val group: String? = null,
    val fame: Long = 0,
    val inventory: CharacterInventory? = null,
    val deathTime: Long? = null,
    val deathCause: String? = null
)

data class CharacterInventory(
    val mainHand: StashItem? = null,
    val offHand: StashItem? = null,
    val helmet: StashItem? = null,
    val chestplate: StashItem? = null,
    val leggings: StashItem? = null,
    val boots: StashItem? = null
)

/**
 * Full data for one character from `/character/<id>`. [type] reuses [PlayerClass] (it carries the
 * class stats and skill tree). [inventory] is the flat player inventory: slots 0-35 are the main
 * inventory, 36-39 the armour (boots, leggings, chestplate, helmet), 40 the offhand.
 */
data class TelosCharacterDetail(
    val id: String? = null,
    val playerId: String? = null,
    val created: Long = 0,
    val lastPlayed: Long = 0,
    val playTime: Long = 0,
    val type: PlayerClass? = null,
    val ruleset: String? = null,
    val group: Long? = null,
    val potions: Int = 0,
    val fame: Long = 0,
    val totalFame: Long = 0,
    val highestFame: Long = 0,
    val inventory: List<StashItem?>? = null,
    val hotBarSlot: Int = 0,
    val backpack: List<StashItem?>? = null,
    val deathTime: Long? = null,
    val deathCause: String? = null,
    val previousLife: String? = null
)
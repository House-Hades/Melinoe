package me.melinoe.clickgui.settings

import com.google.gson.Gson
import com.google.gson.JsonElement

/**
 * Interface for settings that can be saved/loaded.
 */
interface Saving {
    /**
     * Used to update the setting from the json.
     */
    fun read(element: JsonElement, gson: Gson)

    /**
     * Used to create the json.
     */
    fun write(gson: Gson): JsonElement
}

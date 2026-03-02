package me.melinoe.features

import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Category system for organizing modules.
 */
@ConsistentCopyVisibility
data class Category private constructor(val name: String) {
    companion object {

        /**
         * Map containing all the categories, with the key being the name.
         */
        val categories: LinkedHashMap<String, Category> = linkedMapOf()

        @JvmField
        val COMBAT = custom(name = "Combat")
        @JvmField
        val VISUAL = custom(name = "Visual")
        @JvmField
        val TRACKING = custom(name = "Tracking")
        @JvmField
        val MISC = custom(name = "Misc")

        /**
         * Returns a category with name provided.
         *
         * If a category with the same name has already been made, it won't reallocate.
         * Otherwise, it will be added to [categories].
         */
        fun custom(name: String): Category {
            return categories.getOrPut(name) { Category(name) }
        }
    }
}

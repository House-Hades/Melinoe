package me.melinoe.utils

import java.util.*

/**
 * Extension function for number formatting.
 * Formats a number to a string with a specified number of decimal places.
 */
fun Number.toFixed(decimals: Int = 2): String =
    "%.${decimals}f".format(Locale.US, this)

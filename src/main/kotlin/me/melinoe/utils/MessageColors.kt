package me.melinoe.utils

/**
 * Centralized color constants for the unified messaging system.
 * 
 * All colors are in RGB format (0xRRGGBB).
 * 
 * ## Message Type Colors
 * - [SUCCESS]: Green (0x00FF00) - Used for success messages
 * - [ERROR]: Red (0xFF3333) - Used for error messages
 * - [WARNING]: Yellow (0xFFFF00) - Used for warning messages
 * - [INFO]: Cyan (0x55FFFF) - Used for info messages
 * 
 * ## UI Colors
 * - [DEV]: Bright red (0xFF3333) - Used for dev message prefix
 * - [SEPARATOR]: Dark gray (0x606060) - Used for separators and watermark
 * - [TEXT]: Light gray (0xAAAAAA) - Used for regular text
 * - [MUTED]: Gray (0x808080) - Used for muted/secondary text
 * - [PREFIX]: Gray (0x808080) - Used for prefixes
 */
object MessageColors {
    // Message type colors
    /**
     * Green color for success messages.
     * Used by [Message.success]
     */
    const val SUCCESS = 0x00FF00
    
    /**
     * Red color for error messages.
     * Used by [Message.error]
     */
    const val ERROR = 0xFF3333
    
    /**
     * Yellow color for warning messages.
     * Used by [Message.warning]
     */
    const val WARNING = 0xFFFF00
    
    /**
     * Cyan color for info messages.
     * Used by [Message.info]
     */
    const val INFO = 0x55FFFF
    
    // UI colors
    /**
     * Bright red color for dev message prefix.
     * Used by [Message.dev]
     */
    const val DEV = 0xFF3333
    
    /**
     * Dark gray color for separators and watermark.
     * Used by [Message.separator] and watermark prefix
     */
    const val SEPARATOR = 0x606060
    
    /**
     * Light gray color for regular text.
     * Used for general text content
     */
    const val TEXT = 0xAAAAAA
    
    /**
     * Gray color for muted/secondary text.
     * Used for less important information
     */
    const val MUTED = 0x808080
    
    /**
     * Gray color for prefixes.
     * Used for message prefixes and separators
     */
    const val PREFIX = 0x808080
}

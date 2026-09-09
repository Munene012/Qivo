package com.example.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AvatarFrameItem(
    val id: String,
    val name: String,
    val priceCoins: Long,
    val validityDays: Int = 7,
    val description: String,
    val category: String = "Frames",
    val isPopular: Boolean = false
)

data class UserOwnedFrame(
    val id: String = "",
    val userId: String,
    val frameId: String,
    val purchasedAt: String = "",
    val expiresAt: String,
    val isActive: Boolean = false,
    val pricePaid: Long = 0L
) {
    /**
     * Checks if this frame purchase is currently active and not expired
     */
    val isValid: Boolean
        get() {
            if (expiresAt.isBlank()) return true
            return try {
                val formats = listOf(
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSX", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") },
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd", Locale.US)
                )
                for (fmt in formats) {
                    try {
                        val exp = fmt.parse(expiresAt.trim())
                        if (exp != null) {
                            return exp.after(Date())
                        }
                    } catch (_: Exception) {}
                }
                true
            } catch (_: Exception) {
                true
            }
        }

    /**
     * Human-readable remaining time string (e.g., "6d 14h left", "23h left", "Expired")
     */
    val remainingTimeText: String
        get() {
            if (expiresAt.isBlank()) return "7 days left"
            try {
                val formats = listOf(
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSX", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") },
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd", Locale.US)
                )
                var expDate: Date? = null
                for (fmt in formats) {
                    try {
                        expDate = fmt.parse(expiresAt.trim())
                        if (expDate != null) break
                    } catch (_: Exception) {}
                }
                if (expDate == null) return "Valid 7 days"

                val diffMs = expDate.time - System.currentTimeMillis()
                if (diffMs <= 0) return "Expired"

                val days = TimeUnit.MILLISECONDS.toDays(diffMs)
                val hours = TimeUnit.MILLISECONDS.toHours(diffMs) % 24
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs) % 60

                return when {
                    days > 0 -> "${days}d ${hours}h left"
                    hours > 0 -> "${hours}h ${minutes}m left"
                    else -> "${minutes}m left"
                }
            } catch (_: Exception) {
                return "Valid 7 days"
            }
        }
}

object AvatarFrameManager {

    val ALL_FRAMES: List<AvatarFrameItem> = listOf(
        AvatarFrameItem(
            id = "vip4",
            name = "Amethyst Sovereign",
            priceCoins = 4990L,
            validityDays = 7,
            description = "Glorious violet crystal halo with angelic wings and luminous royal sovereign crest.",
            isPopular = true
        ),
        AvatarFrameItem(
            id = "svip1",
            name = "Diamond Luminary",
            priceCoins = 5990L,
            validityDays = 7,
            description = "Celestial diamond crystal wings with platinum star crown and sovereign emblem.",
            isPopular = true
        ),
        AvatarFrameItem(
            id = "acquaintance",
            name = "Aurora Starlight",
            priceCoins = 990L,
            validityDays = 7,
            description = "Luminous rose-gold star crest adorned with celestial feather wings.",
            isPopular = true
        ),
        AvatarFrameItem(
            id = "vip1",
            name = "Azure Sentinel",
            priceCoins = 1990L,
            validityDays = 7,
            description = "Silver crystal ring with azure gems and authentic knight crest.",
            isPopular = true
        ),
        AvatarFrameItem(
            id = "vip3",
            name = "Sapphire Phoenix",
            priceCoins = 3990L,
            validityDays = 7,
            description = "Radiant sapphire wing flame crown with royal phoenix emblem.",
            isPopular = true
        ),
        AvatarFrameItem(
            id = "helicopter",
            name = "Sky Aviator",
            priceCoins = 4990L,
            validityDays = 7,
            description = "Golden celestial ring with cute helicopter, pink heart gem and fluffy clouds.",
            isPopular = true
        ),
        AvatarFrameItem(
            id = "flowers",
            name = "Enchanted Flora",
            priceCoins = 290L,
            validityDays = 7,
            description = "Lush botanical floral wreath woven with blooming pastel roses, leaves & berries.",
            isPopular = true
        ),
        AvatarFrameItem(
            id = "golden_king",
            name = "Solar Monarch",
            priceCoins = 3990L,
            validityDays = 7,
            description = "Radiant solar sovereign aura with glowing crown spikes and gold wing crest.",
            isPopular = true
        ),
        AvatarFrameItem(
            id = "violet_eye",
            name = "Mystic Oculus",
            priceCoins = 3990L,
            validityDays = 7,
            description = "Majestic winged flame gold frame with a glowing magenta jewel eye and top crown."
        ),
        AvatarFrameItem(
            id = "lightning",
            name = "Volt Tempest",
            priceCoins = 1990L,
            validityDays = 7,
            description = "Futuristic cyber armor ring with pulsing electric cyan plasma arcs."
        ),
        AvatarFrameItem(
            id = "green_space",
            name = "Emerald Matrix",
            priceCoins = 1990L,
            validityDays = 7,
            description = "Holographic emerald sci-fi orbital ring with glowing tech nodes."
        ),
        AvatarFrameItem(
            id = "angel_wings",
            name = "Seraphim Grace",
            priceCoins = 3990L,
            validityDays = 7,
            description = "Heavenly golden halo flanked by pure white feathered angel wings and purple ribbon."
        ),
        AvatarFrameItem(
            id = "lion",
            name = "Imperial Leo",
            priceCoins = 4990L,
            validityDays = 7,
            description = "Imperial gold lion emperor head atop a baroque gold filigree shield."
        ),
        AvatarFrameItem(
            id = "cyber_dragon",
            name = "Crimson Drake",
            priceCoins = 3490L,
            validityDays = 7,
            description = "Neon crimson dragon aura with glowing embers and horn crest."
        ),
        AvatarFrameItem(
            id = "star_tiara",
            name = "Astral Diadem",
            priceCoins = 1490L,
            validityDays = 7,
            description = "Sparkling diamond stars and shimmering golden tiara ring."
        ),
        AvatarFrameItem(
            id = "new_user",
            name = "Newcomer Star",
            priceCoins = 0L,
            validityDays = 7,
            description = "Exclusive 7-day welcome avatar frame with verified 'NEW' tag given to every new member upon joining!",
            category = "Welcome",
            isPopular = true
        )
    )

    /**
     * Store frames available for purchase (excludes the exclusive welcome 'NEW' tag frame)
     */
    val STORE_FRAMES: List<AvatarFrameItem> = ALL_FRAMES.filter { it.id != "new_user" }

    /**
     * Automatically grants the official 7-Day 'New User' avatar frame with the 'NEW' tag
     * to a newly registered user and equips it on their avatar.
     */
    fun grantNewUserWelcomeFrame(context: android.content.Context?, userId: String) {
        if (userId.isBlank() || context == null) return
        try {
            val cleanId = userId.trim()
            val prefs = UserSessionManager.getPrefs(context) ?: return
            val grantKey = "granted_welcome_frame_$cleanId"
            
            // Check if already granted once
            if (prefs.getBoolean(grantKey, false)) return

            // Calculate exact expiration timestamp 7 days from now
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 7)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val expiresAt = sdf.format(calendar.time)

            // Save purchased/owned frame in session
            UserSessionManager.savePurchasedFrame(
                context = context,
                frameId = "new_user",
                expiresAt = expiresAt,
                pricePaid = 0L,
                targetUserId = cleanId
            )

            // Equip the frame immediately
            UserSessionManager.saveActiveFrame(
                context = context,
                frameId = "new_user",
                expiresAt = expiresAt,
                targetUserId = cleanId
            )

            // Mark granted
            prefs.edit().putBoolean(grantKey, true).apply()
        } catch (_: Exception) {}
    }

    /**
     * Returns empty list by default so no fake unbought frames are displayed.
     * Only frames genuinely purchased by the user appear in their Bag.
     */
    fun getDefaultStarterOwnedFrames(userId: String): List<UserOwnedFrame> {
        return emptyList()
    }

    fun getFrameById(id: String): AvatarFrameItem? {
        val clean = id.trim().lowercase()
        return ALL_FRAMES.firstOrNull { it.id.equals(clean, ignoreCase = true) }
    }

    fun isFrameValid(frameId: String, expiresAt: String): Boolean {
        if (frameId.isBlank()) return false
        if (expiresAt.isBlank()) return true
        return try {
            val formats = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSX", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") },
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
            )
            for (fmt in formats) {
                try {
                    val exp = fmt.parse(expiresAt.trim())
                    if (exp != null) {
                        return exp.after(Date())
                    }
                } catch (_: Exception) {}
            }
            true
        } catch (_: Exception) {
            true
        }
    }
}

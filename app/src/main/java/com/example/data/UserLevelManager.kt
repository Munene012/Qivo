package com.example.data

import android.content.Context
import androidx.compose.ui.graphics.Color

/**
 * User Level & EXP System
 * 
 * Rules:
 * - 1,000 coins = 1,000 EXP (1 coin = 1 EXP)
 * - Levels: 1 to 50
 * - Level 1 -> Level 2 requires 5,000 EXP
 * - Level 2 -> Level 3 requires 10,000 EXP (doubled)
 * - Each subsequent level doubles the EXP required from the previous level
 * - Profile Visitors list unlocked at Level 4+
 */
object UserLevelManager {
    const val MAX_LEVEL = 50
    const val MIN_LEVEL = 1
    const val BASE_EXP_REQ = 5000L // EXP needed to advance from Level 1 to Level 2
    const val VISITORS_UNLOCK_LEVEL = 4

    // Precomputed cumulative EXP required to REACH level N (1..50)
    // Level 1 = 0
    // Level 2 = 5,000
    // Level 3 = 15,000 (5,000 + 10,000)
    // Level 4 = 35,000 (15,000 + 20,000)
    // Level 5 = 75,000 (35,000 + 40,000)
    // Level N = 5000 * (2^(N-1) - 1)
    private val cumulativeThresholds: LongArray = LongArray(MAX_LEVEL + 1) { lvl ->
        if (lvl <= 1) 0L
        else {
            var sum = 0L
            var step = BASE_EXP_REQ
            for (k in 1 until lvl) {
                sum += step
                // Prevent overflow for 64-bit Long (up to lvl 50 step is 5000 * 2^49 ~ 2.81 * 10^18)
                step = if (step < Long.MAX_VALUE / 2) step shl 1 else Long.MAX_VALUE / 2
            }
            sum
        }
    }

    /**
     * EXP needed to progress from [level] to [level + 1]
     */
    fun getExpRequiredForLevel(level: Int): Long {
        if (level < 1 || level >= MAX_LEVEL) return 0L
        var step = BASE_EXP_REQ
        for (i in 1 until level) {
            step = if (step < Long.MAX_VALUE / 2) step shl 1 else Long.MAX_VALUE / 2
        }
        return step
    }

    /**
     * Total cumulative EXP threshold required to attain [level]
     */
    fun getLevelThreshold(level: Int): Long {
        val clamped = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
        return cumulativeThresholds[clamped]
    }

    /**
     * Calculates the user's level (1 to 50) based on cumulative EXP.
     */
    fun calculateLevel(totalExp: Long): Int {
        val safeExp = totalExp.coerceAtLeast(0L)
        for (lvl in MAX_LEVEL downTo 1) {
            if (safeExp >= cumulativeThresholds[lvl]) {
                return lvl
            }
        }
        return 1
    }

    /**
     * Returns true if the user's level or EXP qualifies them to see Visitors (Level 4+).
     */
    fun isVisitorsUnlocked(level: Int): Boolean = level >= VISITORS_UNLOCK_LEVEL
    fun isVisitorsUnlocked(totalExp: Long): Boolean = calculateLevel(totalExp) >= VISITORS_UNLOCK_LEVEL

    data class UserLevelInfo(
        val level: Int,
        val currentExp: Long,
        val currentLevelMinExp: Long,
        val nextLevelExp: Long,
        val expInCurrentLevel: Long,
        val expNeededInCurrentLevel: Long,
        val progressPercent: Float, // 0.0f .. 1.0f
        val isMaxLevel: Boolean,
        val title: String,
        val badgeColor: Color,
        val badgeGradient: List<Color>,
        val isVisitorsUnlocked: Boolean,
        val perks: List<String>
    )

    data class LevelMilestone(
        val level: Int,
        val title: String,
        val cumulativeExpReq: Long,
        val deltaExpReq: Long,
        val perks: List<String>,
        val badgeColor: Color,
        val badgeGradient: List<Color>
    )

    fun getLevelInfo(totalExp: Long): UserLevelInfo {
        val safeExp = totalExp.coerceAtLeast(0L)
        val level = calculateLevel(safeExp)
        val minExp = cumulativeThresholds[level]
        val isMax = level >= MAX_LEVEL
        val nextExp = if (isMax) minExp else cumulativeThresholds[level + 1]
        val expNeeded = if (isMax) 1L else (nextExp - minExp)
        val expInLevel = if (isMax) expNeeded else (safeExp - minExp)
        val progress = if (isMax) 1.0f else (expInLevel.toDouble() / expNeeded.toDouble()).toFloat().coerceIn(0f, 1f)

        val meta = getLevelMetadata(level)

        return UserLevelInfo(
            level = level,
            currentExp = safeExp,
            currentLevelMinExp = minExp,
            nextLevelExp = nextExp,
            expInCurrentLevel = expInLevel,
            expNeededInCurrentLevel = expNeeded,
            progressPercent = progress,
            isMaxLevel = isMax,
            title = meta.title,
            badgeColor = meta.badgeColor,
            badgeGradient = meta.badgeGradient,
            isVisitorsUnlocked = level >= VISITORS_UNLOCK_LEVEL,
            perks = meta.perks
        )
    }

    private data class LevelMeta(
        val title: String,
        val badgeColor: Color,
        val badgeGradient: List<Color>,
        val perks: List<String>
    )

    private fun getLevelMetadata(level: Int): LevelMeta {
        return when {
            level >= 50 -> LevelMeta(
                title = "Sovereign Master (MAX)",
                badgeColor = Color(0xFFFFD700),
                badgeGradient = listOf(Color(0xFFFFD700), Color(0xFFFF8F00), Color(0xFFFF3D00)),
                perks = listOf(
                    "Ultimate Sovereign Crown & Holographic Profile",
                    "Exclusive 3D Floating Mic Badge in Party Rooms",
                    "All Room & Call Privileges Unlocked",
                    "Permanent Gold Global Chat Highlight"
                )
            )
            level >= 45 -> LevelMeta(
                title = "Mythic Overlord",
                badgeColor = Color(0xFFFF1744),
                badgeGradient = listOf(Color(0xFFFF4081), Color(0xFFFF1744), Color(0xFFD500F9)),
                perks = listOf(
                    "Mythic Fiery Entrance Effect in Voice Parties",
                    "Priority Host Seat Reservation",
                    "Red-Gold Exclusive Nickname Styling"
                )
            )
            level >= 40 -> LevelMeta(
                title = "Grand Monarch",
                badgeColor = Color(0xFFAA00FF),
                badgeGradient = listOf(Color(0xFFE040FB), Color(0xFFAA00FF), Color(0xFF651FFF)),
                perks = listOf(
                    "Grand Monarch Royal Purple Banner",
                    "Party Room Kick & Mute Resistance",
                    "Animated Royal Avatar Frame"
                )
            )
            level >= 35 -> LevelMeta(
                title = "Celestial Star",
                badgeColor = Color(0xFF00E5FF),
                badgeGradient = listOf(Color(0xFF84FFFF), Color(0xFF00E5FF), Color(0xFF2979FF)),
                perks = listOf(
                    "Celestial Particle Trail in Chat",
                    "Custom Profile Theme Badges",
                    "Expanded Friend Capacity (+1,000)"
                )
            )
            level >= 30 -> LevelMeta(
                title = "Diamond Elite",
                badgeColor = Color(0xFF2979FF),
                badgeGradient = listOf(Color(0xFF82B1FF), Color(0xFF2979FF), Color(0xFF1565C0)),
                perks = listOf(
                    "Diamond Elite Badge in Live Rooms",
                    "Special Sound Effect on Room Entry",
                    "Exclusive Diamond Sticker Pack"
                )
            )
            level >= 25 -> LevelMeta(
                title = "Platinum Vanguard",
                badgeColor = Color(0xFF00B0FF),
                badgeGradient = listOf(Color(0xFF80D8FF), Color(0xFF00B0FF), Color(0xFF0288D1)),
                perks = listOf(
                    "Platinum Shield Rank Icon",
                    "Party Room Announcement Privilege",
                    "Special Reaction Animations"
                )
            )
            level >= 20 -> LevelMeta(
                title = "Emerald Knight",
                badgeColor = Color(0xFF00E676),
                badgeGradient = listOf(Color(0xFFB9F6CA), Color(0xFF00E676), Color(0xFF00C853)),
                perks = listOf(
                    "Emerald Chat Glow",
                    "Priority Customer Support Queue",
                    "Special Knight Profile Frame"
                )
            )
            level >= 15 -> LevelMeta(
                title = "Golden Champion",
                badgeColor = Color(0xFFFFB300),
                badgeGradient = listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFFF8F00)),
                perks = listOf(
                    "Golden Level Nameplate",
                    "Higher Gift Combos & Streaks",
                    "VIP Room Search Placement"
                )
            )
            level >= 10 -> LevelMeta(
                title = "Silver Pioneer",
                badgeColor = Color(0xFF90A4AE),
                badgeGradient = listOf(Color(0xFFECEFF1), Color(0xFFB0BEC5), Color(0xFF78909C)),
                perks = listOf(
                    "Silver Badge on Profile",
                    "Extra Daily Check-In Bonus Multiplier",
                    "Unlock Animated Mic Reactions"
                )
            )
            level >= 5 -> LevelMeta(
                title = "Rising Star",
                badgeColor = Color(0xFFFF7043),
                badgeGradient = listOf(Color(0xFFFFAB91), Color(0xFFFF7043), Color(0xFFE64A19)),
                perks = listOf(
                    "Rising Star Profile Glow",
                    "Full Profile Visitor History Access",
                    "Send Enhanced Chat Emotes"
                )
            )
            level >= 4 -> LevelMeta(
                title = "Explorer (Visitors Unlocked)",
                badgeColor = Color(0xFFFF9800),
                badgeGradient = listOf(Color(0xFFFFE082), Color(0xFFFF9800), Color(0xFFF57C00)),
                perks = listOf(
                    "🔓 UNLOCKED: View Who Visited Your Profile",
                    "Profile Visitor Real-Time Radar",
                    "Special Explorer Level Badge"
                )
            )
            level >= 3 -> LevelMeta(
                title = "Pathfinder",
                badgeColor = Color(0xFF26A69A),
                badgeGradient = listOf(Color(0xFF80CBC4), Color(0xFF26A69A), Color(0xFF00796B)),
                perks = listOf(
                    "Customized Chat Bubble Tint",
                    "Party Room Mic Boost",
                    "Reach Level 4 to unlock Visitors!"
                )
            )
            level >= 2 -> LevelMeta(
                title = "Apprentice",
                badgeColor = Color(0xFF42A5F5),
                badgeGradient = listOf(Color(0xFF90CAF9), Color(0xFF42A5F5), Color(0xFF1E88E5)),
                perks = listOf(
                    "Level 2 Apprentice Badge",
                    "Party Voice Soundboard Access",
                    "Reach Level 4 to unlock Visitors!"
                )
            )
            else -> LevelMeta(
                title = "Novice",
                badgeColor = Color(0xFF8D6E63),
                badgeGradient = listOf(Color(0xFFD7CCC8), Color(0xFF8D6E63), Color(0xFF5D4037)),
                perks = listOf(
                    "Standard QIVO Profile",
                    "Join Voice Parties & Chat",
                    "Reach Level 4 to unlock Profile Visitors!"
                )
            )
        }
    }

    /**
     * Returns full list of all 50 Level milestones for the Level Screen Roadmap.
     */
    fun getAllLevelMilestones(): List<LevelMilestone> {
        return (1..MAX_LEVEL).map { lvl ->
            val meta = getLevelMetadata(lvl)
            val cumExp = cumulativeThresholds[lvl]
            val deltaExp = if (lvl < MAX_LEVEL) getExpRequiredForLevel(lvl) else 0L
            LevelMilestone(
                level = lvl,
                title = meta.title,
                cumulativeExpReq = cumExp,
                deltaExpReq = deltaExp,
                perks = meta.perks,
                badgeColor = meta.badgeColor,
                badgeGradient = meta.badgeGradient
            )
        }
    }
}

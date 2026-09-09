package com.example.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Production-grade in-memory cache for Follow, Friends, Followers, and Visitors data.
 * Prevents screen blinking, flickering, and redundant full-screen reloads when switching tabs or navigating screens.
 * Implements Stale-While-Revalidate: cached data is displayed instantly (0ms delay),
 * followed by silent background updates without clearing the active view.
 */
object FollowDataCacheStore {

    // Cache of FollowStats per userId
    private val statsCache = ConcurrentHashMap<String, FollowStats>()

    // Cache of user lists: key is "$userId:$category"
    // e.g. "uuid:VISITORS", "uuid:FRIENDS", "uuid:FOLLOWING", "uuid:FOLLOWERS"
    private val listsCache = ConcurrentHashMap<String, List<UserProfile>>()

    // Cache of who the current user follows: key is currentUserId -> set of followed user IDs
    private val followingSetCache = ConcurrentHashMap<String, MutableSet<String>>()

    // Timestamp of last network fetch per key to avoid rapid repeat spam
    private val lastFetchTime = ConcurrentHashMap<String, Long>()

    // Cache validity duration (e.g. 15 seconds fresh window before silent re-fetch)
    private const val FRESH_WINDOW_MS = 15_000L

    fun getCachedStats(userId: String): FollowStats? {
        if (userId.isBlank()) return null
        return statsCache[userId]
    }

    fun putStats(userId: String, stats: FollowStats) {
        if (userId.isBlank()) return
        statsCache[userId] = stats
    }

    fun getCachedList(userId: String, category: String): List<UserProfile>? {
        if (userId.isBlank()) return null
        return listsCache["$userId:$category"]
    }

    fun putList(userId: String, category: String, list: List<UserProfile>) {
        if (userId.isBlank()) return
        listsCache["$userId:$category"] = list
        lastFetchTime["$userId:$category"] = System.currentTimeMillis()
    }

    fun isCategoryFresh(userId: String, category: String): Boolean {
        val last = lastFetchTime["$userId:$category"] ?: return false
        return (System.currentTimeMillis() - last) < FRESH_WINDOW_MS
    }

    fun getFollowingSet(currentUserId: String): Set<String>? {
        if (currentUserId.isBlank()) return null
        return followingSetCache[currentUserId]
    }

    fun putFollowingSet(currentUserId: String, set: Set<String>) {
        if (currentUserId.isBlank()) return
        followingSetCache[currentUserId] = set.toMutableSet()
    }

    fun updateFollowStatus(currentUserId: String, targetUserId: String, isFollowing: Boolean) {
        if (currentUserId.isBlank() || targetUserId.isBlank()) return
        val currentSet = followingSetCache[currentUserId]
        if (currentSet != null) {
            if (isFollowing) {
                currentSet.add(targetUserId)
            } else {
                currentSet.remove(targetUserId)
            }
        }
    }

    fun clear() {
        statsCache.clear()
        listsCache.clear()
        followingSetCache.clear()
        lastFetchTime.clear()
    }
}

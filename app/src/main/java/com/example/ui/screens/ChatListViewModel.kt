package com.example.ui.screens

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDataCacheManager
import com.example.data.AppAdvertisement
import com.example.data.ChatMessage
import com.example.data.ChatStateHolder
import com.example.data.SupabaseAdService
import com.example.data.SupabaseChatService
import com.example.data.SupabaseProfileService
import com.example.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConversationItem(
    val partnerId: String,
    val partnerName: String,
    val partnerAvatar: String,
    val partnerGender: String,
    val partnerNumericId: Long,
    val partnerCountry: String,
    val latestMessage: String,
    val timestamp: String,
    val isUnread: Boolean,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false
)

class ChatListViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ChatListViewModel"
    }

    private val appContext: Context = application.applicationContext
    private val chatService = SupabaseChatService()
    private val profileService = SupabaseProfileService()
    private val adService = SupabaseAdService()

    private val _conversations = MutableStateFlow<List<ConversationItem>>(emptyList())
    val conversations: StateFlow<List<ConversationItem>> = _conversations.asStateFlow()

    private val _initialLoading = MutableStateFlow(false)
    val initialLoading: StateFlow<Boolean> = _initialLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _displayedLimit = MutableStateFlow(20)
    val displayedLimit: StateFlow<Int> = _displayedLimit.asStateFlow()

    private val _isLoadingMoreChats = MutableStateFlow(false)
    val isLoadingMoreChats: StateFlow<Boolean> = _isLoadingMoreChats.asStateFlow()

    private val _hasMoreServerChats = MutableStateFlow(true)
    val hasMoreServerChats: StateFlow<Boolean> = _hasMoreServerChats.asStateFlow()

    private val _bannerAds = MutableStateFlow<List<AppAdvertisement>>(emptyList())
    val bannerAds: StateFlow<List<AppAdvertisement>> = _bannerAds.asStateFlow()

    private val _profilesMap = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    val profilesMap: StateFlow<Map<String, UserProfile>> = _profilesMap.asStateFlow()

    private val _totalUnreadCount = MutableStateFlow(0)
    val totalUnreadCount: StateFlow<Int> = _totalUnreadCount.asStateFlow()

    var currentUserId: String = ""
        private set

    private var lastSilentRefreshTimeMs = 0L
    private var isInitialized = false

    init {
        // Collect messages & profiles from ChatStateHolder continuously
        viewModelScope.launch {
            combine(ChatStateHolder.messagesList, ChatStateHolder.profilesMap) { msgs, profs ->
                msgs to profs
            }.collect { (msgs, profs) ->
                _profilesMap.value = profs
                if (currentUserId.isNotBlank()) {
                    val built = buildConversations(msgs, profs, currentUserId)
                    updateConversationsIfChanged(built)
                }
            }
        }
    }

    fun initialize(userId: String) {
        if (userId.isBlank()) {
            _conversations.value = emptyList()
            _initialLoading.value = false
            currentUserId = ""
            isInitialized = false
            return
        }

        if (currentUserId == userId && isInitialized) {
            // Already initialized for this user.
            // If conversations already exist, NEVER show initial loading screen!
            onScreenResumed()
            return
        }

        currentUserId = userId
        isInitialized = true

        // Step 1: Load from local cache immediately so UI shows instantly if cache exists
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ChatStateHolder.initialize(appContext, userId)

                val inMem = SupabaseChatService.getInMemoryMessages(userId)
                val cachedMsgs = if (inMem.isNotEmpty()) {
                    inMem
                } else {
                    AppDataCacheManager.getCachedChatMessagesSync(appContext, userId)
                }

                val cachedProfs = AppDataCacheManager.getCachedProfilesSync(appContext, "all")
                val profsMap = cachedProfs.associateBy { it.id.trim() }
                if (profsMap.isNotEmpty()) {
                    _profilesMap.value = profsMap
                }

                if (cachedMsgs.isNotEmpty()) {
                    val initialList = buildConversations(cachedMsgs, profsMap, userId)
                    if (initialList.isNotEmpty()) {
                        _conversations.value = initialList
                        _totalUnreadCount.value = initialList.sumOf { it.unreadCount }
                        _initialLoading.value = false
                    } else {
                        _initialLoading.value = true
                    }
                } else {
                    _initialLoading.value = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading cached chats: ${e.message}")
                if (_conversations.value.isEmpty()) {
                    _initialLoading.value = true
                }
            }

            // Step 2: Establish Supabase Realtime WebSocket subscription (deduplicated internally)
            ChatStateHolder.connectRealtime(userId)

            // Step 3: Fetch server authoritative conversations in background
            fetchServerData(userId, isInitial = _conversations.value.isEmpty())

            // Step 4: Fetch banner ads
            loadBannerAds()
        }
    }

    fun onScreenResumed() {
        if (currentUserId.isBlank()) return
        ChatStateHolder.connectRealtime(currentUserId)

        // Silent background check if > 15s since last refresh
        val now = System.currentTimeMillis()
        if (now - lastSilentRefreshTimeMs > 15_000L && !_isRefreshing.value) {
            refreshSilently()
        }
    }

    fun refreshSilently() {
        if (currentUserId.isBlank() || _isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            fetchServerData(currentUserId, isInitial = false)
        }
    }

    fun pullToRefresh() {
        if (currentUserId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            fetchServerData(currentUserId, isInitial = false)
            loadBannerAds()
        }
    }

    private suspend fun fetchServerData(userId: String, isInitial: Boolean) {
        if (userId.isBlank()) return
        withContext(Dispatchers.IO) {
            if (isInitial && _conversations.value.isEmpty()) {
                _initialLoading.value = true
            } else {
                _isRefreshing.value = true
            }
            try {
                val msgs = chatService.fetchUserMessages(userId, appContext, limit = 300)
                val allProfs = profileService.fetchAllProfiles()
                val profsMap = allProfs.associateBy { it.id.trim() }

                if (allProfs.isNotEmpty()) {
                    _profilesMap.value = profsMap
                    try {
                        AppDataCacheManager.saveProfilesCache(appContext, allProfs, "all")
                    } catch (_: Exception) {}
                }

                if (msgs.isNotEmpty()) {
                    ChatStateHolder.appendOrUpdateMessages(msgs)
                    try {
                        AppDataCacheManager.saveChatMessagesCache(appContext, userId, msgs)
                    } catch (_: Exception) {}
                }

                val currentMsgs = ChatStateHolder.messagesList.value.ifEmpty { msgs }
                val currentProfs = _profilesMap.value.ifEmpty { profsMap }
                val built = buildConversations(currentMsgs, currentProfs, userId)
                updateConversationsIfChanged(built)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching chat data: ${e.message}")
            } finally {
                _initialLoading.value = false
                _isRefreshing.value = false
                lastSilentRefreshTimeMs = System.currentTimeMillis()
            }
        }
    }

    fun loadMoreChats() {
        if (_isLoadingMoreChats.value || !_hasMoreServerChats.value) return
        val currentSize = _conversations.value.size
        if (_displayedLimit.value < currentSize) {
            _displayedLimit.value = minOf(_displayedLimit.value + 20, currentSize)
            return
        }

        if (currentUserId.isBlank()) return
        _isLoadingMoreChats.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentMsgs = ChatStateHolder.messagesList.value
                val nextMsgs = chatService.fetchUserMessages(
                    userId = currentUserId,
                    context = appContext,
                    offset = currentMsgs.size,
                    limit = 100,
                    updateCache = false
                )
                if (nextMsgs.isEmpty()) {
                    _hasMoreServerChats.value = false
                } else {
                    val beforeSize = currentMsgs.size
                    ChatStateHolder.appendMessages(nextMsgs)
                    if (ChatStateHolder.messagesList.value.size != beforeSize) {
                        _displayedLimit.value += 20
                    } else {
                        _hasMoreServerChats.value = false
                    }
                }
            } catch (_: Exception) {
                _hasMoreServerChats.value = false
            } finally {
                _isLoadingMoreChats.value = false
            }
        }
    }

    fun deleteConversation(partnerId: String) {
        val cleanPartnerId = partnerId.trim()
        if (cleanPartnerId.isBlank() || currentUserId.isBlank()) return

        // 1. Optimistic removal from UI state
        val updated = _conversations.value.filterNot { it.partnerId.equals(cleanPartnerId, ignoreCase = true) }
        _conversations.value = updated
        _totalUnreadCount.value = updated.sumOf { it.unreadCount }

        // 2. Perform soft delete & update ChatStateHolder
        viewModelScope.launch(Dispatchers.IO) {
            try {
                chatService.softDeleteConversation(appContext, currentUserId, cleanPartnerId)
                ChatStateHolder.removeConversationLocally(cleanPartnerId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete conversation: ${e.message}")
            }
        }
    }

    private fun loadBannerAds() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ads = adService.fetchActiveAds(appContext, AppAdvertisement.AD_TYPE_CHAT_BANNER, limit = 3)
                _bannerAds.value = ads
            } catch (_: Exception) {}
        }
    }

    private fun updateConversationsIfChanged(newConversations: List<ConversationItem>) {
        val current = _conversations.value
        if (areConversationListsEqual(current, newConversations)) {
            return
        }
        _conversations.value = newConversations
        _totalUnreadCount.value = newConversations.sumOf { it.unreadCount }
        if (newConversations.isNotEmpty()) {
            _initialLoading.value = false
        }
    }

    private fun areConversationListsEqual(a: List<ConversationItem>, b: List<ConversationItem>): Boolean {
        if (a === b) return true
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (a[i] != b[i]) return false
        }
        return true
    }

    private fun buildConversations(
        messages: List<ChatMessage>,
        profiles: Map<String, UserProfile>,
        userId: String
    ): List<ConversationItem> {
        val grouped = mutableMapOf<String, MutableList<ChatMessage>>()
        for (msg in messages) {
            val isSender = msg.senderId.trim().equals(userId, ignoreCase = true)
            val partnerId = if (isSender) msg.receiverId.trim() else msg.senderId.trim()
            if (partnerId.isEmpty()) continue

            if (!grouped.containsKey(partnerId)) {
                grouped[partnerId] = mutableListOf()
            }
            grouped[partnerId]?.add(msg)
        }

        // Also add any users that we have a saved draft with, even if no messages yet
        val draftPartners = AppDataCacheManager.getAllDraftPartners(appContext, userId)
        for (partnerId in draftPartners) {
            val cleanId = partnerId.trim()
            if (cleanId.isNotEmpty() && !grouped.containsKey(cleanId)) {
                grouped[cleanId] = mutableListOf()
            }
        }

        return grouped.mapNotNull { (partnerId, msgs) ->
            val draft = AppDataCacheManager.getChatDraft(appContext, userId, partnerId)
            if (msgs.isEmpty() && draft.isEmpty()) return@mapNotNull null

            val latest = msgs.maxByOrNull { chatService.parseTimestampToMillis(it.createdAt) }

            // Strictly filter out soft-deleted conversations if they only have messages
            if (latest != null && chatService.isConversationSoftDeleted(appContext, userId, partnerId, latest.createdAt)) {
                if (draft.isEmpty()) return@mapNotNull null
            }

            val isSender = latest?.senderId?.trim()?.equals(userId, ignoreCase = true) ?: false

            val partnerProfile = profiles[partnerId]
            val partnerName = partnerProfile?.name ?: if (isSender) (latest?.receiverName ?: "") else (latest?.senderName ?: "")
            val partnerAvatar = partnerProfile?.avatarUrl ?: if (!isSender) (latest?.senderAvatar ?: "") else ""
            val partnerGender = partnerProfile?.gender ?: "Male"
            val partnerNumericId = partnerProfile?.numericId ?: 0L
            val partnerCountry = partnerProfile?.country ?: "Global"

            val unreadCount = msgs.count { !it.isRead && it.receiverId.trim().equals(userId, ignoreCase = true) }
            val isOnline = partnerProfile?.isOnline ?: false

            val displayMessage = if (draft.isNotEmpty()) "Draft: $draft" else (latest?.message ?: "")
            val displayTimestamp = latest?.createdAt ?: ""

            ConversationItem(
                partnerId = partnerId,
                partnerName = partnerName.ifBlank { "QIVO User" },
                partnerAvatar = partnerAvatar,
                partnerGender = partnerGender,
                partnerNumericId = partnerNumericId,
                partnerCountry = partnerCountry,
                latestMessage = displayMessage,
                timestamp = displayTimestamp,
                isUnread = unreadCount > 0,
                unreadCount = unreadCount,
                isOnline = isOnline
            )
        }.sortedByDescending {
            if (it.timestamp.isEmpty()) Long.MAX_VALUE else chatService.parseTimestampToMillis(it.timestamp)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ChatListViewModel onCleared")
    }
}

package com.example.data

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.calling.ZegoCallConfig
import im.zego.zegoexpress.ZegoExpressEngine
import im.zego.zegoexpress.callback.IZegoEventHandler
import im.zego.zegoexpress.constants.ZegoScenario
import im.zego.zegoexpress.constants.ZegoUpdateType
import im.zego.zegoexpress.entity.ZegoRoomConfig
import im.zego.zegoexpress.entity.ZegoStream
import im.zego.zegoexpress.entity.ZegoUser
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.*

/**
 * ZegoCloud Party Voice Room Engine (SECURE VERSION)
 * Uses real ZEGO Express SDK and server-side token generation.
 * No local secrets. No Supabase PCM transport.
 */
class ZegoPartyVoiceEngine(private val context: Context) {

    private var engine: ZegoExpressEngine? = null
    private val config = ZegoCallConfig()
    private var activeRoomId: String = ""
    private var localUserId: String = ""
    private var isJoined = false
    private var isMuted = false

    var onSpeakingVolumeChanged: ((userId: String, volume: Float) -> Unit)? = null
    var onConnectionStateChanged: ((state: String) -> Unit)? = null

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private var refreshJob: Job? = null

    private fun initEngine(appId: Long) {
        if (engine != null) return
        
        val profile = im.zego.zegoexpress.entity.ZegoEngineProfile()
        profile.appID = appId
        profile.scenario = ZegoScenario.HIGH_QUALITY_CHATROOM
        profile.application = context.applicationContext as Application
        
        engine = ZegoExpressEngine.createEngine(profile, object : IZegoEventHandler() {
            override fun onRoomStreamUpdate(
                roomID: String?,
                updateType: ZegoUpdateType?,
                streamList: ArrayList<ZegoStream>?,
                extendedData: JSONObject?
            ) {
                if (updateType == ZegoUpdateType.ADD) {
                    streamList?.forEach { stream ->
                        Log.d("ZegoPartyEngine", "Playing remote stream: ${stream.streamID}")
                        engine?.startPlayingStream(stream.streamID)
                    }
                }
            }

            override fun onCapturedSoundLevelUpdate(level: Float) {
                if (level > 0.1f) {
                    onSpeakingVolumeChanged?.invoke(localUserId, level / 100f)
                }
            }

            override fun onRemoteSoundLevelUpdate(soundLevels: HashMap<String, Float>?) {
                soundLevels?.forEach { (streamId, level) ->
                    val userId = streamId.replace("stream_", "")
                    if (level > 0.1f) {
                        onSpeakingVolumeChanged?.invoke(userId, level / 100f)
                    }
                }
            }

            override fun onRoomStateUpdate(
                roomID: String?,
                state: im.zego.zegoexpress.constants.ZegoRoomState?,
                errorCode: Int,
                extendedData: JSONObject?
            ) {
                Log.d("ZegoPartyEngine", "Room state update: $state, errorCode: $errorCode")
                onConnectionStateChanged?.invoke(state?.name ?: "Unknown")
            }
        })
        
        engine?.startSoundLevelMonitor(250)
    }

    private suspend fun fetchToken(roomId: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                val authHeader = UserSessionManager.getAuthHeader(context)
                
                val payload = JSONObject().apply {
                    put("room_id", roomId)
                }.toString()

                val request = Request.Builder()
                    .url("$baseUrl/functions/v1/party-room-zego-token")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                SupabaseHttpClient.client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return@withContext JSONObject(response.body?.string() ?: "{}")
                    } else {
                        Log.e("ZegoPartyEngine", "Token fetch failed with status: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ZegoPartyEngine", "Token fetch error", e)
            }
            null
        }
    }

    fun enterPartyRoom(
        scope: CoroutineScope,
        roomId: String,
        userId: String,
        seatIndex: Int = -1
    ) {
        activeRoomId = roomId
        localUserId = userId
        
        // Cancel any existing refresh / verification loop
        refreshJob?.cancel()
        
        refreshJob = scope.launch {
            var lastToken = ""
            var lastCanPublish = false
            var isFirstTime = true
            
            while (isActive) {
                val tokenData = fetchToken(roomId)
                if (tokenData != null) {
                    val token = tokenData.optString("token")
                    val appId = tokenData.optLong("zego_app_id", tokenData.optLong("app_id"))
                    val canPublish = tokenData.optBoolean("can_publish", false)
                    val returnedRoomId = tokenData.optString("room_id", roomId)
                    val zegoRoomId = "party_room_$returnedRoomId"
                    
                    if (isFirstTime) {
                        initEngine(appId)
                        val zegoUser = ZegoUser(userId, UserSessionManager.getSession(context)?.name ?: "User")
                        val zegoRoomConfig = ZegoRoomConfig().apply {
                            this.token = token
                            this.isUserStatusNotify = true
                        }
                        
                        Log.d("ZegoPartyEngine", "Logging into ZEGO Room: $zegoRoomId with app_id: $appId")
                        engine?.loginRoom(zegoRoomId, zegoUser, zegoRoomConfig)
                        isJoined = true
                        isFirstTime = false
                        lastToken = token
                        lastCanPublish = canPublish
                        
                        if (canPublish) {
                            startPublishing()
                        } else {
                            stopPublishing()
                        }
                    } else {
                        // Dynamically renew token before its 10-minute expiry
                        if (token.isNotEmpty() && token != lastToken) {
                            Log.d("ZegoPartyEngine", "Renewing ZEGO token before expiry")
                            engine?.renewToken(zegoRoomId, token)
                            lastToken = token
                        }
                        
                        // Respect server's "can_publish" authoritative state (e.g. unseated/lose seat/banned)
                        if (canPublish != lastCanPublish) {
                            Log.d("ZegoPartyEngine", "Publish permission updated from server: can_publish=$canPublish")
                            lastCanPublish = canPublish
                            if (canPublish) {
                                startPublishing()
                            } else {
                                stopPublishing()
                            }
                        }
                    }
                } else {
                    Log.e("ZegoPartyEngine", "Failed to retrieve ZEGO token and authoritative permissions")
                }
                
                // Poll every 15 seconds to enforce real-time authority and handle token expiry/renews
                delay(15000)
            }
        }
    }

    fun takeMicSeat(scope: CoroutineScope, userId: String, seatIndex: Int = -1) {
        // Trigger an immediate server permission check and update
        scope.launch {
            triggerRefresh()
        }
    }

    private suspend fun triggerRefresh() {
        if (activeRoomId.isBlank()) return
        val tokenData = fetchToken(activeRoomId) ?: return
        val token = tokenData.optString("token")
        val canPublish = tokenData.optBoolean("can_publish", false)
        val returnedRoomId = tokenData.optString("room_id", activeRoomId)
        val zegoRoomId = "party_room_$returnedRoomId"
        
        if (token.isNotEmpty()) {
            engine?.renewToken(zegoRoomId, token)
        }
        
        if (canPublish) {
            startPublishing()
        } else {
            stopPublishing()
        }
    }

    private fun startPublishing() {
        if (!isJoined) return
        val streamId = "stream_$localUserId"
        Log.d("ZegoPartyEngine", "Starting mic publish: $streamId")
        engine?.startPublishingStream(streamId)
        engine?.mutePublishStreamAudio(isMuted)
    }

    private fun stopPublishing() {
        Log.d("ZegoPartyEngine", "Stopping mic publish")
        engine?.stopPublishingStream()
    }

    fun leaveMicSeat(userId: String) {
        stopPublishing()
    }

    fun setMicMute(userId: String, mute: Boolean, scope: CoroutineScope? = null) {
        isMuted = mute
        engine?.mutePublishStreamAudio(mute)
    }

    fun setSpeakerphoneOn(speakerOn: Boolean) {
        engine?.setAudioRouteToSpeaker(speakerOn)
    }

    fun exitPartyRoom(userId: String) {
        refreshJob?.cancel()
        refreshJob = null
        engine?.logoutRoom()
        isJoined = false
        ZegoExpressEngine.destroyEngine(null)
        engine = null
    }
}

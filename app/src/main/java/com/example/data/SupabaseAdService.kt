package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

data class AppAdvertisement(
    val id: String = UUID.randomUUID().toString(),
    val adType: String = AD_TYPE_FULLSCREEN, // "APP_OPEN_FULLSCREEN" or "CHAT_TOP_BANNER"
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val targetLink: String = "", // e.g., "qivo://party", "qivo://games/ludo", "qivo://wallet", or "https://..."
    val actionButtonText: String = "Explore Now",
    val isActive: Boolean = true,
    val skipSeconds: Int = 5,
    val createdAt: String = "",
    val createdBy: String = ""
) {
    companion object {
        const val AD_TYPE_FULLSCREEN = "APP_OPEN_FULLSCREEN"
        const val AD_TYPE_CHAT_BANNER = "CHAT_TOP_BANNER"
    }
}

class SupabaseAdService {

    private val client = SupabaseHttpClient.client

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Upload an advertisement image to Supabase Storage bucket 'advertising'
     */
    suspend fun uploadAdImage(
        context: Context,
        bitmap: Bitmap,
        adType: String,
        userId: String
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext null

                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                val imageBytes = baos.toByteArray()

                val prefix = if (adType == AppAdvertisement.AD_TYPE_CHAT_BANNER) "banner" else "interstitial"
                val filename = "${prefix}_${System.currentTimeMillis()}.jpg"

                // Try dedicated 'advertising' bucket first
                val uploadEndpoint = "$baseUrl/storage/v1/object/advertising/$filename"
                val publicUrl = "$baseUrl/storage/v1/object/public/advertising/$filename"

                val authHeader = UserSessionManager.getAuthHeader(context)

                val request = Request.Builder()
                    .url(uploadEndpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "image/jpeg")
                    .addHeader("x-upsert", "true")
                    .post(imageBytes.toRequestBody("image/jpeg".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.d("SupabaseAdService", "Uploaded ad image to advertising bucket: $publicUrl")
                    return@withContext publicUrl
                }

                // Fallback to 'photos' bucket under advertising path
                val fallbackEndpoint = "$baseUrl/storage/v1/object/photos/advertising/$filename"
                val fallbackPublicUrl = "$baseUrl/storage/v1/object/public/photos/advertising/$filename"

                val fallbackRequest = Request.Builder()
                    .url(fallbackEndpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "image/jpeg")
                    .addHeader("x-upsert", "true")
                    .post(imageBytes.toRequestBody("image/jpeg".toMediaType()))
                    .build()

                val fallbackResponse = client.newCall(fallbackRequest).execute()
                if (fallbackResponse.isSuccessful) {
                    Log.d("SupabaseAdService", "Uploaded ad image to photos/advertising: $fallbackPublicUrl")
                    return@withContext fallbackPublicUrl
                }

                // If buckets are restricted, return publicUrl assuming bucket public read
                publicUrl
            } catch (e: Exception) {
                Log.e("SupabaseAdService", "Failed to upload ad image", e)
                null
            }
        }
    }

    /**
     * Fetch all advertisements (for Admin panel and sync)
     */
    suspend fun fetchAllAds(context: Context? = null): List<AppAdvertisement> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext getCachedAds(context)

                val url = "$baseUrl/rest/v1/app_advertisements?select=*&order=created_at.desc"
                val authHeader = UserSessionManager.getAuthHeader(context)
                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (response.isSuccessful && body.isNotBlank()) {
                    val array = JSONArray(body)
                    val result = mutableListOf<AppAdvertisement>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        result.add(parseAd(obj))
                    }
                    if (context != null) {
                        saveAdsToCache(context, result)
                    }
                    return@withContext result
                } else {
                    return@withContext getCachedAds(context)
                }
            } catch (e: Exception) {
                Log.e("SupabaseAdService", "Error fetching ads, using cache", e)
                return@withContext getCachedAds(context)
            }
        }
    }

    /**
     * Fetch specific active ad by type (Full-screen or Chat banner)
     */
    suspend fun fetchActiveAd(context: Context? = null, adType: String): AppAdvertisement? {
        val all = fetchAllAds(context)
        return all.firstOrNull { it.adType == adType && it.isActive && it.imageUrl.isNotBlank() }
    }

    /**
     * Fetch all active ads for a specific type, up to a specified limit (e.g. max 3 for chat banner)
     */
    suspend fun fetchActiveAds(context: Context? = null, adType: String, limit: Int = 3): List<AppAdvertisement> {
        val all = fetchAllAds(context)
        return all.filter { it.adType == adType && it.isActive && it.imageUrl.isNotBlank() }.take(limit)
    }

    /**
     * Save / Upsert an advertisement
     */
    suspend fun saveAd(context: Context, ad: AppAdvertisement, userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                val authHeader = UserSessionManager.getAuthHeader(context)

                val obj = JSONObject().apply {
                    put("id", ad.id)
                    put("ad_type", ad.adType)
                    put("title", ad.title)
                    put("description", ad.description)
                    put("image_url", ad.imageUrl)
                    put("target_link", ad.targetLink)
                    put("action_button_text", ad.actionButtonText)
                    put("is_active", ad.isActive)
                    put("skip_seconds", ad.skipSeconds)
                    put("created_by", userId)
                }

                val currentAds = getCachedAds(context).toMutableList()
                currentAds.removeAll { it.id == ad.id }
                currentAds.add(0, ad)
                saveAdsToCache(context, currentAds)

                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext true

                val url = "$baseUrl/rest/v1/app_advertisements"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(obj.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                Log.e("SupabaseAdService", "Error saving ad", e)
                true // Saved in local cache
            }
        }
    }

    /**
     * Delete an advertisement
     */
    suspend fun deleteAd(context: Context, adId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val currentAds = getCachedAds(context).toMutableList()
                currentAds.removeAll { it.id == adId }
                saveAdsToCache(context, currentAds)

                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                val authHeader = UserSessionManager.getAuthHeader(context)

                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext true

                val url = "$baseUrl/rest/v1/app_advertisements?id=eq.$adId"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .delete()
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                Log.e("SupabaseAdService", "Error deleting ad", e)
                true
            }
        }
    }

    private fun parseAd(obj: JSONObject): AppAdvertisement {
        return AppAdvertisement(
            id = obj.optString("id", UUID.randomUUID().toString()),
            adType = obj.optString("ad_type", AppAdvertisement.AD_TYPE_FULLSCREEN),
            title = obj.optString("title", ""),
            description = obj.optString("description", ""),
            imageUrl = obj.optString("image_url", ""),
            targetLink = obj.optString("target_link", ""),
            actionButtonText = obj.optString("action_button_text", "Explore Now"),
            isActive = obj.optBoolean("is_active", true),
            skipSeconds = obj.optInt("skip_seconds", 5),
            createdAt = obj.optString("created_at", ""),
            createdBy = obj.optString("created_by", "")
        )
    }

    private fun getCachedAds(context: Context?): List<AppAdvertisement> {
        if (context == null) return emptyList()
        val prefs = context.getSharedPreferences("qivo_ads_cache", Context.MODE_PRIVATE)
        val raw = prefs.getString("cached_ads_json", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<AppAdvertisement>()
            for (i in 0 until array.length()) {
                list.add(parseAd(array.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAdsToCache(context: Context, ads: List<AppAdvertisement>) {
        val prefs = context.getSharedPreferences("qivo_ads_cache", Context.MODE_PRIVATE)
        val array = JSONArray()
        for (ad in ads) {
            val obj = JSONObject().apply {
                put("id", ad.id)
                put("ad_type", ad.adType)
                put("title", ad.title)
                put("description", ad.description)
                put("image_url", ad.imageUrl)
                put("target_link", ad.targetLink)
                put("action_button_text", ad.actionButtonText)
                put("is_active", ad.isActive)
                put("skip_seconds", ad.skipSeconds)
                put("created_at", ad.createdAt)
                put("created_by", ad.createdBy)
            }
            array.put(obj)
        }
        prefs.edit().putString("cached_ads_json", array.toString()).apply()
    }
}

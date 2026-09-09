package com.example.data

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object CountryDetector {
    private const val TAG = "CountryDetector"
    private const val PREFS_NAME = "country_detector_cache"
    private const val KEY_CACHED_CODE = "detected_country_code"
    private const val KEY_CACHED_NAME = "detected_country_name"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    /**
     * Detects accurate country using physical telephony, device locale, system timezone, and IP geolocation.
     */
    suspend fun detectCountry(context: Context): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            // 1. Try Telephony SIM / Network ISO (physical cellular connection)
            val telephonyCountry = getTelephonyCountry(context)
            if (telephonyCountry != null) {
                val matched = CountryData.findCountryByCode(telephonyCountry)
                if (matched != null) {
                    saveCache(context, matched.name, matched.code)
                    return@withContext Pair(matched.name, matched.code)
                }
            }

            // 2. Try System TimeZone mapping (highly accurate for regional timezones like Africa/Nairobi, Africa/Lagos, etc.)
            val tzCountryCode = getTimeZoneCountryCode()
            if (tzCountryCode != null) {
                val matched = CountryData.findCountryByCode(tzCountryCode)
                if (matched != null) {
                    saveCache(context, matched.name, matched.code)
                    return@withContext Pair(matched.name, matched.code)
                }
            }

            // 3. Try IP Geolocation (ipwho.is -> ipapi.co -> ipinfo.io)
            val ipDetected = fetchFromIpWhoIs() ?: fetchFromIpApi() ?: fetchFromIpInfo()
            if (ipDetected != null) {
                val matched = CountryData.findCountryByNameOrCode(ipDetected.first, ipDetected.second)
                if (matched != null) {
                    saveCache(context, matched.name, matched.code)
                    return@withContext Pair(matched.name, matched.code)
                }
            }

            // 4. Try Device System Locale
            val localeCountry = getLocaleCountry()
            if (localeCountry != null) {
                val matched = CountryData.findCountryByCode(localeCountry)
                if (matched != null) {
                    saveCache(context, matched.name, matched.code)
                    return@withContext Pair(matched.name, matched.code)
                }
            }

            // 5. Fallback from Cache if present
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cachedCode = prefs.getString(KEY_CACHED_CODE, null)
            val cachedName = prefs.getString(KEY_CACHED_NAME, null)
            if (!cachedCode.isNullOrEmpty() && !cachedName.isNullOrEmpty()) {
                return@withContext Pair(cachedName, cachedCode)
            }

            Pair("Kenya", "KE")
        } catch (e: Exception) {
            Log.w(TAG, "Country detection error: ${e.message}")
            Pair("Kenya", "KE")
        }
    }

    private fun saveCache(context: Context, name: String, code: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_CACHED_NAME, name)
                .putString(KEY_CACHED_CODE, code)
                .apply()
        } catch (_: Exception) {}
    }

    private fun getTelephonyCountry(context: Context): String? {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simCountry = tm?.simCountryIso?.trim()?.uppercase()
            if (!simCountry.isNullOrBlank() && simCountry.length == 2) {
                return simCountry
            }
            val netCountry = tm?.networkCountryIso?.trim()?.uppercase()
            if (!netCountry.isNullOrBlank() && netCountry.length == 2) {
                return netCountry
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun getLocaleCountry(): String? {
        return try {
            val country = Locale.getDefault().country?.trim()?.uppercase()
            if (!country.isNullOrBlank() && country.length == 2) {
                country
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getTimeZoneCountryCode(): String? {
        return try {
            val tzId = TimeZone.getDefault().id.lowercase()
            when {
                tzId.contains("nairobi") -> "KE"
                tzId.contains("dar_es_salaam") -> "TZ"
                tzId.contains("kampala") -> "UG"
                tzId.contains("kigali") -> "RW"
                tzId.contains("bujumbura") -> "BI"
                tzId.contains("juba") -> "SS"
                tzId.contains("lagos") -> "NG"
                tzId.contains("accra") -> "GH"
                tzId.contains("johannesburg") -> "ZA"
                tzId.contains("cairo") -> "EG"
                tzId.contains("addis_ababa") -> "ET"
                tzId.contains("kinshasa") || tzId.contains("lubumbashi") -> "CD"
                tzId.contains("harare") -> "ZW"
                tzId.contains("lusaka") -> "ZM"
                tzId.contains("kolkata") || tzId.contains("calcutta") -> "IN"
                tzId.contains("london") -> "GB"
                tzId.contains("paris") -> "FR"
                tzId.contains("berlin") -> "DE"
                tzId.contains("dubai") -> "AE"
                tzId.contains("riyadh") -> "SA"
                tzId.contains("toronto") || tzId.contains("vancouver") -> "CA"
                tzId.contains("new_york") || tzId.contains("chicago") || tzId.contains("los_angeles") || tzId.contains("denver") -> "US"
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromIpWhoIs(): Pair<String, String>? {
        return try {
            val req = Request.Builder()
                .url("https://ipwho.is/")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: return null
                    val json = JSONObject(body)
                    if (json.optBoolean("success", true)) {
                        val countryName = json.optString("country", "")
                        val countryCode = json.optString("country_code", "")
                        if (countryName.isNotEmpty() && countryCode.isNotEmpty()) {
                            Pair(countryName, countryCode.uppercase())
                        } else null
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "ipwho.is detection failed: ${e.message}")
            null
        }
    }

    private fun fetchFromIpApi(): Pair<String, String>? {
        return try {
            val req = Request.Builder()
                .url("https://ipapi.co/json/")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: return null
                    val json = JSONObject(body)
                    val countryName = json.optString("country_name", "")
                    val countryCode = json.optString("country_code", "")
                    if (countryName.isNotEmpty() && countryCode.isNotEmpty()) {
                        Pair(countryName, countryCode.uppercase())
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "ipapi.co detection failed: ${e.message}")
            null
        }
    }

    private fun fetchFromIpInfo(): Pair<String, String>? {
        return try {
            val req = Request.Builder()
                .url("https://ipinfo.io/json")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: return null
                    val json = JSONObject(body)
                    val countryCode = json.optString("country", "").uppercase()
                    if (countryCode.length == 2) {
                        val name = Locale("", countryCode).displayCountry
                        Pair(name.ifBlank { countryCode }, countryCode)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "ipinfo.io detection failed: ${e.message}")
            null
        }
    }
}


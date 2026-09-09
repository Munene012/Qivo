package com.example.data

data class CoinPackage(
    val id: String,
    val coins: Long,
    val priceKes: Int,
    val priceUsd: Double,
    val isWide: Boolean = false
)

val defaultCoinPackages = listOf(
    CoinPackage("pkg_10", 10, 1, 0.01),
    CoinPackage("pkg_500", 500, 80, 0.65),
    CoinPackage("pkg_1000", 1000, 160, 1.30),
    CoinPackage("pkg_2000", 2000, 320, 2.60),
    CoinPackage("pkg_5000", 5000, 800, 6.50),
    CoinPackage("pkg_10000", 10000, 1600, 13.00),
    CoinPackage("pkg_12500", 12500, 2000, 16.00, isWide = true)
)

data class CountryOption(
    val name: String,
    val flag: String,
    val currencyCode: String,
    val rateMultiplier: Double,
    val code: String = "KE"
)

val countryOptions = listOf(
    CountryOption("Kenya", "🇰🇪", "KES", 1.0, "KE"),
    CountryOption("Tanzania", "🇹🇿", "TZS", 20.0, "TZ"),
    CountryOption("Uganda", "🇺🇬", "UGX", 30.0, "UG"),
    CountryOption("Rwanda", "🇷🇼", "RWF", 10.5, "RW"),
    CountryOption("Burundi", "🇧🇮", "BIF", 23.0, "BI"),
    CountryOption("South Sudan", "🇸🇸", "SSP", 1.0, "SS"),
    CountryOption("United States", "🇺🇸", "USD", 0.008, "US"),
    CountryOption("Nigeria", "🇳🇬", "NGN", 12.5, "NG"),
    CountryOption("Ghana", "🇬🇭", "GHS", 0.12, "GH"),
    CountryOption("South Africa", "🇿🇦", "ZAR", 0.15, "ZA"),
    CountryOption("India", "🇮🇳", "INR", 0.68, "IN"),
    CountryOption("Global", "🌍", "USD", 0.008, "US")
)

object CountryData {
    // Allowed East African countries for Pesapal (e.g. KE, UG, TZ, RW, BI, SS)
    private val eastAfricanCountries = setOf(
        "kenya", "uganda", "tanzania", "rwanda", "burundi", "south sudan", "somalia",
        "democratic republic of the congo", "dr congo", "congo, drc", "drc"
    )

    private val eastAfricanCodes = setOf("KE", "UG", "TZ", "RW", "BI", "SS", "SO", "CD")

    private var cachedIpCountryCode: String? = null

    fun getFlagEmoji(countryCode: String): String {
        val clean = countryCode.trim().uppercase()
        if (clean.length != 2) return "🌍"
        val firstChar = Character.codePointAt(clean, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(clean, 1) - 0x41 + 0x1F1E6
        return try {
            String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
        } catch (_: Exception) {
            "🌍"
        }
    }

    fun isEastAfrica(countryName: String, countryCode: String = ""): Boolean {
        val nameLower = countryName.trim().lowercase()
        val codeUpper = countryCode.trim().uppercase()
        return eastAfricanCountries.contains(nameLower) ||
               eastAfricanCodes.contains(codeUpper) ||
               eastAfricanCountries.any { nameLower.contains(it) }
    }

    fun findCountryByCode(code: String): CountryOption? {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.length != 2) return null
        return allWorldCountryOptions.firstOrNull { it.code.equals(cleanCode, ignoreCase = true) }
            ?: run {
                val localeName = java.util.Locale("", cleanCode).displayCountry
                if (localeName.isNotBlank() && localeName != cleanCode) {
                    CountryOption(name = localeName, flag = getFlagEmoji(cleanCode), currencyCode = "USD", rateMultiplier = 0.008, code = cleanCode)
                } else null
            }
    }

    fun findCountryByName(name: String): CountryOption? {
        val clean = name.trim().lowercase()
        if (clean.isBlank()) return null
        return allWorldCountryOptions.firstOrNull { it.name.trim().lowercase() == clean }
            ?: allWorldCountryOptions.firstOrNull { it.name.trim().lowercase().contains(clean) || clean.contains(it.name.trim().lowercase()) }
    }

    fun findCountryByNameOrCode(name: String, code: String): CountryOption? {
        if (code.isNotBlank()) {
            val byCode = findCountryByCode(code)
            if (byCode != null) return byCode
        }
        if (name.isNotBlank()) {
            val byName = findCountryByName(name)
            if (byName != null) return byName
        }
        return null
    }

    /**
     * Check if given ISO country code is in allowed East Africa list
     */
    fun isEastAfricaCode(countryCode: String?): Boolean {
        if (countryCode.isNullOrBlank()) return false
        return eastAfricanCodes.contains(countryCode.trim().uppercase())
    }

    /**
     * Fetches the user's 2-letter ISO country code based on their real IP address
     */
    suspend fun fetchCountryCodeByIp(): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!cachedIpCountryCode.isNullOrBlank()) {
            return@withContext cachedIpCountryCode
        }
        try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = okhttp3.Request.Builder()
                .url("https://ipwho.is/")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = org.json.JSONObject(body)
                    if (json.optBoolean("success", true)) {
                        val code = json.optString("country_code", "")
                        if (code.isNotBlank()) {
                            cachedIpCountryCode = code.uppercase()
                            return@withContext cachedIpCountryCode
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return@withContext cachedIpCountryCode
    }

    val allWorldCountryOptions: List<CountryOption> by lazy {
        val list = mutableListOf(
            CountryOption("Kenya", "🇰🇪", "KES", 1.0, "KE"),
            CountryOption("Tanzania", "🇹🇿", "TZS", 20.0, "TZ"),
            CountryOption("Uganda", "🇺🇬", "UGX", 30.0, "UG"),
            CountryOption("Rwanda", "🇷🇼", "RWF", 10.5, "RW"),
            CountryOption("Burundi", "🇧🇮", "BIF", 23.0, "BI"),
            CountryOption("South Sudan", "🇸🇸", "SSP", 1.0, "SS"),
            CountryOption("Nigeria", "🇳🇬", "NGN", 12.5, "NG"),
            CountryOption("Ghana", "🇬🇭", "GHS", 0.12, "GH"),
            CountryOption("South Africa", "🇿🇦", "ZAR", 0.15, "ZA"),
            CountryOption("Ethiopia", "🇪🇹", "ETB", 1.0, "ET"),
            CountryOption("Somalia", "🇸🇴", "SOS", 4.5, "SO"),
            CountryOption("United States", "🇺🇸", "USD", 0.008, "US"),
            CountryOption("United Kingdom", "🇬🇧", "GBP", 0.006, "GB"),
            CountryOption("Canada", "🇨🇦", "CAD", 0.011, "CA"),
            CountryOption("Australia", "🇦🇺", "AUD", 0.012, "AU"),
            CountryOption("Germany", "🇩🇪", "EUR", 0.007, "DE"),
            CountryOption("France", "🇫🇷", "EUR", 0.007, "FR"),
            CountryOption("India", "🇮🇳", "INR", 0.68, "IN"),
            CountryOption("United Arab Emirates", "🇦🇪", "AED", 0.03, "AE"),
            CountryOption("Saudi Arabia", "🇸🇦", "SAR", 0.03, "SA")
        )

        // Add all remaining ISO countries
        val existingCodes = list.map { it.code }.toSet()
        val isoCodes = java.util.Locale.getISOCountries()
        for (code in isoCodes) {
            if (!existingCodes.contains(code)) {
                val name = java.util.Locale("", code).displayCountry
                if (name.isNotBlank()) {
                    list.add(
                        CountryOption(
                            name = name,
                            flag = getFlagEmoji(code),
                            currencyCode = "USD",
                            rateMultiplier = 0.008,
                            code = code
                        )
                    )
                }
            }
        }
        list.sortedBy { it.name }
    }

    val worldCountries: List<String> by lazy {
        allWorldCountryOptions.map { it.name }
    }
}

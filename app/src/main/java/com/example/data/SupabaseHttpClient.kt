package com.example.data

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

object SupabaseHttpClient {
    // Shared high-performance connection pool across all services
    val connectionPool = ConnectionPool(32, 5, TimeUnit.MINUTES)

    // Shared dispatcher with high throughput limits for responsive concurrent loading
    val dispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 24
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .dispatcher(dispatcher)
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                var response = chain.proceed(originalRequest)

                // If Supabase returns 401 Unauthorized (expired JWT or invalid token)
                if (response.code == 401) {
                    val currentAuth = originalRequest.header("Authorization")
                    val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                    // Only attempt refresh if this is a Supabase call with a Bearer header
                    if (currentAuth != null && currentAuth.startsWith("Bearer ")) {
                        val failedToken = currentAuth.removePrefix("Bearer ").trim()

                        // Only refresh if the request was using a user token (not anonKey)
                        if (failedToken.isNotBlank() && failedToken != apiKey) {
                            val refreshedToken = SupabaseAuthService.refreshSessionSync(
                                failedToken = failedToken,
                                forceRefresh = true
                            )

                            if (!refreshedToken.isNullOrBlank() && refreshedToken != failedToken) {
                                response.close()
                                val newRequest = originalRequest.newBuilder()
                                    .header("Authorization", "Bearer $refreshedToken")
                                    .build()
                                response = chain.proceed(newRequest)
                            }
                        }
                    }
                }
                response
            }
            .build()
    }
}

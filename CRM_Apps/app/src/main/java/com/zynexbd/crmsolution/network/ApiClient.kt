package com.zynexbd.crmsolution.network

import android.content.Context
import com.zynexbd.crmsolution.BuildConfig
import com.zynexbd.crmsolution.utils.AppLogger
import com.zynexbd.crmsolution.utils.SessionManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds a Retrofit client that automatically attaches the stored JWT
 * as a Bearer token to every request and logs network activity.
 * Guaranteed to never crash the application on invalid or unreachable Base URLs.
 */
typealias RetrofitClient = ApiClient

object ApiClient {

    private const val TAG = "ApiClient"
    const val FALLBACK_BASE_URL = "http://217.216.39.94:83/"

    @Volatile
    private var retrofit: Retrofit? = null
    @Volatile
    private var currentBaseUrl: String? = null

    /**
     * Sanitizes and validates any raw URL string.
     * Guarantees a valid, non-crashing HTTP/HTTPS URL with a trailing slash.
     */
    fun sanitizeBaseUrl(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return FALLBACK_BASE_URL
        var url = rawUrl.trim()
            .removeSurrounding("\"", "\"")
            .removeSurrounding("'", "'")
            .trim()

        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "http://$url"
        }
        if (!url.endsWith("/")) {
            url = "$url/"
        }

        val parsed = url.toHttpUrlOrNull()
        if (parsed == null) {
            AppLogger.e(TAG, "Invalid server URL '$rawUrl'. Falling back to default: $FALLBACK_BASE_URL")
            return FALLBACK_BASE_URL
        }
        return parsed.toString()
    }

    fun getApiService(context: Context): ApiService {
        return getRetrofit(context.applicationContext).create(ApiService::class.java)
    }

    fun resetClient() {
        synchronized(this) {
            retrofit = null
            currentBaseUrl = null
            AppLogger.d(TAG, "ApiClient reset: Retrofit instance cleared.")
        }
    }

    private fun getRetrofit(context: Context): Retrofit {
        val targetBaseUrl = sanitizeBaseUrl(SessionManager(context).getServerBaseUrl())
        if (retrofit == null || currentBaseUrl != targetBaseUrl) {
            synchronized(this) {
                if (retrofit == null || currentBaseUrl != targetBaseUrl) {
                    currentBaseUrl = targetBaseUrl
                    retrofit = buildRetrofit(context, targetBaseUrl)
                }
            }
        }
        return retrofit ?: buildRetrofit(context, FALLBACK_BASE_URL)
    }

    private fun buildRetrofit(context: Context, baseUrl: String): Retrofit {
        val safeUrl = sanitizeBaseUrl(baseUrl)
        AppLogger.i(TAG, "Building Retrofit client with Base URL: $safeUrl")
        val session = SessionManager(context)

        // Interceptor that dynamically redirects requests if server URL was updated in settings
        val hostRedirectInterceptor = Interceptor { chain ->
            val original = chain.request()
            val dynamicBaseUrl = sanitizeBaseUrl(session.getServerBaseUrl()).toHttpUrlOrNull()

            val request = if (dynamicBaseUrl != null &&
                (original.url.host != dynamicBaseUrl.host ||
                 original.url.port != dynamicBaseUrl.port ||
                 original.url.scheme != dynamicBaseUrl.scheme)
            ) {
                val newUrl = original.url.newBuilder()
                    .scheme(dynamicBaseUrl.scheme)
                    .host(dynamicBaseUrl.host)
                    .port(dynamicBaseUrl.port)
                    .build()
                original.newBuilder().url(newUrl).build()
            } else {
                original
            }
            chain.proceed(request)
        }

        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val token = session.getToken()
            val requestBuilder = originalRequest.newBuilder()
            if (!token.isNullOrEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            val request = requestBuilder.build()
            AppLogger.d(TAG, "--> ${request.method} ${request.url}")

            val response: okhttp3.Response
            try {
                response = chain.proceed(request)
            } catch (e: Exception) {
                AppLogger.e(TAG, "<-- HTTP FAILED: ${request.method} ${request.url} - Error: ${e.message}")
                throw e
            }

            AppLogger.d(TAG, "<-- ${response.code} ${request.url} (${response.message})")

            if (response.code == 401 && !request.url.encodedPath.contains("/api/auth/login", ignoreCase = true)) {
                AppLogger.w(TAG, "Received 401 Unauthorized on ${request.url}. Logging user out.")
                session.logout(context)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    val intent = android.content.Intent(context, com.zynexbd.crmsolution.activities.LoginActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    context.startActivity(intent)
                    android.widget.Toast.makeText(context, "সেশন সমাপ্ত হয়েছে। অনুগ্রহ করে আবার লগইন করুন।", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            response
        }

        val logging = HttpLoggingInterceptor { message ->
            AppLogger.v("HTTP", message)
        }.apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(hostRedirectInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return try {
            Retrofit.Builder()
                .baseUrl(safeUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        } catch (e: Throwable) {
            AppLogger.e(TAG, "Failed to build Retrofit with URL: '$safeUrl'. Falling back to default: $FALLBACK_BASE_URL", e)
            Retrofit.Builder()
                .baseUrl(FALLBACK_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
    }
}

package com.zynexbd.crmsolution.network

import android.content.Context
import com.zynexbd.crmsolution.BuildConfig
import com.zynexbd.crmsolution.utils.AppLogger
import com.zynexbd.crmsolution.utils.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds a Retrofit client that automatically attaches the stored JWT
 * as a Bearer token to every request and logs network activity.
 */
typealias RetrofitClient = ApiClient

object ApiClient {

    private const val TAG = "ApiClient"

    @Volatile
    private var retrofit: Retrofit? = null
    @Volatile
    private var currentBaseUrl: String? = null

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
        val targetBaseUrl = SessionManager(context).getServerBaseUrl()
        if (retrofit == null || currentBaseUrl != targetBaseUrl) {
            synchronized(this) {
                if (retrofit == null || currentBaseUrl != targetBaseUrl) {
                    currentBaseUrl = targetBaseUrl
                    retrofit = buildRetrofit(context, targetBaseUrl)
                }
            }
        }
        return retrofit!!
    }

    private fun buildRetrofit(context: Context, baseUrl: String): Retrofit {
        AppLogger.i(TAG, "Building Retrofit client with Base URL: $baseUrl")
        val session = SessionManager(context)

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
                AppLogger.e(TAG, "<-- HTTP FAILED: ${request.method} ${request.url} - Error: ${e.message}", e)
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
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}

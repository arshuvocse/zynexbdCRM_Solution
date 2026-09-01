package com.zynexbd.crmsolution.network

import android.content.Context
import com.zynexbd.crmsolution.BuildConfig
import com.zynexbd.crmsolution.utils.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds a Retrofit client that automatically attaches the stored JWT
 * as a Bearer token to every request.
 */
typealias RetrofitClient = ApiClient

object ApiClient {


    @Volatile
    private var retrofit: Retrofit? = null

    fun getApiService(context: Context): ApiService {
        return getRetrofit(context.applicationContext).create(ApiService::class.java)
    }

    private fun getRetrofit(context: Context): Retrofit {
        return retrofit ?: synchronized(this) {
            retrofit ?: buildRetrofit(context).also { retrofit = it }
        }
    }

    private fun buildRetrofit(context: Context): Retrofit {
        val session = SessionManager(context)

        val authInterceptor = Interceptor { chain ->
            val token = session.getToken()
            val request = chain.request().newBuilder().apply {
                if (!token.isNullOrEmpty()) addHeader("Authorization", "Bearer $token")
            }.build()
            val response = chain.proceed(request)
            if (response.code == 401 && !request.url.encodedPath.contains("/api/auth/login", ignoreCase = true)) {
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

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}

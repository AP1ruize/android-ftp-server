package com.example.ftpembed.ddns

import com.example.ftpembed.auth.AuthConfig
import com.example.ftpembed.debug.HttpClients
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException

interface AccessTokenProvider {
    suspend fun getAccessToken(forceRefresh: Boolean = false): String?
}

class DdnsApiException(val error: DdnsApiError) : Exception(error.message)

class DdnsApiClient(
    private val tokenProvider: AccessTokenProvider,
    baseUrl: String = AuthConfig.apiBase,
) {
    private val api: DdnsApi

    init {
        val authInterceptor = Interceptor { chain ->
            val token = runBlocking { tokenProvider.getAccessToken() }
            val request = if (!token.isNullOrBlank()) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        val authenticator = Authenticator { _, response ->
            if (responseCount(response) >= 2) return@Authenticator null
            val newToken = runBlocking { tokenProvider.getAccessToken(forceRefresh = true) }
                ?: return@Authenticator null
            response.request.newBuilder()
                .removeHeader("Authorization")
                .header("Authorization", "Bearer $newToken")
                .build()
        }

        val client = HttpClients.create("DDNS").newBuilder()
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        api = Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DdnsApi::class.java)
    }

    suspend fun fetchMyShard(): Result<MyShardResponse> =
        safeCall { api.getMyShard() }

    suspend fun fetchRecords(): Result<RecordsListResponse> =
        safeCall { api.listRecords() }

    suspend fun createRecord(label: String, ipv4: String): Result<RecordResponse> =
        safeCall { api.createRecord(CreateRecordRequest(label = label, ipv4 = ipv4)) }

    suspend fun updateRecord(label: String, ipv4: String): Result<RecordResponse> =
        safeCall { api.updateRecord(label, UpdateRecordRequest(ipv4 = ipv4)) }

    suspend fun deleteRecord(label: String): Result<Unit> =
        safeCall {
            val response = api.deleteRecord(label)
            if (response.isSuccessful) {
                Unit
            } else {
                throw HttpException(response)
            }
        }

    fun parseErrorBody(statusCode: Int, body: String?): DdnsApiError =
        ApiErrorParser.parse(statusCode, body)

    private suspend inline fun <T> safeCall(crossinline block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: HttpException) {
            Result.failure(
                DdnsApiException(parseErrorBody(e.code(), e.response()?.errorBody()?.string())),
            )
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private fun String.ensureTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

package com.example.ftpembed.ddns

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface DdnsApi {
    @GET("v1/my-shard")
    suspend fun getMyShard(): MyShardResponse

    @GET("v1/records")
    suspend fun listRecords(): RecordsListResponse

    @POST("v1/records")
    suspend fun createRecord(@Body body: CreateRecordRequest): RecordResponse

    @PATCH("v1/records/{label}")
    suspend fun updateRecord(
        @Path("label") label: String,
        @Body body: UpdateRecordRequest,
    ): RecordResponse

    @DELETE("v1/records/{label}")
    suspend fun deleteRecord(@Path("label") label: String): Response<Void>
}

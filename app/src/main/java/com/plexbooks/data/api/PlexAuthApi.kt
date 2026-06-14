package com.plexbooks.data.api

import com.plexbooks.data.api.model.PlexPin
import com.plexbooks.data.api.model.PlexResource
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PlexAuthApi {

    @POST("pins")
    suspend fun createPin(
        @Query("strong") strong: Boolean = true
    ): PlexPin

    @GET("pins/{id}")
    suspend fun checkPin(
        @Path("id") pinId: Long,
        @Query("code") code: String
    ): PlexPin

    @GET("resources")
    suspend fun getResources(
        @Query("includeHttps") includeHttps: Int = 1,
        @Query("includeRelay") includeRelay: Int = 1,
        @Query("includeIPv6") includeIPv6: Int = 1
    ): List<PlexResource>
}

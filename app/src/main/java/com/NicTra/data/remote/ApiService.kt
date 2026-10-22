package com.nictra.data.remote

import com.nictra.data.remote.dto.FeedDto
import com.nictra.data.remote.dto.FeedVersionDto
import retrofit2.http.GET
import retrofit2.http.Headers

interface ApiService {
    @Headers("Cache-Control: no-store")
    @GET("api/feed/version")
    suspend fun getFeedVersion(): FeedVersionDto

    @GET("api/feed")
    suspend fun getFeed(): FeedDto
}

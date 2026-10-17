package com.omidgame.mench.core.network

import com.omidgame.mench.BuildConfig
import retrofit2.http.GET
import retrofit2.http.Query

/** One GIF search result. Only the id travels in a message; the bytes always come through our own server. */
data class GifItemWire(
    val id: String,
    val title: String? = null,
    val width: Int,
    val height: Int,
)

data class GifPageWire(
    val items: List<GifItemWire>,
    /** Offset to request next, null when there are no more results. */
    val nextOffset: Int? = null,
)

/** Backend GIF proxy (see backend/src/modules/gifs). 503 = the server has no GIF provider key configured. */
interface GifApi {
    @GET("gifs/search")
    suspend fun search(@Query("q") query: String, @Query("offset") offset: Int): GifPageWire

    @GET("gifs/trending")
    suspend fun trending(@Query("offset") offset: Int): GifPageWire
}

/** Authenticated media URLs, loaded by the app-wide Coil loader (which attaches the bearer token). */
object GifUrls {
    fun preview(id: String): String = "${BuildConfig.API_BASE_URL}gifs/$id/media?size=preview"
    fun full(id: String): String = "${BuildConfig.API_BASE_URL}gifs/$id/media?size=full"
}

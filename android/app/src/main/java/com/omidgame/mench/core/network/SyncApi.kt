package com.omidgame.mench.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class SyncEntry(val key: String, val value: String, val updatedAt: String)
data class SyncPutBody(val value: String)

/** Phase 1D.1: small per-user state shared by all of the user's devices (backend: modules/sync). */
interface SyncApi {
    @GET("sync/state") suspend fun changedSince(@Query("since") since: String?): List<SyncEntry>
    @PUT("sync/state/{key}") suspend fun put(@Path("key") key: String, @Body body: SyncPutBody): SyncEntry
}

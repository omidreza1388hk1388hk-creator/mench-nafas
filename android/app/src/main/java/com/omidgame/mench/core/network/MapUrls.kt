package com.omidgame.mench.core.network

import com.omidgame.mench.BuildConfig

/** Map tiles are fetched from our own server (backend /maps/tile), never directly from the tile provider. */
object MapUrls {
    fun tile(zoom: Int, x: Int, y: Int): String = "${BuildConfig.API_BASE_URL}maps/tile/$zoom/$x/$y"
}

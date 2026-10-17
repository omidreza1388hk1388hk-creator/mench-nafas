package com.omidgame.mench.feature.chat.presentation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** One map tile to draw, with its top-left corner relative to the preview's top-left (dp). */
data class TilePlacement(val zoom: Int, val x: Int, val y: Int, val offsetXDp: Float, val offsetYDp: Float)

/**
 * Slippy-map tile math for the location bubble's static preview (Web Mercator, 256 px tiles —
 * the scheme OpenStreetMap-style servers use). Pure Kotlin, unit-tested; no map SDK needed.
 */
object MapTiles {
    const val DEFAULT_ZOOM = 15
    private const val MAX_LATITUDE = 85.0511

    /** Fractional tile coordinates of a point: floor() is the tile index, the remainder the position inside it. */
    fun tileCoordinates(latitude: Double, longitude: Double, zoom: Int): Pair<Double, Double> {
        val n = (1 shl zoom).toDouble()
        val lat = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val latRad = Math.toRadians(lat)
        val x = (longitude + 180.0) / 360.0 * n
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        return x to y
    }

    /**
     * Every tile that intersects a [widthDp] x [heightDp] viewport centred on the point, where one
     * tile is drawn [tileDp] wide. The x index wraps around the antimeridian; rows outside the
     * world (above/below the poles) are dropped.
     */
    fun visibleTiles(
        latitude: Double,
        longitude: Double,
        zoom: Int,
        widthDp: Float,
        heightDp: Float,
        tileDp: Float = 256f,
    ): List<TilePlacement> {
        val n = 1 shl zoom
        val (cx, cy) = tileCoordinates(latitude, longitude, zoom)
        val halfW = widthDp / 2f / tileDp
        val halfH = heightDp / 2f / tileDp
        val minX = floor(cx - halfW).toInt()
        val maxX = floor(cx + halfW).toInt()
        val minY = floor(cy - halfH).toInt()
        val maxY = floor(cy + halfH).toInt()
        val out = ArrayList<TilePlacement>()
        for (ty in minY..maxY) {
            if (ty < 0 || ty >= n) continue
            for (tx in minX..maxX) {
                val wrappedX = ((tx % n) + n) % n
                out.add(
                    TilePlacement(
                        zoom = zoom,
                        x = wrappedX,
                        y = ty,
                        offsetXDp = ((tx - cx) * tileDp + widthDp / 2.0).toFloat(),
                        offsetYDp = ((ty - cy) * tileDp + heightDp / 2.0).toFloat(),
                    ),
                )
            }
        }
        return out
    }

    /** "35.6892, 51.3890" for display — fixed 5 decimals, always a dot. */
    fun formatCoordinates(latitude: Double, longitude: Double): String =
        "%.5f, %.5f".format(java.util.Locale.US, latitude, longitude)

    /** Plain https fallback link for devices with no app that handles geo: URIs. */
    fun webLink(latitude: Double, longitude: Double): String {
        val lat = "%.6f".format(java.util.Locale.US, latitude)
        val lon = "%.6f".format(java.util.Locale.US, longitude)
        return "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=16/$lat/$lon"
    }

    fun geoUri(latitude: Double, longitude: Double): String {
        val lat = "%.6f".format(java.util.Locale.US, latitude)
        val lon = "%.6f".format(java.util.Locale.US, longitude)
        return "geo:$lat,$lon?q=$lat,$lon"
    }
}

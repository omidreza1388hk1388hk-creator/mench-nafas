package com.omidgame.mench.feature.chat.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import coil.compose.AsyncImage
import com.omidgame.mench.R
import com.omidgame.mench.core.network.MapUrls
import com.omidgame.mench.core.ui.components.MenchDialog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class LatLon(val latitude: Double, val longitude: Double)

/** One-shot device location through the platform LocationManager (no Google Play Services needed). */
object LocationFetcher {
    private const val TIMEOUT_MS = 12_000L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // callers check hasPermission() first
    suspend fun current(context: Context): LatLon? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        // A fresh fix from the best enabled provider, bounded by a timeout.
        val fresh = providers.firstNotNullOfOrNull { provider ->
            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    try {
                        LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                            if (continuation.isActive) continuation.resume(location)
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        }
        val best = fresh ?: providers.plus(LocationManager.PASSIVE_PROVIDER).firstNotNullOfOrNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        return best?.let { LatLon(it.latitude, it.longitude) }
    }
}

/** Opens the coordinates in whatever maps app is installed, with a browser fallback. */
fun openInMaps(context: Context, latitude: Double, longitude: Double) {
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse(MapTiles.geoUri(latitude, longitude))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse(MapTiles.webLink(latitude, longitude))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(geo)
    } catch (e: Exception) {
        runCatching { context.startActivity(web) }
    }
}

/** Static map: tiles from our server around the point, a pin on top, the required OSM credit. */
@Composable
fun MapPreview(latitude: Double, longitude: Double, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val tiles = remember(latitude, longitude, width, height) {
        MapTiles.visibleTiles(latitude, longitude, MapTiles.DEFAULT_ZOOM, width.value, height.value)
    }
    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        tiles.forEach { tile ->
            AsyncImage(
                model = MapUrls.tile(tile.zoom, tile.x, tile.y),
                contentDescription = null,
                modifier = Modifier.offset(tile.offsetXDp.dp, tile.offsetYDp.dp).requiredSize(256.dp),
            )
        }
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.align(Alignment.Center).offset(y = (-18).dp).size(36.dp),
        )
        Text(
            text = "\u00A9 OpenStreetMap",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(topStart = 6.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/** A location inside a message bubble. Tap opens the maps app. A live location also shows when it ends. */
@Composable
fun LocationBubbleContent(latitude: Double, longitude: Double, liveUntilEpochMillis: Long? = null) {
    val context = LocalContext.current
    // Flips to "ended" by itself when the expiry passes while the chat is open.
    var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(liveUntilEpochMillis) {
        val until = liveUntilEpochMillis ?: return@LaunchedEffect
        while (now < until) {
            kotlinx.coroutines.delay(minOf(30_000L, until - now) + 50)
            now = System.currentTimeMillis()
        }
    }
    val liveActive = liveUntilEpochMillis != null && now < liveUntilEpochMillis
    Column(
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.location_open)) { openInMaps(context, latitude, longitude) },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MapPreview(latitude, longitude, width = 240.dp, height = 150.dp)
        Text(
            text = when {
                liveUntilEpochMillis == null -> stringResource(R.string.preview_location)
                liveActive -> stringResource(
                    R.string.live_location_until,
                    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(liveUntilEpochMillis)),
                )
                else -> stringResource(R.string.live_location_ended)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (liveActive) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
        Text(
            text = MapTiles.formatCoordinates(latitude, longitude),
            style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current.copy(alpha = 0.75f),
        )
    }
}

/** Shown before anything is sent: your location is private, so it is never shared without a look and a tap. */
@Composable
fun LocationConfirmDialog(location: LatLon, onSend: () -> Unit, onDismiss: () -> Unit, onShareLive: (durationMs: Long) -> Unit = {}) {
    MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.location_send_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MapPreview(location.latitude, location.longitude, width = 240.dp, height = 150.dp)
                Text(MapTiles.formatCoordinates(location.latitude, location.longitude), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.location_send_hint), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.live_location_share), style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        R.string.live_location_15m to 15 * 60_000L,
                        R.string.live_location_1h to 60 * 60_000L,
                        R.string.live_location_8h to 8 * 60 * 60_000L,
                    ).forEach { (label, ms) ->
                        androidx.compose.material3.AssistChip(onClick = { onShareLive(ms) }, label = { Text(stringResource(label), maxLines = 1) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSend) { Text(stringResource(R.string.chat_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

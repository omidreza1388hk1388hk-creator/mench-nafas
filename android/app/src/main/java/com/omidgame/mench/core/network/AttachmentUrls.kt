package com.omidgame.mench.core.network

import com.omidgame.mench.BuildConfig

/**
 * Builds the authenticated content/thumbnail URLs Coil loads images from.
 * These are plain GET endpoints requiring the same bearer token as every
 * other API call — Coil's ImageLoader is configured with the
 * @AuthenticatedClient OkHttpClient (see core/di/ImageLoaderModule.kt) so
 * the Authorization header is attached automatically, the same way it is
 * for ChatApi's Retrofit calls.
 */
object AttachmentUrls {
    fun content(attachmentId: String): String = "${BuildConfig.API_BASE_URL}attachments/$attachmentId/content"
    fun thumbnail(attachmentId: String): String = "${BuildConfig.API_BASE_URL}attachments/$attachmentId/thumbnail"
}

package com.omidgame.mench.feature.product

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR generation (ZXing core, pure Java; no Google Play Services needed). */
object QrCodes {
    fun generate(text: String, sizePx: Int = 640): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val offset = y * sizePx
            for (x in 0 until sizePx) pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }
}

/** Everything the app puts in invite links, and the matching parser for deep links and scanned codes. */
object InviteLinks {
    private val CODE = Regex("^[A-Za-z0-9]{6,16}$")

    /** Accepts `mench://invite/CODE`, `https://mench.app/i/CODE` or a bare code. Returns the normalised code or null. */
    fun extractCode(raw: String): String? {
        val text = raw.trim()
        val fromUri = runCatching { android.net.Uri.parse(text) }.getOrNull()
        val candidate = when {
            fromUri?.scheme == "mench" && fromUri.host == "invite" -> fromUri.lastPathSegment
            fromUri?.host?.endsWith("mench.app") == true && fromUri.pathSegments.firstOrNull() == "i" -> fromUri.pathSegments.getOrNull(1)
            else -> text
        }
        return candidate?.takeIf { CODE.matches(it) }?.uppercase()
    }
}

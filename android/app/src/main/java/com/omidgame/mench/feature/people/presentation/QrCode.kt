package com.omidgame.mench.feature.people.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Plain black-on-white (scanners need the contrast) QR on a white rounded card, so it also reads in dark theme. */
@Composable
fun InviteQr(content: String, size: Dp, contentDescription: String, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap = remember(content, px) { buildQrBitmap(content, px) }
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(10.dp)
            .size(size),
    )
}

private fun buildQrBitmap(content: String, requestedPx: Int): ImageBitmap {
    val hints = HashMap<EncodeHintType, Any>()
    hints[EncodeHintType.MARGIN] = 0
    hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, requestedPx, requestedPx, hints)
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
            pixels[offset + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap.asImageBitmap()
}

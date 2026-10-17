package com.omidgame.mench.feature.product

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File

/**
 * Renders a shareable invitation card (brand gradient, QR code, invite code) as a PNG and opens the
 * share sheet. Drawn with plain Canvas, no network, no extra dependency. The QR encodes the https
 * invite link, so it also works for people scanning with a normal camera app.
 */
object InviteCard {
    fun render(code: String, link: String, headline: String, subtitle: String, width: Int = 1080, height: Int = 1350): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), 0xFFA81D56.toInt(), 0xFF5B2A86.toInt(), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)

        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        white.textSize = 84f
        c.drawText(headline, width / 2f, 230f, white)
        white.typeface = Typeface.DEFAULT; white.textSize = 44f; white.alpha = 220
        c.drawText(subtitle, width / 2f, 310f, white)

        val qrSize = 640
        val qr = QrCodes.generate(link, qrSize)
        val panel = RectF((width - qrSize) / 2f - 40f, 400f, (width + qrSize) / 2f + 40f, 400f + qrSize + 80f)
        c.drawRoundRect(panel, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        c.drawBitmap(qr, (width - qrSize) / 2f, 440f, null)

        white.alpha = 255; white.typeface = Typeface.MONOSPACE; white.textSize = 96f
        c.drawText(code, width / 2f, 1200f, white)
        return bmp
    }

    fun share(context: Context, code: String, link: String, headline: String, subtitle: String, chooserTitle: String?) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "mench-invite.png")
        file.outputStream().use { render(code, link, headline, subtitle).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, link)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }
}

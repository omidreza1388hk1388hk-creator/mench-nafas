package com.omidgame.mench.core.media

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

/** Phase 1B: decodes a BlurHash string (see backend AttachmentsService.computeBlurHash) into a tiny bitmap. */
object BlurHashDecoder {
    private const val CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"
    private val INDEX = IntArray(128) { -1 }.also { table -> CHARS.forEachIndexed { i, c -> table[c.code] = i } }

    /** Returns null for a malformed hash. [width]/[height] can be tiny (e.g. 32x32): it is only a blurred backdrop. */
    fun decode(hash: String?, width: Int, height: Int, punch: Float = 1f): Bitmap? {
        if (hash == null || hash.length < 6 || width <= 0 || height <= 0) return null
        val sizeFlag = decode83(hash, 0, 1) ?: return null
        val numY = sizeFlag / 9 + 1
        val numX = sizeFlag % 9 + 1
        if (hash.length != 4 + 2 * numX * numY) return null
        val quantMax = decode83(hash, 1, 2) ?: return null
        val maxValue = (quantMax + 1) / 166f

        val colors = Array(numX * numY) { FloatArray(3) }
        for (i in colors.indices) {
            val c = colors[i]
            if (i == 0) {
                val v = decode83(hash, 2, 6) ?: return null
                c[0] = srgbToLinear(v shr 16)
                c[1] = srgbToLinear((v shr 8) and 255)
                c[2] = srgbToLinear(v and 255)
            } else {
                val v = decode83(hash, 4 + i * 2, 6 + i * 2) ?: return null
                c[0] = signPow((v / (19 * 19) - 9) / 9f, 2f) * maxValue * punch
                c[1] = signPow(((v / 19) % 19 - 9) / 9f, 2f) * maxValue * punch
                c[2] = signPow((v % 19 - 9) / 9f, 2f) * maxValue * punch
            }
        }

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (j in 0 until numY) {
                    for (i in 0 until numX) {
                        val basis = (cos(PI * x * i / width) * cos(PI * y * j / height)).toFloat()
                        val c = colors[j * numX + i]
                        r += c[0] * basis
                        g += c[1] * basis
                        b += c[2] * basis
                    }
                }
                pixels[y * width + x] = (0xFF shl 24) or (linearToSrgb(r) shl 16) or (linearToSrgb(g) shl 8) or linearToSrgb(b)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun decode83(s: String, from: Int, to: Int): Int? {
        var result = 0
        for (i in from until to) {
            val code = s[i].code
            val digit = if (code < 128) INDEX[code] else -1
            if (digit < 0) return null
            result = result * 83 + digit
        }
        return result
    }

    private fun srgbToLinear(value: Int): Float {
        val v = value / 255f
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        return if (v <= 0.0031308f) (v * 12.92f * 255f + 0.5f).roundToInt() else ((1.055f * v.pow(1 / 2.4f) - 0.055f) * 255f + 0.5f).roundToInt()
    }

    private fun signPow(value: Float, exp: Float): Float = sign(value) * abs(value).pow(exp)
}

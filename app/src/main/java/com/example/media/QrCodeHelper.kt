package com.example.media

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeHelper {

    fun buildPairingUri(code: String): String = "togethergps://pair?code=${code.trim().uppercase()}"

    fun extractCodeFromQr(rawValue: String?): String? {
        if (rawValue.isNullOrBlank()) return null
        val trimmed = rawValue.trim()
        val match = Regex("code=([A-Za-z0-9]{6,12})").find(trimmed)
        if (match != null) {
            return match.groupValues[1].uppercase()
        }
        val clean = trimmed.uppercase()
        return if (clean.matches(Regex("^[A-Z0-9]{6,12}$"))) clean else null
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) Color.parseColor("#132A3E") else Color.WHITE)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}

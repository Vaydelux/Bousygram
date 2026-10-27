package com.vaydelux.bousygram.core.tdlib.utils

import android.graphics.Bitmap
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.util.EnumMap

object QrCodeGenerator {
    private const val TAG = "QrCodeGenerator"

    /**
     * Generates a high-contrast black-and-white Bitmap from a text string.
     * Uses ARGB_8888 for Compose ImageBitmap compatibility and margin 1 to maximize QR module size.
     */
    fun generate(text: String, width: Int, height: Int): Bitmap? {
        if (text.isBlank() || width <= 0 || height <= 0) return null
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.MARGIN, 1)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                width,
                height,
                hints
            )
            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)
            val black = 0xFF000000.toInt()
            val white = 0xFFFFFFFF.toInt()
            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) black else white
                }
            }
            val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
            bitmap
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to generate QR code for '$text'", e)
            null
        }
    }
}

package com.example.crypto

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject

/**
 * QR Code encoding and decoding utility for BitCall cryptographic identity exchange.
 */
object QrCodeUtil {

    data class ContactQrPayload(
        val bitcallId: String,
        val displayName: String,
        val publicKeyBase64: String,
        val fingerprint: String,
        val version: Int = 1
    ) {
        fun toJson(): String {
            val json = JSONObject()
            json.put("v", version)
            json.put("id", bitcallId)
            json.put("name", displayName)
            json.put("pk", publicKeyBase64)
            json.put("fp", fingerprint)
            return json.toString()
        }

        companion object {
            fun fromJson(jsonString: String): ContactQrPayload? {
                return try {
                    val json = JSONObject(jsonString)
                    ContactQrPayload(
                        bitcallId = json.getString("id"),
                        displayName = json.optString("name", "Unknown Peer"),
                        publicKeyBase64 = json.getString("pk"),
                        fingerprint = json.getString("fp"),
                        version = json.optInt("v", 1)
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Generates a square QR Code Bitmap from raw text.
     */
    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)

        val darkColor = Color.argb(255, 13, 27, 42) // Deep navy
        val lightColor = Color.argb(255, 240, 244, 248) // Clean light surface

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) darkColor else lightColor
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}

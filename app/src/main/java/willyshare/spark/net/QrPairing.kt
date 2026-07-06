package willyshare.spark.net

import android.graphics.Bitmap
import android.graphics.Color as AColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Encodes/decodes the small pairing payload embedded in the QR code shown on the
 * Receive screen. Format: SPARKS|<deviceName>|<ip>|<port>
 */
object QrPairing {
    private const val PREFIX = "SPARKS"

    data class Payload(val deviceName: String, val ip: String, val port: Int)

    fun buildPayload(deviceName: String, ip: String, port: Int = TRANSFER_PORT): String {
        val safeName = deviceName.replace("|", " ")
        return "$PREFIX|$safeName|$ip|$port"
    }

    fun parsePayload(raw: String): Payload? {
        val parts = raw.trim().split("|")
        if (parts.size != 4 || parts[0] != PREFIX) return null
        val port = parts[3].toIntOrNull() ?: return null
        if (parts[2].isBlank()) return null
        return Payload(deviceName = parts[1], ip = parts[2], port = port)
    }

    fun generateQrBitmap(content: String, sizePx: Int = 720): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val writer = QRCodeWriter()
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) AColor.BLACK else AColor.WHITE)
            }
        }
        return bitmap
    }
}

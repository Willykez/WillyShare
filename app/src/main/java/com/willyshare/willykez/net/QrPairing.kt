package com.willyshare.willykez.net

import android.graphics.Bitmap
import android.graphics.Color as AColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Encodes/decodes the small pairing payload embedded in the QR code shown on the
 * Receive screen.
 *
 * Two formats, both pipe-separated and both starting with "SPARKS":
 *  - LAN (original):    SPARKS|<deviceName>|<ip>|<port>
 *  - High-speed (P2P):  SPARKS|<deviceName>|<ip>|<port>|P2P|<networkName>|<passphrase>
 *
 * The high-speed form still carries an <ip>/<port> pair as a same-network fallback
 * (some devices/OEMs reject Wi-Fi Direct Fast Connect), so older builds of this app -
 * or a scan that fails to join the P2P group - can still fall back to it.
 */
object QrPairing {
    private const val PREFIX = "SPARKS"
    private const val MODE_P2P = "P2P"

    data class Payload(
        val deviceName: String,
        val ip: String,
        val port: Int,
        val fastConnectNetworkName: String? = null,
        val fastConnectPassphrase: String? = null
    ) {
        val isFastConnect: Boolean get() = fastConnectNetworkName != null && fastConnectPassphrase != null
    }

    fun buildPayload(deviceName: String, ip: String, port: Int = TRANSFER_PORT): String {
        val safeName = deviceName.replace("|", " ")
        return "$PREFIX|$safeName|$ip|$port"
    }

    /** Same as [buildPayload] but also carries high-speed Wi-Fi Direct group credentials. */
    fun buildFastConnectPayload(
        deviceName: String,
        ip: String,
        port: Int = TRANSFER_PORT,
        networkName: String,
        passphrase: String
    ): String {
        val safeName = deviceName.replace("|", " ")
        return "$PREFIX|$safeName|$ip|$port|$MODE_P2P|$networkName|$passphrase"
    }

    fun parsePayload(raw: String): Payload? {
        val parts = raw.trim().split("|")
        if (parts.size != 4 && parts.size != 7) return null
        if (parts[0] != PREFIX) return null
        val port = parts[3].toIntOrNull() ?: return null
        if (parts[2].isBlank()) return null
        return if (parts.size == 7 && parts[4] == MODE_P2P) {
            if (parts[5].isBlank() || parts[6].isBlank()) return null
            Payload(
                deviceName = parts[1], ip = parts[2], port = port,
                fastConnectNetworkName = parts[5], fastConnectPassphrase = parts[6]
            )
        } else {
            Payload(deviceName = parts[1], ip = parts[2], port = port)
        }
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

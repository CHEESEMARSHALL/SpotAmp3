package com.example.ui

import com.example.data.parseCompanionPairingPayload
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.PlanarYUVLuminanceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompanionPairingQrDecoderTest {
    private val pairingUri =
        "spotamp://pair?v=1&baseUrl=https%3A%2F%2F203.0.113.10&pairingId=01234567-89ab-cdef-0123-456789abcdef&secret=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq_"

    @Test
    fun decodesAndParsesSpotAmpPairingQr() {
        val decoded = decodePairingQr(qrSource(pairingUri))

        assertEquals(pairingUri, decoded)
        val payload = parseCompanionPairingPayload(requireNotNull(decoded))
        assertEquals("https://203.0.113.10", payload.baseUrl)
        assertEquals("01234567-89ab-cdef-0123-456789abcdef", payload.pairingId)
    }

    @Test
    fun decodesNonSpotAmpQrSoScannerCanPresentInvalidCodeGuidance() {
        val decoded = decodePairingQr(qrSource("https://example.com/not-a-pairing-code"))

        assertEquals("https://example.com/not-a-pairing-code", decoded)
        val error = runCatching { parseCompanionPairingPayload(requireNotNull(decoded)) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("not a SpotAmp pairing code"))
    }

    @Test
    fun noQrInFrameReturnsNoDecodedValue() {
        val size = 256
        val blank = ByteArray(size * size) { 0xFF.toByte() }
        val source = PlanarYUVLuminanceSource(blank, size, size, 0, 0, size, size, false)

        assertNull(decodePairingQr(source))
    }

    private fun qrSource(value: String): PlanarYUVLuminanceSource {
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512)
        val size = matrix.width
        val yPlane = ByteArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                yPlane[y * size + x] = if (matrix[x, y]) 0 else 0xFF.toByte()
            }
        }
        return PlanarYUVLuminanceSource(yPlane, size, size, 0, 0, size, size, false)
    }
}

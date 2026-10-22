package app.altitude.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor

fun interface GeoidModel {
    /** Geoid undulation N in meters. Orthometric height H = ellipsoidal h − N. */
    fun undulationMeters(latitudeDegrees: Double, longitudeDegrees: Double): Double
}

fun resolveMsl(sample: GnssSample, geoid: GeoidModel?): Double? {
    sample.platformMslAltitudeMeters?.let { return it }
    val ellipsoidal = sample.ellipsoidalAltitudeMeters ?: return null
    val model = geoid ?: return null
    return ellipsoidal - model.undulationMeters(sample.latitude, sample.longitude)
}

/**
 * EGM96 15-arc-minute geoid, stored as little-endian centimeters.
 * Grid registration matches the GeographicLib egm96-15 file: row 0 is 90°N,
 * longitude starts at 0° and runs east in 0.25° steps across a full circle.
 * Bilinear interpolation. The published RMS bilinear error of this grid is about 4 cm;
 * the maximum is about 1.2 m, which is smaller than typical phone GPS vertical error.
 */
class Egm96Geoid(
    private val rows: Int,
    private val cols: Int,
    private val lat0: Double,
    private val lon0: Double,
    private val latStep: Double,
    private val lonStep: Double,
    private val centimeters: ShortArray,
) : GeoidModel {

    init {
        require(rows >= 2 && cols >= 2)
        require(centimeters.size == rows * cols)
        require(latStep != 0.0 && lonStep != 0.0)
    }

    override fun undulationMeters(latitudeDegrees: Double, longitudeDegrees: Double): Double {
        val minLat = minOf(lat0, lat0 + (rows - 1) * latStep)
        val maxLat = maxOf(lat0, lat0 + (rows - 1) * latStep)
        val lat = latitudeDegrees.coerceIn(minLat, maxLat)
        var lon = longitudeDegrees % 360.0
        if (lon < 0.0) lon += 360.0

        val rowPos = ((lat - lat0) / latStep).coerceIn(0.0, (rows - 1).toDouble())
        val r0 = floor(rowPos).toInt().coerceIn(0, rows - 1)
        val r1 = if (r0 >= rows - 1) r0 else r0 + 1
        val ty = if (r0 == r1) 0.0 else rowPos - r0

        var colPos = (lon - lon0) / lonStep
        colPos %= cols.toDouble()
        if (colPos < 0.0) colPos += cols.toDouble()
        val c0 = floor(colPos).toInt().mod(cols)
        val c1 = (c0 + 1).mod(cols)
        val tx = colPos - floor(colPos)

        val v00 = at(r0, c0)
        val v01 = at(r0, c1)
        val v10 = at(r1, c0)
        val v11 = at(r1, c1)
        return v00 * (1.0 - tx) * (1.0 - ty) +
            v01 * tx * (1.0 - ty) +
            v10 * (1.0 - tx) * ty +
            v11 * tx * ty
    }

    private fun at(row: Int, col: Int): Double = centimeters[row * cols + col].toInt() / 100.0

    companion object {
        fun decode(bytes: ByteArray): Egm96Geoid {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4)
            buffer.get(magic)
            check(magic.contentEquals(MAGIC)) { "Not an EGM96 asset" }
            val version = buffer.short.toInt()
            check(version == 1) { "Unsupported geoid asset version $version" }
            buffer.short // flags
            val rows = buffer.short.toInt() and 0xFFFF
            val cols = buffer.short.toInt() and 0xFFFF
            val lat0 = buffer.int / 10_000.0
            val lon0 = buffer.int / 10_000.0
            val latStep = buffer.int / 10_000.0
            val lonStep = buffer.int / 10_000.0
            buffer.int // reserved
            val count = rows * cols
            check(bytes.size == HEADER_BYTES + count * 2) {
                "Geoid asset size ${bytes.size} does not match ${rows}x${cols}"
            }
            val centimeters = ShortArray(count) { buffer.short }
            return Egm96Geoid(rows, cols, lat0, lon0, latStep, lonStep, centimeters)
        }

        private val MAGIC = byteArrayOf('E'.code.toByte(), 'G'.code.toByte(), '9'.code.toByte(), '6'.code.toByte())
        private const val HEADER_BYTES = 32
    }
}

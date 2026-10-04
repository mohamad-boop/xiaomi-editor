package app.xeditor.util

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Writes a Bitmap as an 8-bit palette PNG with at most [maxColors] colours (alpha
 * kept via tRNS). Frames of boot and unlock animations shrink several times this
 * way, and any PNG decoder reads them. Android's own encoder can't write palettes.
 *
 * Colours are grouped by a 4-bit-per-channel ARGB key (65 536 buckets), the palette
 * is built by median cut over those buckets, and each bucket maps to its nearest
 * palette entry once, so large frames stay fast.
 */
object PalettePng {

    fun encode(bmp: Bitmap, maxColors: Int): ByteArray {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)

        // Histogram with per-bucket sums so palette entries are true averages.
        val count = IntArray(65536)
        val sa = LongArray(65536); val sr = LongArray(65536); val sg = LongArray(65536); val sb = LongArray(65536)
        for (c in px) {
            val k = key(c)
            count[k]++
            sa[k] += (c ushr 24).toLong(); sr[k] += (c shr 16 and 0xFF).toLong()
            sg[k] += (c shr 8 and 0xFF).toLong(); sb[k] += (c and 0xFF).toLong()
        }
        val buckets = (0 until 65536).filter { count[it] > 0 }.toIntArray()
        val palette = medianCut(buckets, count, sa, sr, sg, sb, maxColors.coerceIn(2, 256))

        // Bucket -> palette index, resolved once per bucket.
        val map = IntArray(65536) { -1 }
        for (k in buckets) {
            val n = count[k]
            map[k] = nearest(palette, (sa[k] / n).toInt(), (sr[k] / n).toInt(), (sg[k] / n).toInt(), (sb[k] / n).toInt())
        }

        // Filter type 0 per row: palette images compress best unfiltered.
        val raw = ByteArray((w + 1) * h)
        var o = 0; var i = 0
        repeat(h) {
            raw[o++] = 0
            repeat(w) { raw[o++] = map[key(px[i++])].toByte() }
        }
        val idat = ByteArrayOutputStream(raw.size / 4)
        DeflaterOutputStream(idat, Deflater(Deflater.BEST_COMPRESSION), 64 * 1024).use { it.write(raw) }

        val out = ByteArrayOutputStream()
        val d = DataOutputStream(out)
        d.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A))
        chunk(d, "IHDR", ByteArrayOutputStream().also { b ->
            DataOutputStream(b).run { writeInt(w); writeInt(h); writeByte(8); writeByte(3); writeByte(0); writeByte(0); writeByte(0) }
        }.toByteArray())
        chunk(d, "PLTE", ByteArray(palette.size * 3).also { b ->
            palette.forEachIndexed { j, c -> b[j * 3] = (c shr 16).toByte(); b[j * 3 + 1] = (c shr 8).toByte(); b[j * 3 + 2] = c.toByte() }
        })
        if (palette.any { it ushr 24 != 0xFF }) {
            val lastTranslucent = palette.indexOfLast { it ushr 24 != 0xFF }
            chunk(d, "tRNS", ByteArray(lastTranslucent + 1) { (palette[it] ushr 24).toByte() })
        }
        chunk(d, "IDAT", idat.toByteArray())
        chunk(d, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun key(c: Int) =
        ((c ushr 28) shl 12) or ((c shr 20 and 0xF) shl 8) or ((c shr 12 and 0xF) shl 4) or (c shr 4 and 0xF)

    private fun medianCut(
        buckets: IntArray, count: IntArray,
        sa: LongArray, sr: LongArray, sg: LongArray, sb: LongArray,
        maxColors: Int,
    ): IntArray {
        // Per-bucket channel means, computed once.
        val mean = Array(4) { IntArray(65536) }
        for (k in buckets) {
            val n = count[k]
            mean[0][k] = (sa[k] / n).toInt(); mean[1][k] = (sr[k] / n).toInt()
            mean[2][k] = (sg[k] / n).toInt(); mean[3][k] = (sb[k] / n).toInt()
        }
        class Box(val keys: IntArray) {
            var score = 0L; var channel = 0
            init {
                if (keys.size >= 2) {
                    var pixels = 0L
                    for (k in keys) pixels += count[k]
                    for (c in 0..3) {
                        val m = mean[c]
                        var lo = 255; var hi = 0
                        for (k in keys) { val v = m[k]; if (v < lo) lo = v; if (v > hi) hi = v }
                        val sc = (hi - lo).toLong() * pixels
                        if (sc > score) { score = sc; channel = c }
                    }
                }
            }
        }
        // Split the box with the widest channel range, weighted by how many pixels it holds.
        val boxes = mutableListOf(Box(buckets))
        while (boxes.size < maxColors) {
            val best = boxes.maxByOrNull { it.score } ?: break
            if (best.score == 0L) break
            val m = mean[best.channel]
            val sorted = best.keys.sortedBy { m[it] }
            val total = sorted.sumOf { count[it].toLong() }
            var acc = 0L; var cut = 1
            for ((j, k) in sorted.withIndex()) { acc += count[k]; if (acc * 2 >= total) { cut = (j + 1).coerceIn(1, sorted.size - 1); break } }
            boxes.remove(best)
            boxes += Box(sorted.subList(0, cut).toIntArray())
            boxes += Box(sorted.subList(cut, sorted.size).toIntArray())
        }
        return IntArray(boxes.size) { bi ->
            var n = 0L; var a = 0L; var r = 0L; var g = 0L; var b = 0L
            for (k in boxes[bi].keys) { n += count[k]; a += sa[k]; r += sr[k]; g += sg[k]; b += sb[k] }
            ((a / n).toInt() shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        }
    }

    private fun nearest(palette: IntArray, a: Int, r: Int, g: Int, b: Int): Int {
        var best = 0; var bestD = Int.MAX_VALUE
        for (j in palette.indices) {
            val c = palette[j]
            val da = (c ushr 24) - a; val dr = (c shr 16 and 0xFF) - r; val dg = (c shr 8 and 0xFF) - g; val db = (c and 0xFF) - b
            val dist = 2 * da * da + dr * dr + dg * dg + db * db
            if (dist < bestD) { bestD = dist; best = j }
        }
        return best
    }

    private fun chunk(d: DataOutputStream, type: String, data: ByteArray) {
        val t = type.toByteArray(Charsets.US_ASCII)
        d.writeInt(data.size); d.write(t); d.write(data)
        d.writeInt(CRC32().apply { update(t); update(data) }.value.toInt())
    }
}

/** Colour-count choices shown in the UI; 0 = keep full colour. */
val COLOR_CHOICES = listOf(0 to "Full colour", 256 to "256 colours", 128 to "128 colours", 64 to "64 colours")

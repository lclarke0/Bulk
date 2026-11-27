package com.bulkplan.tracker.net

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * A small reader for Garmin .FIT files: enough to pull the strength "set" messages
 * (global message 225) that a watch writes when it counts reps.
 */
object Fit {
    /** One data message: field number → its raw values (arrays have several). */
    data class Msg(val global: Int, val fields: Map<Int, List<Long>>)

    data class FitSet(
        val index: Int,
        val active: Boolean,
        val reps: Int?,
        val weightKg: Double?,
        val durationS: Double?,
        /** Seconds since the FIT epoch (1989-12-31 UTC). */
        val startTime: Long?,
        val category: Int?,
    )

    const val SET = 225
    /** Seconds between the Unix epoch and the FIT epoch. */
    const val FIT_EPOCH = 631065600L

    private class Def(val global: Int, val big: Boolean, val fields: List<IntArray>, val devBytes: Int)

    private val ELEM = intArrayOf(1, 1, 1, 2, 2, 4, 4, 1, 4, 8, 1, 2, 4, 1, 8, 8, 8)

    /** Accepts a .fit file or Garmin's ZIP that holds one. */
    fun unwrap(bytes: ByteArray): ByteArray {
        if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (!e.isDirectory && e.name.lowercase().endsWith(".fit")) return z.readBytes()
                }
            }
            throw IllegalArgumentException("The download had no .fit file inside.")
        }
        return bytes
    }

    fun decode(raw: ByteArray): List<Msg> {
        val b = unwrap(raw)
        if (b.size < 12) throw IllegalArgumentException("Not a FIT file.")
        val headerSize = b[0].toInt() and 0xFF
        val dataSize = (b[4].toLong() and 0xFF) or ((b[5].toLong() and 0xFF) shl 8) or ((b[6].toLong() and 0xFF) shl 16) or ((b[7].toLong() and 0xFF) shl 24)
        if (String(b, 8, 4, Charsets.US_ASCII) != ".FIT") throw IllegalArgumentException("Not a FIT file.")
        val end = minOf(b.size.toLong(), headerSize + dataSize).toInt()
        val defs = arrayOfNulls<Def>(16)
        val out = mutableListOf<Msg>()
        var p = headerSize
        fun u8() = (b[p++].toInt() and 0xFF)
        fun read(size: Int, big: Boolean): Long {
            var v = 0L
            for (i in 0 until size) {
                val byte = b[p + i].toLong() and 0xFF
                v = if (big) (v shl 8) or byte else v or (byte shl (8 * i))
            }
            p += size
            return v
        }
        while (p < end) {
            val h = u8()
            if (h and 0x80 != 0) {
                // Compressed-timestamp header: a data message for local type 0–3.
                val d = defs[(h shr 5) and 0x3] ?: break
                out += readData(d, ::read) { p += it }
                continue
            }
            val local = h and 0x0F
            if (h and 0x40 != 0) {
                p++ // reserved
                val big = u8() == 1
                val global = read(2, big).toInt()
                val n = u8()
                val fields = List(n) { intArrayOf(u8(), u8(), u8()) }
                var dev = 0
                if (h and 0x20 != 0) { val nd = u8(); repeat(nd) { p++; dev += u8(); p++ } }
                defs[local] = Def(global, big, fields, dev)
            } else {
                val d = defs[local] ?: break
                out += readData(d, ::read) { p += it }
            }
        }
        return out
    }

    private inline fun readData(d: Def, read: (Int, Boolean) -> Long, skip: (Int) -> Unit): Msg {
        val f = HashMap<Int, List<Long>>()
        for ((num, size, base) in d.fields.map { Triple(it[0], it[1], it[2]) }) {
            val t = base and 0x1F
            val es = if (t < ELEM.size) ELEM[t] else 1
            if (t == 7 || size % es != 0) { skip(size); continue } // strings and odd sizes aren't needed here
            f[num] = List(size / es) { read(es, d.big) }
        }
        skip(d.devBytes)
        return Msg(d.global, f)
    }

    /** The strength sets in a file, in order. Rest periods come back with active = false. */
    fun sets(raw: ByteArray): List<FitSet> = decode(raw).filter { it.global == SET }.mapIndexed { i, m ->
        fun one(n: Int, invalid: Long) = m.fields[n]?.firstOrNull()?.takeIf { it != invalid }
        FitSet(
            index = (one(10, 0xFFFF) ?: one(254, 0xFFFF))?.toInt() ?: i,
            active = (one(5, 0xFF) ?: 1L) == 1L,
            reps = one(3, 0xFFFF)?.toInt(),
            weightKg = one(4, 0xFFFF)?.let { it / 16.0 }?.takeIf { it > 0 },
            durationS = one(0, 0xFFFFFFFFL)?.let { it / 1000.0 },
            startTime = one(6, 0xFFFFFFFFL),
            category = m.fields[7]?.firstOrNull { it != 0xFFFFL && it != 0xFFFEL }?.toInt(),
        )
    }

    /** Garmin's exercise categories mapped to words that appear in exercise names. */
    private val CATEGORY_WORDS = mapOf(
        0 to listOf("bench"), 1 to listOf("calf"), 6 to listOf("crunch"), 7 to listOf("curl"), 8 to listOf("deadlift", "rdl"),
        9 to listOf("fly", "flye"), 14 to listOf("lateral"), 15 to listOf("leg curl", "lying curl", "seated curl", "hamstring curl"),
        16 to listOf("leg raise", "knee raise"), 17 to listOf("lunge", "split squat"), 21 to listOf("pull-up", "pullup", "pulldown", "chin"),
        22 to listOf("push-up", "pushup", "dip"), 23 to listOf("row"), 24 to listOf("shoulder press", "overhead press", "ohp"),
        26 to listOf("shrug"), 28 to listOf("squat", "leg press", "hack"), 30 to listOf("triceps", "skull", "pushdown"),
    )

    /**
     * Suggests which exercise each counted set belongs to: an exercise named like the
     * watch's category when there is one, otherwise the next exercise in workout order,
     * moving on once it has its planned number of sets.
     */
    fun assign(sets: List<FitSet>, exercises: List<Pair<String, String>>, planned: List<Int>): List<String?> {
        val counts = IntArray(exercises.size)
        var cur = 0
        return sets.map { s ->
            val words = s.category?.let { CATEGORY_WORDS[it] }
            val byCat = words?.let { w -> exercises.indices.firstOrNull { i -> w.any { exercises[i].second.lowercase().contains(it) } && counts[i] < planned.getOrElse(i) { 3 } + 1 } }
            val i = byCat ?: run {
                while (cur < exercises.size && counts[cur] >= planned.getOrElse(cur) { 3 }) cur++
                cur.takeIf { it < exercises.size }
            }
            if (i == null) null else { counts[i]++; exercises[i].first }
        }
    }
}

package com.tavoohoh.quiet.core

/**
 * Deterministic PRNG (SplitMix64), port of QuietCore's `SeededRandom`. Same seed, same
 * sequence as iOS; bounded values use the same rejection sampling (`fixtures/core.json`).
 */
class SeededRandom(seed: ULong) {
    var state: ULong = seed
        private set

    fun next(): ULong {
        state += 0x9E3779B97F4A7C15uL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return z xor (z shr 31)
    }

    /** Uniform integer in `lower..upper` (both included), unbiased. Swift's `Int` is 64-bit. */
    fun nextLong(lower: Long, upper: Long): Long {
        val span = (upper - lower).toULong() + 1uL
        val limit = ULong.MAX_VALUE - (ULong.MAX_VALUE % span)
        var value = next()
        while (value >= limit) value = next()
        return lower + (value % span).toLong()
    }

    fun nextInt(lower: Int, upper: Int): Int = nextLong(lower.toLong(), upper.toLong()).toInt()

    fun nextInt(range: IntRange): Int = nextInt(range.first, range.last)

    override fun equals(other: Any?): Boolean = other is SeededRandom && other.state == state
    override fun hashCode(): Int = state.hashCode()
}

/** Stable 64-bit FNV-1a over the UTF-8 bytes. */
object FNV1a {
    fun hash(string: String): ULong {
        var hash = 0xCBF29CE484222325uL
        for (byte in string.encodeToByteArray()) {
            hash = hash xor (byte.toUByte().toULong())
            hash *= 0x00000100000001B3uL
        }
        return hash
    }
}

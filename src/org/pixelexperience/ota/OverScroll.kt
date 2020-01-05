package org.pixelexperience.ota

import kotlin.math.abs
import kotlin.math.roundToInt

object OverScroll {
    private const val OVERSCROLL_DAMP_FACTOR = 0.07f

    @Suppress("NAME_SHADOWING")
    private fun overScrollInfluenceCurve(f: Float): Float {
        var f = f
        f -= 1.0f
        return f * f * f + 1.0f
    }

    fun dampedScroll(amount: Float, max: Int): Int {
        if (amount.compareTo(0f) == 0) return 0
        var f = amount / max
        f =
            f / abs(f) * overScrollInfluenceCurve(abs(f))
        if (abs(f) >= 1) {
            f /= abs(f)
        }
        return (OVERSCROLL_DAMP_FACTOR * f * max).roundToInt()
    }
}

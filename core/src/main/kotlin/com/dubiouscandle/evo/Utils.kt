package com.dubiouscandle.evo

import java.util.*
import java.util.concurrent.atomic.AtomicLong

const val PI = Math.PI.toFloat()
const val TAU = 2 * Math.PI.toFloat()

fun normAngle(angle: Float): Float {
    return ((angle % TAU) + TAU) % TAU
}

fun Random.gaussianf(sd: Float): Float {
    return nextGaussian().toFloat() * sd
}
fun Random.nextBoolean(trueChance: Float): Boolean {
    return nextFloat() < trueChance
}

fun FloatArray.transform(func: (Float) -> Float) {
    for (i in 0..<size) {
        this[i] = func(this[i])
    }
}

object InnovationTracker {
    private val nextAddress = AtomicLong(0)
    fun getNextAddress(): Long = nextAddress.getAndIncrement()
}

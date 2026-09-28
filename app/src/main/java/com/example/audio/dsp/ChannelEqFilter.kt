package com.example.audio.dsp

class ChannelEqFilter(sampleRate: Int = 48000) {

    private val lowFilter = BiquadFilter(sampleRate)
    private val midFilter = BiquadFilter(sampleRate)
    private val highFilter = BiquadFilter(sampleRate)

    private var currentLow = 1.0f
    private var currentMid = 1.0f
    private var currentHigh = 1.0f

    init {
        updateFilters()
    }

    fun setSampleRate(sr: Int) {
        lowFilter.setSampleRate(sr)
        midFilter.setSampleRate(sr)
        highFilter.setSampleRate(sr)
        updateFilters()
    }

    /**
     * Values from 0.0 (kill) to 1.0 (flat 0dB) to 2.0 (+6dB boost)
     */
    fun setGains(low: Float, mid: Float, high: Float) {
        if (low != currentLow || mid != currentMid || high != currentHigh) {
            currentLow = low.coerceIn(0f, 2f)
            currentMid = mid.coerceIn(0f, 2f)
            currentHigh = high.coerceIn(0f, 2f)
            updateFilters()
        }
    }

    private fun eqValueToDb(value: Float): Float {
        return when {
            value <= 0.005f -> -48.0f // Isolator Kill
            value < 1.0f -> -48.0f * (1.0f - value)
            else -> (value - 1.0f) * 6.0f // +6dB boost
        }
    }

    private fun updateFilters() {
        val lowDb = eqValueToDb(currentLow)
        val midDb = eqValueToDb(currentMid)
        val highDb = eqValueToDb(currentHigh)

        lowFilter.configure(FilterType.LOW_SHELF, 260f, 0.707f, lowDb)
        midFilter.configure(FilterType.PEAKING, 1100f, 0.9f, midDb)
        highFilter.configure(FilterType.HIGH_SHELF, 3200f, 0.707f, highDb)
    }

    fun processLeft(input: Float): Float {
        val s1 = lowFilter.processLeft(input)
        val s2 = midFilter.processLeft(s1)
        return highFilter.processLeft(s2)
    }

    fun processRight(input: Float): Float {
        val s1 = lowFilter.processRight(input)
        val s2 = midFilter.processRight(s1)
        return highFilter.processRight(s2)
    }

    fun reset() {
        lowFilter.reset()
        midFilter.reset()
        highFilter.reset()
    }
}

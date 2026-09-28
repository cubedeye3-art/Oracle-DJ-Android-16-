package com.example.audio.dsp

import kotlin.math.abs
import kotlin.math.pow

class DJFilter(private var sampleRate: Int = 48000) {

    private val lpf = BiquadFilter(sampleRate)
    private val hpf = BiquadFilter(sampleRate)
    private var currentFilterValue = 0.0f // -1.0 .. +1.0

    init {
        setFilterValue(0.0f)
    }

    fun setSampleRate(sr: Int) {
        sampleRate = sr
        lpf.setSampleRate(sr)
        hpf.setSampleRate(sr)
        setFilterValue(currentFilterValue)
    }

    /**
     * Filter value:
     * -1.0 (Extreme LPF ~50Hz) ... 0.0 (Neutral/Bypass) ... +1.0 (Extreme HPF ~12kHz)
     */
    fun setFilterValue(value: Float) {
        val clamped = value.coerceIn(-1.0f, 1.0f)
        currentFilterValue = clamped

        if (clamped < -0.01f) {
            // Low-pass filter active
            val amount = abs(clamped)
            // Logarithmic cutoff sweep from 18,000 Hz down to 60 Hz
            val cutoff = 18000.0f * (0.0033f).pow(amount)
            val resonance = 1.0f + amount * 1.8f
            lpf.configure(FilterType.LOW_PASS, cutoff, resonance)
        } else if (clamped > 0.01f) {
            // High-pass filter active
            val amount = clamped
            // Logarithmic cutoff sweep from 25 Hz up to 12,000 Hz
            val cutoff = 25.0f * (480.0f).pow(amount)
            val resonance = 1.0f + amount * 1.8f
            hpf.configure(FilterType.HIGH_PASS, cutoff, resonance)
        }
    }

    fun processLeft(input: Float): Float {
        return when {
            currentFilterValue < -0.01f -> lpf.processLeft(input)
            currentFilterValue > 0.01f -> hpf.processLeft(input)
            else -> input
        }
    }

    fun processRight(input: Float): Float {
        return when {
            currentFilterValue < -0.01f -> lpf.processRight(input)
            currentFilterValue > 0.01f -> hpf.processRight(input)
            else -> input
        }
    }

    fun reset() {
        lpf.reset()
        hpf.reset()
    }
}

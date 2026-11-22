package com.music.bitchord.playback.audio

import com.music.bitchord.playback.SpatialAudioProcessor
import com.music.bitchord.playback.TransitionFilterProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Comprehensive verification of the Float32 DSP processors:
 * - SpatialAudioProcessor (FloatAudioProcessor)
 * - TransitionFilterProcessor (FloatAudioProcessor)
 * - DspChain composite pipeline
 */
class FloatDspChainTest {

    private val sampleRate = 44100

    // ── SpatialAudioProcessor Float32 Tests ─────────────────────────────────

    @Test
    fun `spatial crossfeed leaks delayed and low-passed signal to opposite channel`() {
        val spatial = SpatialAudioProcessor()
        spatial.configure(sampleRate, 2)
        spatial.enabled = true

        // 15ms delay at 44.1kHz is ~661 frames
        val frames = 1000
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        // Feed only Left channel with a 500 Hz sine
        for (f in 0 until frames) {
            block.samples[f * 2] = (0.5 * sin(2.0 * PI * 500.0 * f / sampleRate)).toFloat()
            block.samples[f * 2 + 1] = 0.0f
        }
        block.reset(frames)

        spatial.process(block)

        // After the delay buffer fills (>700 frames), the right channel must have crossfeed signal
        var rightEnergy = 0.0
        for (f in 700 until frames) {
            rightEnergy += abs(block.samples[f * 2 + 1].toDouble())
        }
        assertTrue("Crossfeed must produce non-zero signal on right channel", rightEnergy > 1.0)
    }

    @Test
    fun `spatial bypass passes samples through untouched when disabled`() {
        val spatial = SpatialAudioProcessor()
        spatial.configure(sampleRate, 2)
        spatial.enabled = false

        val frames = 64
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        for (i in 0 until frames * 2) {
            block.samples[i] = (i + 1) * 0.01f
        }
        block.reset(frames)

        spatial.process(block)

        for (i in 0 until frames * 2) {
            assertEquals((i + 1) * 0.01f, block.samples[i], 0.0f)
        }
    }

    // ── TransitionFilterProcessor Float32 Tests ─────────────────────────────

    @Test
    fun `transition low pass attenuates high frequency signal`() {
        val transition = TransitionFilterProcessor()
        transition.configure(sampleRate, 2)
        transition.setCutoffs(lowPassHz = 500f, highPassHz = TransitionFilterProcessor.OFF_HZ)
        transition.flush()

        // Feed 5 kHz sine wave
        val frames = 1024
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        val freq = 5000.0
        for (f in 0 until frames) {
            val s = sin(2.0 * PI * freq * f / sampleRate).toFloat()
            block.samples[f * 2] = s
            block.samples[f * 2 + 1] = s
        }
        block.reset(frames)

        transition.process(block)

        // 5 kHz is 10x above 500 Hz cutoff; with 24 dB/octave it should be heavily attenuated
        var peakOut = 0f
        for (f in 512 until frames) {
            val amp = abs(block.samples[f * 2])
            if (amp > peakOut) peakOut = amp
        }
        assertTrue("5 kHz through 500 Hz low-pass must be attenuated (got peak $peakOut)", peakOut < 0.1f)
    }

    @Test
    fun `transition high pass attenuates low frequency signal`() {
        val transition = TransitionFilterProcessor()
        transition.configure(sampleRate, 2)
        transition.setCutoffs(lowPassHz = TransitionFilterProcessor.OPEN_HZ, highPassHz = 2000f)
        transition.flush()

        // Feed 100 Hz sine wave
        val frames = 1024
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        val freq = 100.0
        for (f in 0 until frames) {
            val s = sin(2.0 * PI * freq * f / sampleRate).toFloat()
            block.samples[f * 2] = s
            block.samples[f * 2 + 1] = s
        }
        block.reset(frames)

        transition.process(block)

        var peakOut = 0f
        for (f in 512 until frames) {
            val amp = abs(block.samples[f * 2])
            if (amp > peakOut) peakOut = amp
        }
        assertTrue("100 Hz through 2000 Hz high-pass must be attenuated (got peak $peakOut)", peakOut < 0.1f)
    }

    @Test
    fun `transition bypass passes samples through untouched when open`() {
        val transition = TransitionFilterProcessor()
        transition.configure(sampleRate, 2)
        transition.open()
        transition.flush()

        val frames = 64
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        for (i in 0 until frames * 2) {
            block.samples[i] = (i + 1) * 0.01f
        }
        block.reset(frames)

        transition.process(block)

        for (i in 0 until frames * 2) {
            assertEquals((i + 1) * 0.01f, block.samples[i], 0.0f)
        }
    }

    // ── Cross-Processor (DspChain) Tests ────────────────────────────────────

    @Test
    fun `dsp chain preserves float32 precision across processors without intermediate quantization`() {
        val chain = DspChain()
        chain.configure(sampleRate, 2)

        // Enable spatial, open transition
        chain.spatial.enabled = true
        chain.transition.open()
        chain.flush()

        // Feed tiny sub-LSB float (1.0e-7)
        val tinyVal = 1.0e-7f
        val frames = 128
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        for (f in 0 until frames) {
            block.samples[f * 2] = tinyVal
            block.samples[f * 2 + 1] = tinyVal
        }
        block.reset(frames)

        chain.process(block)

        // Must remain non-zero through stages
        var hasNonZero = false
        for (f in 0 until frames) {
            if (block.samples[f * 2] != 0.0f) hasNonZero = true
        }
        assertTrue("Sub-LSB Float32 signal must survive entire DSP chain", hasNonZero)
    }

    @Test
    fun `zero length input is handled safely across entire chain`() {
        val chain = DspChain()
        chain.configure(sampleRate, 2)
        val block = AudioBlock(channelCount = 2, capacityFrames = 64)
        block.reset(0)

        // Must return without error
        chain.process(block)
        assertEquals(0, block.frameCount)
    }

    @Test
    fun `repeated block processing maintains filter continuity and stability`() {
        val chain = DspChain()
        chain.configure(sampleRate, 2)

        chain.spatial.enabled = true
        chain.transition.setCutoffs(lowPassHz = 8000f, highPassHz = 100f)

        val blockSize = 512
        val block = AudioBlock(channelCount = 2, capacityFrames = blockSize)

        // Run 50 consecutive blocks (1 second of audio)
        for (iteration in 0 until 50) {
            for (f in 0 until blockSize) {
                val totalFrame = iteration * blockSize + f
                val s = sin(2.0 * PI * 440.0 * totalFrame / sampleRate).toFloat()
                block.samples[f * 2] = s
                block.samples[f * 2 + 1] = s
            }
            block.reset(blockSize)
            chain.process(block)

            // Verify no NaN or Infinity emerged
            for (i in 0 until blockSize * 2) {
                val v = block.samples[i]
                assertFalse("Sample must not be NaN at block $iteration", v.isNaN())
                assertFalse("Sample must not be Infinite at block $iteration", v.isInfinite())
            }
        }
    }

    @Test
    fun `steady state processing reuses audio block samples array without allocation`() {
        val chain = DspChain()
        chain.configure(sampleRate, 2)

        val block = AudioBlock(channelCount = 2, capacityFrames = 128)
        val arrayRef = block.samples

        for (i in 0 until 10) {
            block.reset(128)
            chain.process(block)
            assertTrue("Array reference must be preserved across iterations", block.samples === arrayRef)
        }
    }
}

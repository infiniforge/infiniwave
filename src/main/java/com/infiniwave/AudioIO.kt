package com.infiniwave

import com.infinifolder.audio.AudioFileProcessor
import com.infinifolder.audio.DecoderPreference
import com.infinifolder.audio.stream.FileAudioInput
import com.infiniwave.editor.WaveProject
import io.github.gretchycat.infinisynth.AudioBuffer
import java.io.*
import kotlin.math.*

/** Explicit bounded staging and PCM conversion; codec internals remain in libinfinisound. */
object AudioIO {
    fun decode(file: File, name: String): AudioBuffer {
        val stream = AudioFileProcessor().openPcm(FileAudioInput(file, name), DecoderPreference.PORTABLE_ONLY)
        return stream.use { pcm ->
            val format = pcm.format
            var samples = FloatArray(65536)
            var count = 0
            val block = IntArray(4096 * format.channels)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Import cancelled")
                val n = pcm.read(block, 0, block.size)
                if (n < 0) break
                require(n > 0 && n <= block.size && n % format.channels == 0) { "Invalid PCM stream" }
                require(count.toLong() + n <= AudioBuffer.MAX_SAMPLES) { "Decoded audio exceeds import budget" }
                if (count + n > samples.size) samples = samples.copyOf(min(AudioBuffer.MAX_SAMPLES, max(count + n, samples.size * 2)))
                repeat(n) { samples[count + it] = block[it] / 2147483648f }
                count += n
            }
            require(count > 0) { "No decoded audio" }
            val sourceFrames = count / format.channels
            val frames = ceil(sourceFrames.toDouble() * WaveProject.RATE / format.sampleRate).toInt()
            require(frames.toLong() * format.channels <= AudioBuffer.MAX_SAMPLES) { "Resampled import exceeds PCM budget" }
            val output = FloatArray(frames * format.channels)
            repeat(frames) { frame ->
                if (frame and 1023 == 0 && Thread.currentThread().isInterrupted) throw InterruptedIOException("Import cancelled")
                val p = frame.toDouble() * format.sampleRate / WaveProject.RATE
                val a = min(sourceFrames - 1, p.toInt()); val b = min(sourceFrames - 1, a + 1)
                val fraction = (p - a).coerceIn(0.0,1.0)
                repeat(format.channels) { ch -> output[frame * format.channels + ch] = (samples[a * format.channels + ch] * (1 - fraction) + samples[b * format.channels + ch] * fraction).toFloat() }
            }
            AudioBuffer(WaveProject.RATE, format.channels, output)
        }
    }
    /** Export saturates at full scale; editing and mixing preserve unclipped float levels. */
    fun writeWave(file: File, audio: AudioBuffer) {
        file.outputStream().buffered().use { out ->
            fun le16(v: Int) { out.write(v and 255); out.write(v ushr 8 and 255) }
            fun le32(v: Int) { repeat(4) { out.write(v ushr (it*8) and 255) } }
            val size = audio.frames() * audio.channels() * 2
            out.write("RIFF".toByteArray());le32(36+size);out.write("WAVEfmt ".toByteArray());le32(16);le16(1);le16(audio.channels())
            le32(audio.sampleRate());le32(audio.sampleRate()*audio.channels()*2);le16(audio.channels()*2);le16(16);out.write("data".toByteArray());le32(size)
            repeat(audio.frames()) { frame ->
                if (frame and 1023 == 0 && Thread.currentThread().isInterrupted) throw InterruptedIOException("Export cancelled")
                repeat(audio.channels()) { channel -> le16((audio.sample(frame,channel).coerceIn(-1f,1f) * 32767).roundToInt()) }
            }
        }
    }
}

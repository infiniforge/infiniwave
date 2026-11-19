package com.infiniwave

import com.infiniwave.editor.*
import com.infinifolder.audio.processing.PcmEffects
import io.github.gretchycat.infinisynth.AudioBuffer
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class StereoDifferenceTest {
    @Test fun splitInvertOneSideAndMixToMonoKeepsOtherWaveformsAndCancelsCommonAudio() {
        val source=AudioBuffer(44100,2,floatArrayOf(.6f,.6f,.8f,.2f,-.4f,.2f))
        val original=WaveProject().add("Stereo",source,20).add("Unrelated",AudioBuffer(44100,1,floatArrayOf(.9f)),0)
        val split=original.splitChannels(original.tracks()[0].id())
        val left=split.tracks()[0];val right=split.tracks()[1];val other=split.tracks()[2]
        val inverted=split.process(right.id(),20,23){input,a,b->AudioBuffer(44100,1,PcmEffects.invert(input.samples(),1,a,b))}
        assertSame(left,inverted.track(left.id()));assertSame(other,inverted.track(other.id()))
        assertArrayEquals(floatArrayOf(-.6f,-.2f,-.2f),inverted.track(right.id()).audio().samples(),0f)
        val mixed=inverted.mixToMono(listOf(left.id(),right.id()),"Difference",true)
        val result=mixed.tracks()[0]
        assertEquals(1,result.audio().channels());assertEquals(20,result.offset());assertSame(other,mixed.tracks()[1])
        assertArrayEquals(floatArrayOf(0f,.3f,-.3f),result.audio().samples(),1e-7f)
        assertArrayEquals(floatArrayOf(.6f,.6f,.8f,.2f,-.4f,.2f),source.samples(),0f)
        val history=ProjectHistory();history.reset(original);history.apply(split);history.apply(inverted);history.apply(mixed)
        history.undo();assertSame(inverted,history.current());history.undo();assertSame(split,history.current());history.undo();assertSame(original,history.current())
        val file=Files.createTempFile("mono-difference-",".wav").toFile()
        try {
            AudioIO.writeWave(file,result.audio());val decoded=AudioIO.decode(file,"difference.wav")
            assertEquals(1,decoded.channels());assertArrayEquals(floatArrayOf(0f,.3f,-.3f),decoded.samples(),.0001f)
        } finally { file.delete() }
    }
}

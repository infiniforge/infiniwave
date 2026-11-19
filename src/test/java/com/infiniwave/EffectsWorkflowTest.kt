package com.infiniwave

import com.infiniwave.editor.*
import com.infinifolder.audio.processing.PcmEffects
import io.github.gretchycat.infinisynth.AudioBuffer
import org.junit.Assert.*
import org.junit.Test

class EffectsWorkflowTest {
    @Test fun selectedEffectSurvivesSaveAndUndoWithoutChangingTheSource(){
        val original=WaveProject().add("Stereo",AudioBuffer(44100,2,floatArrayOf(.1f,.2f,.3f,.4f,.5f,.6f)),441)
        val id=original.tracks()[0].id()
        val edited=original.process(id,442,444){input,a,b->AudioBuffer(44100,2,PcmEffects.invert(input.samples(),2,a,b))}
        val bytes=java.io.ByteArrayOutputStream();ProjectFile.write(edited,bytes)
        val loaded=ProjectFile.read(java.io.ByteArrayInputStream(bytes.toByteArray()))
        assertArrayEquals(floatArrayOf(.1f,.2f,-.3f,-.4f,-.5f,-.6f),loaded.track(id).audio().samples(),0f)
        val history=ProjectHistory();history.reset(original);history.apply(edited);history.undo();assertSame(original,history.current());history.redo();assertSame(edited,history.current())
        assertEquals(441,edited.track(id).offset());assertEquals(2,loaded.track(id).audio().channels())
    }
}

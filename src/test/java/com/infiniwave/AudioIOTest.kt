package com.infiniwave
import io.github.gretchycat.infinisynth.AudioBuffer
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AudioIOTest {
    @Test fun waveExportAndImportPreserveChannelSeparationAndClipOnlyAtExport() {
        val file=Files.createTempFile("wave-io-",".wav").toFile()
        try{
            val audio=AudioBuffer(44100,2,floatArrayOf(2f,-2f,.5f,-.5f,0f,0f))
            AudioIO.writeWave(file,audio)
            val decoded=AudioIO.decode(file,"test.wav")
            assertEquals(2,decoded.channels());assertEquals(3,decoded.frames())
            assertEquals(1f,decoded.sample(0,0),.0001f);assertEquals(-1f,decoded.sample(0,1),.0001f)
            assertEquals(.5f,decoded.sample(1,0),.0001f);assertEquals(-.5f,decoded.sample(1,1),.0001f)
            assertEquals(2f,audio.sample(0,0),0f)
        }finally{file.delete()}
    }
}

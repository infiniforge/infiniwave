package com.infiniwave.editor;
import io.github.gretchycat.infinisynth.AudioBuffer;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;

public class EditorTest {
    private AudioBuffer mono(float...samples){return new AudioBuffer(44100,1,samples);}
    @Test public void workspaceHasNoFixedTrackCountAndSharesDuplicatedPcm(){
        WaveProject p=new WaveProject();for(int i=0;i<100;i++)p=p.add("Wave "+i,mono(1,0,-1),i);
        assertEquals(100,p.tracks().size());assertEquals(102,p.endFrame());
        var t=p.tracks().get(0);var d=p.duplicate(t.id());assertSame(t.audio(),d.tracks().get(100).audio());
    }
    @Test public void splitAndRecombinePreserveIndependentStereoChannels()throws Exception{
        var b=new AudioBuffer(44100,2,new float[]{1,.1f,.5f,.2f,0,.3f});var p=new WaveProject().add("Stereo",b,0);
        var split=p.splitChannels(p.tracks().get(0).id());assertEquals(2,split.tracks().size());
        assertArrayEquals(b.samples(),split.mix(0,3,2).samples(),0);
        assertArrayEquals(b.samples(),split.combineAsChannels().tracks().get(0).audio().samples(),0);
    }
    @Test public void channelRoutingMuteSoloAndOffsetsMixCorrectly()throws Exception{
        var p=new WaveProject().add("Left",mono(1,1),1).add("Right",mono(.5f,.5f),0);
        var l=p.tracks().get(0);var r=p.tracks().get(1);
        p=p.replace(l.settings(l.name(),1,1,0,false,false,0)).replace(r.settings(r.name(),0,1,0,false,false,1));
        assertArrayEquals(new float[]{0,.5f,1,.5f,1,0},p.mix(0,3,2).samples(),0);
        var solo=p.replace(p.track(l.id()).settings(l.name(),1,1,0,false,true,0));
        assertArrayEquals(new float[]{0,0,1,0,1,0},solo.mix(0,3,2).samples(),0);
        final WaveProject routed=p;assertThrows(IllegalArgumentException.class,()->routed.mix(0,3,1));
    }
    @Test public void editsAreNondestructiveAndClipSelectionsToTrack()throws Exception{
        var p=new WaveProject().add("Clip",mono(1,2,3,4),10);String id=p.tracks().get(0).id();
        assertArrayEquals(new float[]{1,4},p.edit(id,11,13,WaveProject.Edit.DELETE).track(id).audio().samples(),0);
        assertArrayEquals(new float[]{1,3,2,4},p.edit(id,11,13,WaveProject.Edit.REVERSE).track(id).audio().samples(),0);
        assertArrayEquals(new float[]{1,2,3,4},p.track(id).audio().samples(),0);
        var trim=p.edit(id,11,99,WaveProject.Edit.TRIM).track(id);assertEquals(11,trim.offset());assertArrayEquals(new float[]{2,3,4},trim.audio().samples(),0);
        assertThrows(IllegalArgumentException.class,()->p.edit(id,0,1,WaveProject.Edit.DELETE));
    }
    @Test public void copyPasteAndHistoryRestoreExactAudio()throws Exception{
        var p=new WaveProject().add("Clip",mono(1,2,3,4),0);String id=p.tracks().get(0).id();var copied=p.copyRange(id,1,3);
        var pasted=p.paste(id,2,copied);assertArrayEquals(new float[]{1,2,2,3,3,4},pasted.track(id).audio().samples(),0);
        var h=new ProjectHistory();h.reset(p);h.apply(pasted);h.undo();assertSame(p,h.current());h.redo();assertSame(pasted,h.current());
    }
    @Test public void projectRoundTripKeepsRoutingAndSharedBuffers()throws Exception{
        var p=new WaveProject().add("Synth",mono(1,.5f,-.2f),44);String id=p.tracks().get(0).id();p=p.duplicate(id);
        var out=new ByteArrayOutputStream();ProjectFile.write(p,out);var loaded=ProjectFile.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(2,loaded.tracks().size());assertEquals(44,loaded.tracks().get(0).offset());assertSame(loaded.tracks().get(0).audio(),loaded.tracks().get(1).audio());
        assertArrayEquals(p.tracks().get(0).audio().samples(),loaded.tracks().get(0).audio().samples(),0);
        assertThrows(IOException.class,()->ProjectFile.read(new ByteArrayInputStream(new byte[16])));
        byte[] truncated=java.util.Arrays.copyOf(out.toByteArray(),out.size()-1);assertThrows(IOException.class,()->ProjectFile.read(new ByteArrayInputStream(truncated)));
    }
}

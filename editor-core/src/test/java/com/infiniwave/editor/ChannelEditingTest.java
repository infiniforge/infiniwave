package com.infiniwave.editor;
import io.github.gretchycat.infinisynth.AudioBuffer;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;

public class ChannelEditingTest {
    private AudioBuffer mono(float...data){return new AudioBuffer(44100,1,data);}
    @Test public void recombiningChosenPairKeepsUnrelatedTracksAndChannelOrder()throws Exception{
        var p=new WaveProject().add("Unrelated",mono(.7f,.8f),0).add("Right",mono(.1f,.2f),12).add("Left",mono(.3f,.4f),10);
        var unrelated=p.tracks().get(0);String right=p.tracks().get(1).id(),left=p.tracks().get(2).id();
        var result=p.combineChannels(List.of(left,right),"Stereo");assertEquals(2,result.tracks().size());assertSame(unrelated,result.tracks().get(0));
        var joined=result.tracks().get(1);assertEquals(10,joined.offset());assertEquals(2,joined.audio().channels());
        assertArrayEquals(new float[]{.3f,0,.4f,0,0,.1f,0,.2f},joined.audio().samples(),0);
        assertEquals(3,p.tracks().size());assertEquals(-1,joined.route());
    }
    @Test public void splitAndRecombinePreserveMixerLevelsAndUndo()throws Exception{
        var p=new WaveProject().add("Stereo",new AudioBuffer(44100,2,new float[]{.6f,.3f,.2f,.1f}),123);
        var t=p.tracks().get(0);p=p.replace(t.settings(t.name(),123,.5,.25,false,false,-1));
        var split=p.splitChannels(t.id());assertTrue(split.tracks().get(0).name().endsWith("Left"));assertTrue(split.tracks().get(1).name().endsWith("Right"));
        assertArrayEquals(p.mix(123,125,2).samples(),split.mix(123,125,2).samples(),1e-7f);
        var ids=List.of(split.tracks().get(0).id(),split.tracks().get(1).id());var combined=split.combineChannels(ids,"Joined");
        assertArrayEquals(p.mix(123,125,2).samples(),combined.mix(123,125,2).samples(),1e-7f);
        var history=new ProjectHistory();history.reset(p);history.apply(split);history.apply(combined);history.undo();assertSame(split,history.current());history.undo();assertSame(p,history.current());
    }
    @Test public void combiningValidatesBeforeReplacingAnything()throws Exception{
        var p=new WaveProject().add("A",mono(.3f),0).add("B",mono(.4f),0);String a=p.tracks().get(0).id();
        assertThrows(IllegalArgumentException.class,()->p.combineChannels(List.of(a,a),"Bad"));
        assertThrows(IllegalArgumentException.class,()->p.combineChannels(List.of(a,"missing"),"Bad"));
        var profile=p.addProfile("FM",new RenderProfile(RenderProfile.Engine.FM,Map.of("operators","2")),mono(.5f),0);String id=profile.tracks().get(2).id();
        assertThrows(IllegalArgumentException.class,()->profile.combineChannels(List.of(a,id),"Bad"));
        assertEquals(3,profile.tracks().size());
    }
    @Test public void selectedEffectsKeepIdentityAndTimingAndRejectDurationChanges()throws Exception{
        var p=new WaveProject().add("Clip",mono(.1f,.2f,.3f),100);String id=p.tracks().get(0).id();
        var changed=p.process(id,101,200,(input,a,b)->{assertEquals(1,a);assertEquals(3,b);float[] out=input.samples();for(int i=a;i<b;i++)out[i]=-out[i];return mono(out);});
        assertEquals(id,changed.tracks().get(0).id());assertEquals(100,changed.track(id).offset());assertArrayEquals(new float[]{.1f,-.2f,-.3f},changed.track(id).audio().samples(),0);
        assertThrows(IllegalArgumentException.class,()->p.process(id,100,103,(input,a,b)->mono(1)));
        assertArrayEquals(new float[]{.1f,.2f,.3f},p.track(id).audio().samples(),0);
    }
    @Test public void combineRespectsMuteAndSoloAndPersistsAsOrdinaryStereo()throws Exception{
        var p=new WaveProject().add("L",mono(.5f,.5f),0).add("R",mono(.2f,.2f),0);
        var left=p.tracks().get(0);var right=p.tracks().get(1);p=p.replace(right.settings(right.name(),0,1,0,true,false,1));
        var result=p.combineChannels(List.of(left.id(),right.id()),"Stereo");assertArrayEquals(new float[]{.5f,0,.5f,0},result.tracks().get(0).audio().samples(),0);
        ByteArrayOutputStream out=new ByteArrayOutputStream();ProjectFile.write(result,out);var read=ProjectFile.read(new ByteArrayInputStream(out.toByteArray()));assertArrayEquals(result.tracks().get(0).audio().samples(),read.tracks().get(0).audio().samples(),0);
    }
}

package com.infiniwave.editor;

import io.github.gretchycat.infinisynth.AudioBuffer;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.InterruptedIOException;
import java.util.*;

public class PlaybackMixerTest {
    private WaveProject set(WaveProject p,String id,boolean mute,boolean solo) {
        var t=p.track(id);return p.replace(t.settings(t.name(),t.offset(),t.gain(),t.pan(),mute,solo,t.route()));
    }
    private WaveProject pair(int frames,int offset) {
        float[] left=new float[frames],right=new float[frames];
        for(int f=0;f<frames;f++){left[f]=f*.0001f;right[f]=-.25f;}
        var p=new WaveProject().add("L",new AudioBuffer(44100,1,left),offset).add("R",new AudioBuffer(44100,1,right),offset);
        var l=p.tracks().get(0);var r=p.tracks().get(1);
        return p.replace(l.settings("L",offset,1,0,false,false,0)).replace(r.settings("R",offset,1,0,false,false,1));
    }
    @Test public void liveMuteSoloAndAllMutedContinueSameCursorUntilOriginalEnd() throws Exception {
        int block=PlaybackMixer.CHUNK_FRAMES,start=200;
        var original=pair(5*block+3,start);var p=original;String left=p.tracks().get(0).id(),right=p.tracks().get(1).id();
        var mixer=new PlaybackMixer(p,start,p.endFrame());
        assertEquals(0f,mixer.next().sample(0,0),0);
        p=set(p,left,true,false);mixer.update(p);var muted=mixer.next();
        assertEquals(0f,muted.sample(0,0),0);assertEquals(-.25f,muted.sample(0,1),0);assertEquals(start+2*block,mixer.nextFrame());
        p=set(p,left,false,true);mixer.update(p);var solo=mixer.next();
        assertEquals(2*block*.0001f,solo.sample(0,0),0);assertEquals(0f,solo.sample(0,1),0);
        p=set(p,left,true,true);mixer.update(p);var silent=mixer.next();
        assertTrue(mixer.hasNext());for(float sample:silent.samples())assertEquals(0f,sample,0);
        p=set(p,left,false,false);mixer.update(p);var resumed=mixer.next();
        assertEquals(4*block*.0001f,resumed.sample(0,0),0);assertEquals(-.25f,resumed.sample(0,1),0);
        assertEquals(3,mixer.next().frames());assertFalse(mixer.hasNext());assertEquals(original.endFrame(),mixer.nextFrame());
        assertThrows(IllegalStateException.class,mixer::next);
        assertFalse(original.track(left).mute());assertFalse(original.track(left).solo());assertFalse(original.track(right).mute());
    }
    @Test public void blockMixMatchesOfflineMixForNonzeroSelectionAndPartialLastBlock() throws Exception {
        var p=pair(4000,20);var mixer=new PlaybackMixer(p,123,2599);var expected=p.mix(123,2599,2).samples();int at=0;
        while(mixer.hasNext()){var block=mixer.next();assertTrue(block.frames()<=PlaybackMixer.CHUNK_FRAMES);for(float sample:block.samples())assertEquals(expected[at++],sample,0);}
        assertEquals(expected.length,at);assertEquals(123,mixer.startFrame());assertEquals(2599,mixer.endFrame());
    }
    @Test public void rapidUpdatesUseLatestFlagsAndMultipleSoloTracksAreAudible() throws Exception {
        var p=pair(3000,0);var left=p.tracks().get(0).id();var right=p.tracks().get(1).id();var mixer=new PlaybackMixer(p,10,2999);
        mixer.update(set(p,left,true,false));mixer.update(set(p,left,false,true));
        mixer.update(set(set(p,left,false,true),right,false,true));
        var block=mixer.next();assertEquals(.001f,block.sample(0,0),1e-7f);assertEquals(-.25f,block.sample(0,1),0);
        assertEquals(10+PlaybackMixer.CHUNK_FRAMES,mixer.nextFrame());
    }
    @Test public void highDestinationChannelsMonitorThroughStereoSidesOnEveryUpdate() throws Exception {
        var p=pair(3000,0);var l=p.tracks().get(0);var r=p.tracks().get(1);
        p=p.replace(l.settings("L",0,2,0,false,false,30)).replace(r.settings("R",0,.5,0,false,false,31));
        var mixer=new PlaybackMixer(p,10,2500);var block=mixer.next();assertEquals(.002f,block.sample(0,0),1e-7f);assertEquals(-.125f,block.sample(0,1),0);
        mixer.update(set(p,l.id(),true,false));block=mixer.next();assertEquals(0f,block.sample(0,0),0);assertEquals(-.125f,block.sample(0,1),0);
    }
    @Test public void silenceAndLongRangesStayBlockBounded() throws Exception {
        int end=AudioBuffer.MAX_SAMPLES;
        var p=new WaveProject().add("Late",new AudioBuffer(44100,1,new float[]{.5f}),end);
        var t=p.tracks().get(0);p=set(p,t.id(),true,false);var mixer=new PlaybackMixer(p,0,end+1);
        var block=mixer.next();assertEquals(PlaybackMixer.CHUNK_FRAMES,block.frames());assertEquals(PlaybackMixer.CHUNK_FRAMES,mixer.nextFrame());assertTrue(mixer.hasNext());
        for(float sample:block.samples())assertEquals(0f,sample,0);
    }
    @Test public void invalidRangesFailAndCancellationDoesNotAdvanceCursor() {
        var p=pair(2000,0);
        assertThrows(IllegalArgumentException.class,()->new PlaybackMixer(p,-1,100));
        assertThrows(IllegalArgumentException.class,()->new PlaybackMixer(p,100,100));
        var mixer=new PlaybackMixer(p,0,2000);Thread.currentThread().interrupt();
        try{assertThrows(InterruptedIOException.class,mixer::next);assertEquals(0,mixer.nextFrame());}
        finally{Thread.interrupted();}
    }
}

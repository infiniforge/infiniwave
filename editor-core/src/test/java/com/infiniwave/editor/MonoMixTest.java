package com.infiniwave.editor;

import io.github.gretchycat.infinisynth.AudioBuffer;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;

public class MonoMixTest {
    private AudioBuffer mono(float... samples) { return new AudioBuffer(44100,1,samples); }

    @Test public void chosenMonoMixFoldsRoutesPreservesOffsetsGainsAndOtherTracks() throws Exception {
        var p=new WaveProject().add("Unrelated",mono(.7f),0).add("Left",mono(.8f,.4f),10)
                .add("Middle",mono(.9f),1).add("Right",mono(.4f,-.2f),11);
        var left=p.tracks().get(1);var right=p.tracks().get(3);
        p=p.replace(left.settings(left.name(),10,.5,1,false,false,31));
        p=p.replace(right.settings(right.name(),11,2,-1,false,false,1));
        var ids=List.of(left.id(),right.id());
        var sum=p.mixToMono(ids,"Sum",false);
        assertEquals(3,sum.tracks().size());assertSame(p.tracks().get(0),sum.tracks().get(0));assertSame(p.tracks().get(2),sum.tracks().get(2));
        var result=sum.tracks().get(1);
        assertEquals(1,result.audio().channels());assertEquals(10,result.offset());assertEquals(-1,result.route());assertNull(result.profile());
        assertArrayEquals(new float[]{.4f,1f,-.4f},result.audio().samples(),1e-7f);
        assertArrayEquals(new float[]{.2f,.5f,-.2f},p.mixToMono(ids,"Average",true).tracks().get(1).audio().samples(),1e-7f);
        assertArrayEquals(new float[]{.8f,.4f},p.track(left.id()).audio().samples(),0);
    }

    @Test public void averagesByChosenCountAndUsesOnlyChosenMuteSoloFlags() throws Exception {
        var p=new WaveProject().add("A",mono(.8f),0).add("B",mono(.4f),0).add("C",mono(.6f),0).add("Unrelated solo",mono(.9f),0);
        var a=p.tracks().get(0);var b=p.tracks().get(1);var c=p.tracks().get(2);var other=p.tracks().get(3);
        p=p.replace(a.settings(a.name(),0,2,0,false,true,0)).replace(c.settings(c.name(),0,1,0,true,true,1));
        p=p.replace(other.settings(other.name(),0,1,0,false,true,-1));
        var mixed=p.mixToMono(List.of(a.id(),b.id(),c.id()),"Average",true);
        assertEquals(1.6f/3,mixed.tracks().get(0).audio().sample(0,0),1e-7f);
        assertTrue(mixed.tracks().get(0).solo());assertSame(p.track(other.id()),mixed.tracks().get(1));
        // An unrelated solo track does not suppress chosen audio while rendering.
        var unsolo=p.replace(a.settings(a.name(),0,2,0,false,false,0)).replace(c.settings(c.name(),0,1,0,true,false,1));
        assertEquals(1f,unsolo.mixToMono(List.of(a.id(),b.id()),"Average",true).tracks().get(0).audio().sample(0,0),1e-7f);
    }

    @Test public void monoMixDoesNotClipAndRoundTripsWithUndo() throws Exception {
        var p=new WaveProject().add("A",mono(1),20).add("B",mono(1),20);
        var mixed=p.mixToMono(p.tracks().stream().map(WaveProject.Track::id).toList(),"Sum",false);
        assertEquals(2f,mixed.tracks().get(0).audio().sample(0,0),0);
        var bytes=new ByteArrayOutputStream();ProjectFile.write(mixed,bytes);
        var saved=ProjectFile.read(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(1,saved.tracks().get(0).audio().channels());assertEquals(20,saved.tracks().get(0).offset());assertEquals(2f,saved.tracks().get(0).audio().sample(0,0),0);
        var history=new ProjectHistory();history.reset(p);history.apply(mixed);history.undo();assertSame(p,history.current());history.redo();assertSame(mixed,history.current());
    }

    @Test public void invalidChoicesProfilesAndMultichannelSourcesCannotReplaceTracks() {
        var p=new WaveProject().add("A",mono(.3f),0).add("Stereo",new AudioBuffer(44100,2,new float[]{.4f,.5f}),0)
                .addProfile("FM",new RenderProfile(RenderProfile.Engine.FM,Map.of("operators","2")),mono(.2f),0);
        var a=p.tracks().get(0).id();var stereo=p.tracks().get(1).id();var profile=p.tracks().get(2).id();
        for(var ids:List.of(List.<String>of(),List.of(a),List.of(a,a),List.of(a,"missing"),List.of(a,stereo),List.of(a,profile)))
            assertThrows(IllegalArgumentException.class,()->p.mixToMono(ids,"Bad",true));
        assertEquals(3,p.tracks().size());assertEquals(.3f,p.track(a).audio().sample(0,0),0);
    }

    @Test public void rejectsOversizedSpansBeforeAllocatingAndObservesCancellation() {
        var p=new WaveProject().add("Early",mono(1),0).add("Late",mono(1),AudioBuffer.MAX_SAMPLES);
        assertThrows(IllegalArgumentException.class,()->p.mixToMono(p.tracks().stream().map(WaveProject.Track::id).toList(),"Too long",true));
        var small=new WaveProject().add("A",mono(1),0).add("B",mono(1),0);
        Thread.currentThread().interrupt();
        try { assertThrows(InterruptedIOException.class,()->small.mixToMono(small.tracks().stream().map(WaveProject.Track::id).toList(),"Cancelled",true)); }
        finally { Thread.interrupted(); }
    }
}

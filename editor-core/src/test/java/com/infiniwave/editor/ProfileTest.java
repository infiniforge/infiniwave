package com.infiniwave.editor;
import io.github.gretchycat.infinisynth.AudioBuffer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProfileTest {
    private RenderProfile fm(double frequency){Map<String,String> p=new LinkedHashMap<>();p.put("operators","2");p.put("frequency",Double.toString(frequency));p.put("left","true");return new RenderProfile(RenderProfile.Engine.FM,p);}
    private AudioBuffer audio(float value){return new AudioBuffer(44100,1,new float[]{value,-value,0});}
    private byte[] json(RenderProfile p)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();ProfileFile.write(p,out);return out.toByteArray();}
    @Test public void profileJsonIsPortableVersionedAndImmutable()throws Exception{
        var p=fm(440);byte[] file=json(p);String text=new String(file,StandardCharsets.UTF_8);
        assertTrue(text.contains("\"version\": 1"));assertTrue(text.contains("\"frequency\": 440.0"));assertTrue(text.contains("\"left\": true"));
        assertEquals(p,ProfileFile.read(new ByteArrayInputStream(file)));
        assertThrows(UnsupportedOperationException.class,()->p.parameters().put("frequency","220"));
        var escaped=text.replace("frequency","frequ\\u0065ncy");assertEquals(p,ProfileFile.read(new ByteArrayInputStream(escaped.getBytes(StandardCharsets.UTF_8))));
    }
    @Test public void invalidAndUnboundedJsonCannotBecomeAProfile()throws Exception{
        String valid=new String(json(fm(440)),StandardCharsets.UTF_8);
        for(String invalid:List.of(valid.replace("\"version\": 1","\"version\": 2"),valid.replace("\"FM\"","\"UNKNOWN\""),valid.replace("440.0","1e999"),valid.replace("440.0","null"),valid.replace("440.0","[1]"),valid.replace("\"version\": 1","\"version\": 1, \"version\": 1"),valid+"garbage",valid.substring(0,valid.length()-3))){
            assertThrows(invalid,IOException.class,()->ProfileFile.read(new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8))));
        }
        assertThrows(IOException.class,()->ProfileFile.read(new ByteArrayInputStream(new byte[ProfileFile.MAX_BYTES+1])));
        assertThrows(IOException.class,()->ProfileFile.read(new ByteArrayInputStream(new byte[]{(byte)0xc0,(byte)0xaf})));
    }
    @Test public void parameterTracksPreserveSourceUntilExplicitAudioConversion()throws Exception{
        var p=new WaveProject().add("Wave",audio(.1f),0).addProfile("FM",fm(440),audio(.2f),0);String id=p.tracks().get(1).id();
        assertThrows(IllegalArgumentException.class,()->p.edit(id,0,3,WaveProject.Edit.REVERSE));
        assertThrows(IllegalArgumentException.class,()->p.paste(id,0,audio(.5f)));
        assertThrows(IllegalArgumentException.class,()->p.copyRange(id,0,3));
        var h=new ProjectHistory();h.reset(p);var edited=p.replace(p.track(id).withProfile(fm(220),audio(.3f)));h.apply(edited);
        assertEquals("220.0",h.current().track(id).profile().parameters().get("frequency"));h.undo();assertEquals(fm(440),h.current().track(id).profile());h.redo();
        assertTrue(edited.mix(0,3,2).sample(0,0)>.2);
        var converted=edited.renderToAudio(id);assertNull(converted.track(id).profile());assertSame(edited.track(id).audio(),converted.track(id).audio());assertNotNull(converted.edit(id,0,3,WaveProject.Edit.REVERSE));
        assertEquals(fm(220),edited.track(id).profile());assertEquals(fm(440),p.duplicate(id).tracks().get(2).profile());
    }
    @Test public void projectRetainsAllProfileKindsAndRenderCaches()throws Exception{
        var p=new WaveProject();
        for(var engine:RenderProfile.Engine.values()){
            Map<String,String> params=new LinkedHashMap<>();params.put("duration","1");if(engine==RenderProfile.Engine.FM||engine==RenderProfile.Engine.OPL3)params.put("operators","2");
            p=p.addProfile(engine.name(),new RenderProfile(engine,params),audio(.4f),44);
        }
        ByteArrayOutputStream out=new ByteArrayOutputStream();ProjectFile.write(p,out);var read=ProjectFile.read(new ByteArrayInputStream(out.toByteArray()));
        for(int i=0;i<4;i++){assertEquals(p.tracks().get(i).profile(),read.tracks().get(i).profile());assertArrayEquals(p.tracks().get(i).audio().samples(),read.tracks().get(i).audio().samples(),0);}
    }
}

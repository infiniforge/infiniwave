package com.infiniwave.editor;
import io.github.gretchycat.infinisynth.AudioBuffer;
import java.io.*;
import java.util.*;

/** App-owned immutable multitrack document. Tracks and channel routes are independent. */
public final class WaveProject {
    public static final int RATE=44100;
    public static final long MAX_PROJECT_SAMPLES=32_000_000L;
    public record Track(String id,String name,AudioBuffer audio,int offset,double gain,double pan,boolean mute,boolean solo,int route,RenderProfile profile){
        public Track{
            if(id==null||name==null||audio==null||audio.sampleRate()!=RATE||offset<0||offset>RATE*3600||!Double.isFinite(gain)||gain<0||gain>8||!Double.isFinite(pan)||Math.abs(pan)>1||route< -1||route>31)throw new IllegalArgumentException("Invalid track settings");
        }
        public Track(String id,String name,AudioBuffer audio,int offset,double gain,double pan,boolean mute,boolean solo,int route){this(id,name,audio,offset,gain,pan,mute,solo,route,null);}
        public Track withProfile(RenderProfile profile,AudioBuffer rendered){return new Track(id,name,rendered,offset,gain,pan,mute,solo,route,profile);}
        public Track withAudio(AudioBuffer data,int start){return new Track(id,name,data,start,gain,pan,mute,solo,route,profile);}
        public Track settings(String name,int offset,double gain,double pan,boolean mute,boolean solo,int route){return new Track(id,name,audio,offset,gain,pan,mute,solo,route,profile);}
    }
    private final List<Track> tracks;
    public WaveProject(){this(Collections.emptyList());}
    public WaveProject(List<Track> tracks){
        this.tracks=Collections.unmodifiableList(new ArrayList<>(tracks));Set<String> ids=new HashSet<>();Set<AudioBuffer> buffers=Collections.newSetFromMap(new IdentityHashMap<>());long samples=0;
        for(Track t:tracks){if(!ids.add(t.id))throw new IllegalArgumentException("Duplicate track identity");if(buffers.add(t.audio))samples+=(long)t.audio.frames()*t.audio.channels();}
        if(samples>MAX_PROJECT_SAMPLES)throw new IllegalArgumentException("Project PCM budget exceeded (128 MB); export or remove audio to continue");
    }
    public List<Track> tracks(){return tracks;}
    public int endFrame(){int end=0;for(Track t:tracks)end=Math.max(end,t.offset+t.audio.frames());return end;}
    public WaveProject add(String name,AudioBuffer audio,int offset){List<Track> list=new ArrayList<>(tracks);list.add(new Track(UUID.randomUUID().toString(),name,audio,offset,1,0,false,false,-1));return new WaveProject(list);}
    public WaveProject addProfile(String name,RenderProfile profile,AudioBuffer rendered,int offset){WaveProject p=add(name,rendered,offset);Track t=p.tracks.get(p.tracks.size()-1);return p.replace(t.withProfile(profile,rendered));}
    public WaveProject renderToAudio(String id){Track t=track(id);return replace(t.withProfile(null,t.audio));}
    private static void requireAudio(Track t){if(t.profile!=null)throw new IllegalArgumentException("Edit this track's parameters, or render it to an audio waveform first");}
    public WaveProject replace(Track track){List<Track> list=new ArrayList<>(tracks);int i=index(track.id);list.set(i,track);return new WaveProject(list);}
    public AudioBuffer copyRange(String id,int from,int to){
        Track t=track(id);requireAudio(t);int a=Math.max(0,Math.min(t.audio.frames(),from-t.offset)),b=Math.max(0,Math.min(t.audio.frames(),to-t.offset));
        if(b<=a)throw new IllegalArgumentException("Select a time range overlapping the track");
        return new AudioBuffer(RATE,t.audio.channels(),Arrays.copyOfRange(t.audio.samples(),a*t.audio.channels(),b*t.audio.channels()));
    }
    public WaveProject paste(String id,int frame,AudioBuffer clip){
        Track t=track(id);requireAudio(t);if(clip.channels()!=t.audio.channels())throw new IllegalArgumentException("Clipboard channels differ; paste as a new track or split/combine channels first");
        if(frame<t.offset)throw new IllegalArgumentException("Place the cursor within or after the track");
        int at=frame-t.offset,ch=t.audio.channels();long length=(long)Math.max(at,t.audio.frames())+clip.frames();
        if(length*ch>AudioBuffer.MAX_SAMPLES)throw new IllegalArgumentException("Pasted audio exceeds clip budget");
        float[] out=new float[(int)length*ch],original=t.audio.samples(),insert=clip.samples();
        int before=Math.min(at,t.audio.frames())*ch;System.arraycopy(original,0,out,0,before);System.arraycopy(insert,0,out,at*ch,insert.length);
        if(at<t.audio.frames())System.arraycopy(original,at*ch,out,(at+clip.frames())*ch,original.length-at*ch);
        return replace(t.withAudio(new AudioBuffer(RATE,ch,out),t.offset));
    }
    public WaveProject duplicate(String id){Track t=track(id);List<Track> list=new ArrayList<>(tracks);list.add(new Track(UUID.randomUUID().toString(),t.name+" copy",t.audio,t.offset,t.gain,t.pan,t.mute,t.solo,t.route,t.profile));return new WaveProject(list);}
    public WaveProject remove(String id){List<Track> list=new ArrayList<>(tracks);list.remove(index(id));return new WaveProject(list);}
    public Track track(String id){return tracks.get(index(id));}
    private int index(String id){for(int i=0;i<tracks.size();i++)if(tracks.get(i).id.equals(id))return i;throw new IllegalArgumentException("Track no longer exists");}
    public enum Edit { DELETE, TRIM, SILENCE, REVERSE, NORMALIZE, FADE_IN, FADE_OUT }
    public WaveProject edit(String id,int from,int to,Edit operation) throws InterruptedIOException {
        Track t=track(id);requireAudio(t);int a=Math.max(0,Math.min(t.audio.frames(),from-t.offset)),b=Math.max(0,Math.min(t.audio.frames(),to-t.offset));
        if(b<=a)throw new IllegalArgumentException("Select a time range overlapping the track");
        int channels=t.audio.channels();float[] input=t.audio.samples(),output;int offset=t.offset;
        if(operation==Edit.DELETE){output=new float[input.length-(b-a)*channels];System.arraycopy(input,0,output,0,a*channels);System.arraycopy(input,b*channels,output,a*channels,input.length-b*channels);}
        else if(operation==Edit.TRIM){output=Arrays.copyOfRange(input,a*channels,b*channels);offset+=a;}
        else{
            output=input;double peak=0;
            if(operation==Edit.NORMALIZE)for(int i=a*channels;i<b*channels;i++)peak=Math.max(peak,Math.abs(input[i]));
            for(int f=a;f<b;f++){
                if((f&1023)==0&&Thread.currentThread().isInterrupted())throw new InterruptedIOException("Edit cancelled");
                for(int ch=0;ch<channels;ch++){
                    int p=f*channels+ch;
                    switch(operation){
                        case SILENCE->output[p]=0;
                        case NORMALIZE->{if(peak>0)output[p]=(float)(output[p]*.99/peak);}
                        case FADE_IN->output[p]*=(float)(f-a)/Math.max(1,b-a-1);
                        case FADE_OUT->output[p]*=(float)(b-f-1)/Math.max(1,b-a-1);
                        case REVERSE->{if(f<a+(b-a)/2){int q=(b-1-(f-a))*channels+ch;float v=output[p];output[p]=output[q];output[q]=v;}}
                        default->throw new IllegalArgumentException("Invalid edit");
                    }
                }
            }
        }
        return replace(t.withAudio(new AudioBuffer(RATE,channels,output),offset));
    }
    public WaveProject splitChannels(String id){
        Track t=track(id);requireAudio(t);if(t.audio.channels()<2)throw new IllegalArgumentException("This track is already mono");List<Track> list=new ArrayList<>(tracks);int at=index(id);list.remove(at);
        for(int ch=0;ch<t.audio.channels();ch++)list.add(at+ch,new Track(UUID.randomUUID().toString(),t.name+" · channel "+(ch+1),t.audio.channel(ch),t.offset,t.gain,0,t.mute,t.solo,ch));
        return new WaveProject(list);
    }
    /** Render only requested frames; individual routes select a destination channel. */
    public AudioBuffer mix(int from,int to,int channels) throws InterruptedIOException {
        if(from<0||to<=from||channels<1||channels>32||(long)(to-from)*channels>AudioBuffer.MAX_SAMPLES)throw new IllegalArgumentException("Mix is empty or exceeds the render budget; select a shorter range");
        float[] output=new float[(to-from)*channels];boolean solo=tracks.stream().anyMatch(Track::solo);
        for(Track t:tracks){
            if(t.mute||solo&&!t.solo)continue;
            if(t.route>=channels)throw new IllegalArgumentException(t.name+" routes to channel "+(t.route+1)+"; choose more output channels");
            int a=Math.max(from,t.offset),b=Math.min(to,t.offset+t.audio.frames());
            for(int f=a;f<b;f++){
                if((f&1023)==0&&Thread.currentThread().isInterrupted())throw new InterruptedIOException("Mix cancelled");
                int sf=f-t.offset,dst=(f-from)*channels;
                if(t.route>=0){double mono=0;for(int c=0;c<t.audio.channels();c++)mono+=t.audio.sample(sf,c)/t.audio.channels();output[dst+t.route]+=(float)(mono*t.gain);}
                else if(channels==1){for(int c=0;c<t.audio.channels();c++)output[dst]+=(float)(t.audio.sample(sf,c)*t.gain/t.audio.channels());}
                else if(t.audio.channels()==1){output[dst]+=(float)(t.audio.sample(sf,0)*t.gain*Math.sqrt((1-t.pan)/2));output[dst+1]+=(float)(t.audio.sample(sf,0)*t.gain*Math.sqrt((1+t.pan)/2));}
                else{for(int c=0;c<Math.min(channels,t.audio.channels());c++)output[dst+c]+=(float)(t.audio.sample(sf,c)*t.gain*(c==0?Math.min(1,1-t.pan):c==1?Math.min(1,1+t.pan):1));}
            }
        }
        return new AudioBuffer(RATE,channels,output);
    }
    public WaveProject combineAsChannels() {
        if(tracks.isEmpty()||tracks.size()>32)throw new IllegalArgumentException("Choose 1..32 mono tracks for a multichannel clip; the workspace itself has no track-count limit");
        if((long)endFrame()*tracks.size()>AudioBuffer.MAX_SAMPLES)throw new IllegalArgumentException("Combined clip exceeds PCM budget");
        int n=tracks.size();float[] output=new float[endFrame()*n];
        for(int i=0;i<n;i++){Track t=tracks.get(i);if(t.audio.channels()!=1)throw new IllegalArgumentException("Split tracks to mono before combining channels");for(int f=0;f<t.audio.frames();f++)output[(f+t.offset)*n+i]=(float)(t.audio.sample(f,0)*t.gain);}
        return new WaveProject().add("Combined channels",new AudioBuffer(RATE,n,output),0);
    }
}

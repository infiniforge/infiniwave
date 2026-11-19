package com.infiniwave.editor;
import io.github.gretchycat.infinisynth.AudioBuffer;
import java.io.*;
import java.util.*;

/** Versioned project snapshots; caller owns streams, input is bounded and original audio is untouched. */
public final class ProjectFile {
    private static final int MAGIC=0x49575631;
    public static void write(WaveProject p,OutputStream stream)throws IOException{
        DataOutputStream out=new DataOutputStream(stream);out.writeInt(MAGIC);out.writeInt(2);
        Map<AudioBuffer,Integer> ids=new IdentityHashMap<>();List<AudioBuffer> buffers=new ArrayList<>();
        for(var t:p.tracks())if(!ids.containsKey(t.audio())){ids.put(t.audio(),buffers.size());buffers.add(t.audio());}
        out.writeInt(buffers.size());
        for(var b:buffers){out.writeInt(b.channels());out.writeInt(b.frames());float[] data=b.samples();for(int i=0;i<data.length;i++){if((i&8191)==0)checkCancel();out.writeFloat(data[i]);}}
        out.writeInt(p.tracks().size());
        for(var t:p.tracks()){out.writeUTF(t.id());out.writeUTF(t.name());out.writeInt(ids.get(t.audio()));out.writeInt(t.offset());out.writeDouble(t.gain());out.writeDouble(t.pan());out.writeBoolean(t.mute());out.writeBoolean(t.solo());out.writeInt(t.route());out.writeBoolean(t.profile()!=null);if(t.profile()!=null){ByteArrayOutputStream json=new ByteArrayOutputStream();ProfileFile.write(t.profile(),json);byte[] data=json.toByteArray();out.writeInt(data.length);out.write(data);}}out.flush();
    }
    public static WaveProject read(InputStream stream)throws IOException{
        DataInputStream in=new DataInputStream(stream);if(in.readInt()!=MAGIC)throw new IOException("Unsupported InfiniWave project");int version=in.readInt();if(version<1||version>2)throw new IOException("Unsupported InfiniWave project version");
        int n=in.readInt();if(n<0||n>1_000_000)throw new IOException("Invalid project buffer count");List<AudioBuffer> buffers=new ArrayList<>();long total=0;
        try{
            for(int i=0;i<n;i++){int channels=in.readInt(),frames=in.readInt();long size=(long)channels*frames;
                if(channels<1||channels>32||frames<0||size>AudioBuffer.MAX_SAMPLES||(total+=size)>WaveProject.MAX_PROJECT_SAMPLES)throw new IOException("Project PCM budget exceeded");
                float[] data=new float[(int)size];for(int j=0;j<data.length;j++){if((j&8191)==0)checkCancel();data[j]=in.readFloat();}buffers.add(new AudioBuffer(WaveProject.RATE,channels,data));}
            int count=in.readInt();if(count<0||count>1_000_000)throw new IOException("Project metadata budget exceeded");List<WaveProject.Track> tracks=new ArrayList<>();
            for(int i=0;i<count;i++){String id=in.readUTF(),name=in.readUTF();int b=in.readInt();if(b<0||b>=buffers.size())throw new IOException("Invalid audio reference");WaveProject.Track t=new WaveProject.Track(id,name,buffers.get(b),in.readInt(),in.readDouble(),in.readDouble(),in.readBoolean(),in.readBoolean(),in.readInt());if(version>=2&&in.readBoolean()){int size=in.readInt();if(size<1||size>ProfileFile.MAX_BYTES)throw new IOException("Invalid embedded profile size");byte[] json=new byte[size];in.readFully(json);t=t.withProfile(ProfileFile.read(new ByteArrayInputStream(json)),t.audio());}tracks.add(t);}
            if(in.read()!=-1)throw new IOException("Trailing project data");return new WaveProject(tracks);
        }catch(IllegalArgumentException e){throw new IOException("Invalid project: "+e.getMessage(),e);}
    }
    private static void checkCancel()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Project operation cancelled");}
}

package com.infiniwave.editor;

import io.github.gretchycat.infinisynth.AudioBuffer;
import java.io.InterruptedIOException;
import java.util.Objects;

/** One playback-thread cursor with atomically replaceable immutable mixer settings. */
public final class PlaybackMixer {
    public static final int CHUNK_FRAMES=1024;
    private final int startFrame,endFrame;
    private int nextFrame;
    private volatile WaveProject project;

    public PlaybackMixer(WaveProject project,int startFrame,int endFrame) {
        if(startFrame<0||endFrame<=startFrame)throw new IllegalArgumentException("Select a nonempty playback range");
        this.startFrame=startFrame;this.endFrame=endFrame;nextFrame=startFrame;
        update(project);
    }
    /** The UI publishes a complete snapshot; the audio thread takes one snapshot per block. */
    public void update(WaveProject source) {
        Objects.requireNonNull(source);
        project=new WaveProject(source.tracks().stream().map(t->t.route()>1?
                t.settings(t.name(),t.offset(),t.gain(),t.pan(),t.mute(),t.solo(),t.route()%2):t).toList());
    }
    public int startFrame(){return startFrame;}
    public int endFrame(){return endFrame;}
    public int nextFrame(){return nextFrame;}
    public boolean hasNext(){return nextFrame<endFrame;}
    /** Called by the playback thread only. Muting everything renders silence, not end-of-stream. */
    public AudioBuffer next() throws InterruptedIOException {
        if(!hasNext())throw new IllegalStateException("Playback range finished");
        int end=nextFrame+Math.min(CHUNK_FRAMES,endFrame-nextFrame);
        AudioBuffer audio=project.mix(nextFrame,end,2);
        nextFrame=end;
        return audio;
    }
}

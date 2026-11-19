package com.infiniwave.editor;
import io.github.gretchycat.infinisynth.AudioBuffer;
import java.util.*;

/** History shares immutable PCM and evicts oldest undo states by memory rather than track count. */
public final class ProjectHistory {
    private WaveProject current=new WaveProject();private final List<WaveProject> undo=new ArrayList<>(),redo=new ArrayList<>();
    public synchronized WaveProject current(){return current;}
    public synchronized void reset(WaveProject project){current=project;undo.clear();redo.clear();}
    public synchronized void apply(WaveProject project){if(project==current)return;undo.add(current);current=project;redo.clear();while(undo.size()>30||retainedSamples()>64_000_000L)undo.remove(0);}
    private long retainedSamples(){Set<AudioBuffer> seen=Collections.newSetFromMap(new IdentityHashMap<>());long size=0;List<WaveProject> all=new ArrayList<>(undo);all.add(current);for(WaveProject p:all)for(var t:p.tracks())if(seen.add(t.audio()))size+=(long)t.audio().frames()*t.audio().channels();return size;}
    public synchronized boolean canUndo(){return !undo.isEmpty();}
    public synchronized boolean canRedo(){return !redo.isEmpty();}
    public synchronized void undo(){if(canUndo()){redo.add(current);current=undo.remove(undo.size()-1);}}
    public synchronized void redo(){if(canRedo()){undo.add(current);current=redo.remove(redo.size()-1);}}
}

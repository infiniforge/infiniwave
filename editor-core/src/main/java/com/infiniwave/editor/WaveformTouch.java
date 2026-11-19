package com.infiniwave.editor;

/** A drag previews selection; only finger-up commits it. Any multitouch cancels the pending selection. */
public final class WaveformTouch {
    public record Range(double from,double to) {}
    private double startX,anchor;
    private boolean active,multi,dragging;
    private Range preview;
    public void down(double x,double time){startX=x;anchor=time;active=true;multi=false;dragging=false;preview=null;}
    public void move(double x,double time,double slop){
        if(!active||multi)return;
        if(Math.abs(x-startX)>slop)dragging=true;
        if(dragging)preview=new Range(Math.min(anchor,time),Math.max(anchor,time));
    }
    public void beginMultitouch(){multi=true;preview=null;dragging=false;}
    public Range up(double time){Range result=active&&!multi?(preview!=null?preview:new Range(time,time)):null;cancel();return result;}
    public Range preview(){return preview;}
    public boolean multitouch(){return multi;}
    public void cancel(){active=false;multi=false;dragging=false;preview=null;}
}

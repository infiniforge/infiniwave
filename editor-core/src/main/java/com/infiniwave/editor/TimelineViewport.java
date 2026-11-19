package com.infiniwave.editor;

/** Time geometry shared by buttons and touch zoom; the sound under the pinch stays under the fingers. */
public record TimelineViewport(double start,double seconds) {
    public static final double MIN_SECONDS=.02,MAX_SECONDS=7200;
    public TimelineViewport {
        if(!Double.isFinite(start)||!Double.isFinite(seconds)||start<0||seconds<MIN_SECONDS||seconds>MAX_SECONDS||start+seconds>MAX_SECONDS+.000001)throw new IllegalArgumentException("Invalid timeline view");
    }
    public static TimelineViewport fit(double end){return new TimelineViewport(0,Math.min(MAX_SECONDS,Math.max(5,end*1.03)));}
    public TimelineViewport zoom(double factor,double previousFocus,double newFocus){
        if(!Double.isFinite(factor)||factor<=0||!Double.isFinite(previousFocus)||!Double.isFinite(newFocus))throw new IllegalArgumentException("Invalid zoom gesture");
        double span=Math.max(MIN_SECONDS,Math.min(MAX_SECONDS,seconds/factor));
        double anchor=start+Math.max(0,Math.min(1,previousFocus))*seconds;
        double first=Math.max(0,Math.min(MAX_SECONDS-span,anchor-Math.max(0,Math.min(1,newFocus))*span));
        return new TimelineViewport(first,span);
    }
    public TimelineViewport pan(double delta){if(!Double.isFinite(delta))throw new IllegalArgumentException("Invalid pan");return new TimelineViewport(Math.max(0,Math.min(MAX_SECONDS-seconds,start+delta)),seconds);}
}

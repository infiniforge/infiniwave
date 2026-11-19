package com.infiniwave.editor;
import org.junit.Test;
import static org.junit.Assert.*;

public class WaveformTouchTest {
    @Test public void dragCommitsOnlyOnReleaseAndSortsTimeBounds(){
        var touch=new WaveformTouch();touch.down(100,3);touch.move(50,1,8);assertEquals(new WaveformTouch.Range(1,3),touch.preview());
        assertEquals(new WaveformTouch.Range(1,3),touch.up(1));assertNull(touch.preview());
    }
    @Test public void pinchCancelsPendingSelectionAndRemainingFingerCannotSelect(){
        var touch=new WaveformTouch();touch.down(0,2);touch.move(20,3,8);assertNotNull(touch.preview());
        touch.beginMultitouch();assertNull(touch.preview());touch.move(100,10,8);assertNull(touch.preview());assertNull(touch.up(12));
        touch.down(30,4);assertEquals(new WaveformTouch.Range(4,4),touch.up(4));
    }
    @Test public void tinyMovementPlacesCursorAndCancelNeverEditsSelection(){
        var touch=new WaveformTouch();touch.down(30,1);touch.move(33,1.1,8);assertNull(touch.preview());assertEquals(new WaveformTouch.Range(1.1,1.1),touch.up(1.1));
        touch.down(0,1);touch.move(100,2,8);touch.cancel();assertNull(touch.up(2));
    }
    @Test public void zoomAnchorsTimeUnderFingersAndSupportsTwoFingerPanning(){
        var before=new TimelineViewport(10,8);var zoom=before.zoom(2,.25,.25);
        assertEquals(4,zoom.seconds(),0);assertEquals(12,zoom.start()+zoom.seconds()*.25,1e-12);
        var pan=zoom.zoom(1,.25,.5);assertEquals(12,pan.start()+pan.seconds()*.5,1e-12);assertEquals(4,pan.seconds(),0);
    }
    @Test public void viewportBoundsStayFiniteAtBothZoomLimits(){
        var view=new TimelineViewport(0,5).zoom(1e20,0,1);assertEquals(TimelineViewport.MIN_SECONDS,view.seconds(),0);assertEquals(0,view.start(),0);
        view=view.zoom(1e-20,.5,.5);assertEquals(TimelineViewport.MAX_SECONDS,view.seconds(),0);assertEquals(0,view.start(),0);
        assertEquals(0,view.pan(-10000).start(),0);assertThrows(IllegalArgumentException.class,()->new TimelineViewport(0,5).zoom(Double.NaN,.5,.5));
    }
}

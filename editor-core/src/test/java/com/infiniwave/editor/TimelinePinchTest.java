package com.infiniwave.editor;

import org.junit.Test;
import static org.junit.Assert.*;

public class TimelinePinchTest {
    @Test public void spreadingZoomsInAndPinchingZoomsOutIncrementally() {
        var pinch = new TimelinePinch(8);
        pinch.begin(100, 50, 300, 50);
        var delta = pinch.move(0, 50, 400, 50);
        assertEquals(2, delta.factor(), 1e-12);
        assertEquals(200, delta.focusX(), 0);
        assertEquals(.5, pinch.move(100, 50, 300, 50).factor(), 1e-12);
    }

    @Test public void fingersOnDifferentTracksAndVerticalPinchesBothZoom() {
        var pinch = new TimelinePinch(8);
        pinch.begin(100, 100, 300, 300);
        assertEquals(2, pinch.move(0, 0, 400, 400).factor(), 1e-12);
        pinch.begin(200, 100, 200, 300);
        assertEquals(2, pinch.move(200, 0, 200, 400).factor(), 1e-12);
    }

    @Test public void movingFocusKeepsSameTimeUnderFingersAcrossTracks() {
        var pinch = new TimelinePinch(8);
        pinch.begin(110, 100, 310, 300);
        var delta = pinch.move(60, 0, 460, 400);
        // Match the view's 10px content inset and 1000px waveform width.
        double beforeFocus = (delta.previousFocusX() - 10) / 1000;
        double afterFocus = (delta.focusX() - 10) / 1000;
        var before = new TimelineViewport(10, 8);
        var after = before.zoom(delta.factor(), beforeFocus, afterFocus);
        assertEquals(4, after.seconds(), 1e-12);
        assertEquals(before.start() + beforeFocus * before.seconds(), after.start() + afterFocus * after.seconds(), 1e-12);
    }

    @Test public void twoFingerTranslationPansWithoutChangingZoom() {
        var pinch = new TimelinePinch(8);
        pinch.begin(100, 100, 300, 200);
        var delta = pinch.move(150, 120, 350, 220);
        assertEquals(1, delta.factor(), 1e-12);
        var view = new TimelineViewport(10, 8).zoom(delta.factor(), delta.previousFocusX()/1000, delta.focusX()/1000);
        assertEquals(9.6, view.start(), 1e-12);
        assertEquals(8, view.seconds(), 0);
    }

    @Test public void replacingPointerPairRebasesWithoutJumping() {
        var pinch = new TimelinePinch(8);
        pinch.begin(0, 0, 100, 0);
        pinch.move(0, 0, 200, 0);
        pinch.begin(200, 0, 500, 400);
        var delta = pinch.move(500, 400, 200, 0);
        assertEquals(1, delta.factor(), 1e-12);
        assertEquals(delta.previousFocusX(), delta.focusX(), 0);
    }

    @Test public void closeOrInvalidPointersAndNewGesturesDoNotCauseJumps() {
        var pinch = new TimelinePinch(8);
        pinch.begin(0, 0, 1, 0);
        assertNull(pinch.move(0, 0, 100, 0));
        assertEquals(2, pinch.move(0, 0, 200, 0).factor(), 1e-12);
        assertNull(pinch.move(0, 0, Double.NaN, 0));
        assertNull(pinch.move(0, 0, 100, 0));
        assertNull(pinch.move(0, 0, 0, 0));
        assertNull(pinch.move(0, 0, 100, 0));
        pinch.reset();
        assertNull(pinch.move(0, 0, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> new TimelinePinch(0));
    }

    @Test public void smallFingerMovementRespondsWithoutPlatformScaleThreshold() {
        var pinch = new TimelinePinch(8);
        pinch.begin(100, 100, 300, 100);
        assertEquals(1.01, pinch.move(99, 100, 301, 100).factor(), 1e-12);
    }
}

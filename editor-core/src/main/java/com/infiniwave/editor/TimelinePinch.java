package com.infiniwave.editor;

/** Incremental two-pointer geometry; coordinates may belong to different waveform lanes. */
public final class TimelinePinch {
    public record Delta(double factor, double previousFocusX, double focusX) {}

    private final double minimumSpan;
    private double span = Double.NaN, focusX;

    public TimelinePinch(double minimumSpan) {
        if (!Double.isFinite(minimumSpan) || minimumSpan <= 0) throw new IllegalArgumentException("Invalid minimum span");
        this.minimumSpan = minimumSpan;
    }

    /** Rebase when pointers are added/removed, avoiding a jump between pointer pairs. */
    public void begin(double x1, double y1, double x2, double y2) {
        reset();
        move(x1, y1, x2, y2);
    }

    public Delta move(double x1, double y1, double x2, double y2) {
        double nextSpan = Math.hypot(x2 - x1, y2 - y1);
        double nextFocusX = x1 / 2 + x2 / 2;
        if (!Double.isFinite(nextSpan) || !Double.isFinite(nextFocusX) || nextSpan < minimumSpan) {
            reset();
            return null;
        }
        Delta delta = Double.isNaN(span) ? null : new Delta(nextSpan / span, focusX, nextFocusX);
        span = nextSpan;
        focusX = nextFocusX;
        return delta;
    }

    public void reset() { span = Double.NaN; }
}

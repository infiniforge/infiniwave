package com.infiniwave

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.infiniwave.editor.TimelineViewport
import com.infiniwave.editor.WaveformTouch
import com.infiniwave.editor.WaveProject
import io.github.gretchycat.infinisynth.AudioBuffer
import kotlin.math.*

class WaveformView(context: Context) : View(context) {
    data class Peaks(val low: Array<FloatArray>, val high: Array<FloatArray>, val frames: Int) {
        companion object {
            fun of(audio: AudioBuffer): Peaks {
                val bins = min(4096, max(1,audio.frames()))
                val low = Array(audio.channels()) { FloatArray(bins) { Float.POSITIVE_INFINITY } }
                val high = Array(audio.channels()) { FloatArray(bins) { Float.NEGATIVE_INFINITY } }
                repeat(audio.frames()) { f ->
                    if(f and 8191 == 0 && Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("Waveform preparation cancelled")
                    val bin = min((bins-1).toLong(), f.toLong()*bins/max(1,audio.frames())).toInt()
                    repeat(audio.channels()) { ch -> val v=audio.sample(f,ch);low[ch][bin]=min(low[ch][bin],v);high[ch][bin]=max(high[ch][bin],v) }
                }
                return Peaks(low,high,audio.frames())
            }
        }
    }
    lateinit var track: WaveProject.Track
    var peaks: Peaks?=null
    var viewFrom=0.0
    var viewSeconds=5.0
    var selectionFrom=0.0
    var selectionTo=0.0
    var playSeconds: Double?=null
    var trackSelected=false
    var onSelection: ((Double,Double)->Unit)?=null
    var onZoom: ((Double,Double)->Unit)?=null
    var panMode=false
    var interactionEnabled=true
    private val touch=WaveformTouch()
    private val slop=ViewConfiguration.get(context).scaledTouchSlop.toDouble()
    private var panX=0f
    private var previousFocus=.5
    private fun viewport()=TimelineViewport(viewFrom,viewSeconds)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{strokeCap=Paint.Cap.ROUND}
    private val scale=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScaleBegin(detector:ScaleGestureDetector):Boolean {
            if(!interactionEnabled)return false
            touch.beginMultitouch();previousFocus=(detector.focusX/max(1,width)).toDouble();invalidate();return true
        }
        override fun onScale(detector:ScaleGestureDetector):Boolean {
            if(!interactionEnabled)return false
            val focus=(detector.focusX/max(1,width)).toDouble()
            val factor=detector.scaleFactor.toDouble();if(!factor.isFinite()||factor<=0)return false
            val next=viewport().zoom(factor,previousFocus,focus)
            previousFocus=focus;onZoom?.invoke(next.start(),next.seconds());return true
        }
    }).apply{isQuickScaleEnabled=false}
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas);canvas.drawColor(if(trackSelected)0xff182d3b.toInt() else 0xff14222d.toInt())
        val pending=touch.preview();val a=pending?.from()?:selectionFrom;val b=pending?.to()?:selectionTo
        val x0=((a-viewFrom)/viewSeconds*width).toFloat();val x1=((b-viewFrom)/viewSeconds*width).toFloat()
        paint.color=0x553babb2;canvas.drawRect(x0,0f,x1,height.toFloat(),paint)
        if(trackSelected&&b>a){paint.color=0xffffc777.toInt();paint.strokeWidth=2*resources.displayMetrics.density;canvas.drawLine(x0,0f,x0,height.toFloat(),paint);canvas.drawLine(x1,0f,x1,height.toFloat(),paint);paint.strokeWidth=1f}
        paint.textSize=12*resources.displayMetrics.scaledDensity
        for(i in 0..5){val x=width*i/5f;paint.color=0xff314956.toInt();canvas.drawLine(x,0f,x,height.toFloat(),paint);paint.color=0xff96abb7.toInt();canvas.drawText("%.2fs".format(viewFrom+viewSeconds*i/5),x+4,paint.textSize+3,paint)}
        val p=peaks?:return
        if(p.frames==0)return
        // At close zoom, draw actual samples instead of magnifying coarse peak bins.
        // Cap scanning so a long/multichannel clip never triggers an unbounded UI pass.
        val detail=viewSeconds*WaveProject.RATE*p.low.size<=120000
        val samplesPerPixel=viewSeconds*WaveProject.RATE/max(1,width)
        for(ch in p.low.indices){
            val lane=(height-24f)/p.low.size;val center=24+lane*(ch+.5f)
            paint.color=0xff375462.toInt();canvas.drawLine(0f,center,width.toFloat(),center,paint)
            paint.color=if(track.mute())0xff657b85.toInt() else 0xff58d5ca.toInt()
            var previousX:Float?=null;var previousY=0f
            for(x in 0 until width){
                val first=((viewFrom+viewSeconds*x/max(1,width))*WaveProject.RATE-track.offset()).toInt()
                val last=((viewFrom+viewSeconds*(x+1)/max(1,width))*WaveProject.RATE-track.offset()).toInt()
                if(last<0||first>=p.frames){previousX=null;continue}
                if(detail&&samplesPerPixel<=1){
                    val position=((viewFrom+viewSeconds*(x+.5)/max(1,width))*WaveProject.RATE-track.offset()).coerceIn(0.0,(p.frames-1).toDouble())
                    val a=position.toInt();val b=min(p.frames-1,a+1);val value=track.audio().sample(a,ch)*(1-(position-a))+track.audio().sample(b,ch)*(position-a)
                    val y=center-value.toFloat().coerceIn(-1f,1f)*lane*.42f
                    previousX?.let{canvas.drawLine(it,previousY,x.toFloat(),y,paint)};previousX=x.toFloat();previousY=y;continue
                }
                var low=Float.POSITIVE_INFINITY;var high=Float.NEGATIVE_INFINITY
                if(detail){
                    val a=first.coerceIn(0,p.frames-1);val b=last.coerceIn(a,p.frames-1)
                    for(frame in a..b){val v=track.audio().sample(frame,ch);low=min(low,v);high=max(high,v)}
                }else{
                    val a=(max(0,first).toLong()*p.low[ch].size/max(1,p.frames)).toInt().coerceIn(p.low[ch].indices)
                    val b=(max(0,last).toLong()*p.low[ch].size/max(1,p.frames)).toInt().coerceIn(p.low[ch].indices)
                    for(bin in a..b){low=min(low,p.low[ch][bin]);high=max(high,p.high[ch][bin])}
                }
                canvas.drawLine(x.toFloat(),center-high.coerceIn(-1f,1f)*lane*.42f,x.toFloat(),center-low.coerceIn(-1f,1f)*lane*.42f,paint)
            }
            paint.color=0xffa4c1cb.toInt();canvas.drawText(if(p.low.size==2)if(ch==0)"L · Left" else "R · Right" else "CH ${ch+1}",8f,center-lane*.3f,paint)
        }
        playSeconds?.let {val x=((it-viewFrom)/viewSeconds*width).toFloat();paint.color=0xffffc777.toInt();canvas.drawLine(x,0f,x,height.toFloat(),paint)}
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(!interactionEnabled){touch.cancel();parent.requestDisallowInterceptTouchEvent(false);return false}
        if(event.actionMasked==MotionEvent.ACTION_DOWN){
            parent.requestDisallowInterceptTouchEvent(true);panX=event.x
            val time=(viewFrom+event.x.coerceIn(0f,width.toFloat())/max(1,width)*viewSeconds).coerceAtLeast(0.0)
            touch.down(event.x.toDouble(),time)
        }
        if(event.actionMasked==MotionEvent.ACTION_POINTER_DOWN)touch.beginMultitouch()
        scale.onTouchEvent(event)
        val time=(viewFrom+event.x.coerceIn(0f,width.toFloat())/max(1,width)*viewSeconds).coerceAtLeast(0.0)
        when(event.actionMasked){
            MotionEvent.ACTION_MOVE->if(!touch.multitouch()&&!scale.isInProgress){
                if(panMode){val next=viewport().pan((panX-event.x)/max(1,width)*viewSeconds);panX=event.x;onZoom?.invoke(next.start(),next.seconds())}
                else touch.move(event.x.toDouble(),time,slop)
            }
            MotionEvent.ACTION_UP->{val range=touch.up(time);if(!panMode&&range!=null)onSelection?.invoke(range.from(),range.to());performClick();parent.requestDisallowInterceptTouchEvent(false)}
            MotionEvent.ACTION_CANCEL->{touch.cancel();parent.requestDisallowInterceptTouchEvent(false)}
        }
        invalidate();return true
    }
    override fun performClick():Boolean {super.performClick();return true}
}

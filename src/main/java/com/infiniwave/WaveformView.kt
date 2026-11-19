package com.infiniwave

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
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
    private var anchor=0.0
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val scale=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(detector:ScaleGestureDetector):Boolean {
            val pivot=viewFrom+detector.focusX/max(1,width)*viewSeconds
            val span=(viewSeconds/detector.scaleFactor).coerceIn(.02,3600.0)
            onZoom?.invoke(max(0.0,pivot-detector.focusX/max(1,width)*span),span);return true
        }
    })
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas);canvas.drawColor(if(trackSelected)0xff182d3b.toInt() else 0xff14222d.toInt())
        val x0=((selectionFrom-viewFrom)/viewSeconds*width).toFloat();val x1=((selectionTo-viewFrom)/viewSeconds*width).toFloat()
        paint.color=0x553babb2;canvas.drawRect(x0,0f,x1,height.toFloat(),paint)
        paint.textSize=12*resources.displayMetrics.scaledDensity
        for(i in 0..5){val x=width*i/5f;paint.color=0xff314956.toInt();canvas.drawLine(x,0f,x,height.toFloat(),paint);paint.color=0xff96abb7.toInt();canvas.drawText("%.2fs".format(viewFrom+viewSeconds*i/5),x+4,paint.textSize+3,paint)}
        val p=peaks?:return
        for(ch in p.low.indices){
            val lane=(height-24f)/p.low.size;val center=24+lane*(ch+.5f)
            paint.color=0xff375462.toInt();canvas.drawLine(0f,center,width.toFloat(),center,paint)
            paint.color=if(track.mute())0xff657b85.toInt() else 0xff58d5ca.toInt()
            for(x in 0 until width){
                val first=((viewFrom+viewSeconds*x/max(1,width))*WaveProject.RATE-track.offset()).toInt()
                val last=((viewFrom+viewSeconds*(x+1)/max(1,width))*WaveProject.RATE-track.offset()).toInt()
                if(last<0||first>=p.frames)continue
                val a=(max(0,first).toLong()*p.low[ch].size/max(1,p.frames)).toInt().coerceIn(p.low[ch].indices)
                val b=(max(0,last).toLong()*p.low[ch].size/max(1,p.frames)).toInt().coerceIn(p.low[ch].indices)
                var low=1f;var high=-1f
                for(bin in a..b){low=min(low,p.low[ch][bin]);high=max(high,p.high[ch][bin])}
                canvas.drawLine(x.toFloat(),center-high.coerceIn(-1f,1f)*lane*.42f,x.toFloat(),center-low.coerceIn(-1f,1f)*lane*.42f,paint)
            }
            paint.color=0xffa4c1cb.toInt();canvas.drawText("CH ${ch+1}",8f,center-lane*.3f,paint)
        }
        playSeconds?.let {val x=((it-viewFrom)/viewSeconds*width).toFloat();paint.color=0xffffc777.toInt();canvas.drawLine(x,0f,x,height.toFloat(),paint)}
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        scale.onTouchEvent(event)
        if(event.pointerCount>1||scale.isInProgress)return true
        val time=(viewFrom+event.x.coerceIn(0f,width.toFloat())/max(1,width)*viewSeconds).coerceAtLeast(0.0)
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{parent.requestDisallowInterceptTouchEvent(true);anchor=time;onSelection?.invoke(time,time);return true}
            MotionEvent.ACTION_MOVE->{onSelection?.invoke(min(anchor,time),max(anchor,time));return true}
            MotionEvent.ACTION_UP->{performClick();parent.requestDisallowInterceptTouchEvent(false);return true}
        }
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
}

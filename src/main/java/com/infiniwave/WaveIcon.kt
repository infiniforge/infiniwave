package com.infiniwave

import android.content.Context
import android.graphics.*
import android.view.View

/** App-owned vector toolbar icons with 48 dp targets, TalkBack names and long-press tooltips. */
class WaveIcon(context:Context,var symbol:String,label:String):View(context){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init{isClickable=true;isFocusable=true;contentDescription=label;tooltipText=label}
    fun label(text:String){contentDescription=text;tooltipText=text}
    override fun drawableStateChanged(){super.drawableStateChanged();invalidate()}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas);val d=resources.displayMetrics.density
        if(isSelected||isPressed||isFocused){paint.color=if(isSelected)WaveUi.accent else WaveUi.raised;paint.style=Paint.Style.FILL;canvas.drawRoundRect(width/2-20*d,height/2-20*d,width/2+20*d,height/2+20*d,10*d,10*d,paint)}
        canvas.save();canvas.translate(width/2-12*d,height/2-12*d);canvas.scale(d,d)
        paint.color=if(isSelected)WaveUi.background else WaveUi.text;paint.alpha=if(isEnabled)255 else 75
        paint.style=Paint.Style.STROKE;paint.strokeWidth=1.8f;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND;paint.pathEffect=null
        fun line(vararg points:Float){val p=Path();p.moveTo(points[0],points[1]);for(i in 2 until points.size step 2)p.lineTo(points[i],points[i+1]);canvas.drawPath(p,paint)}
        fun box(l:Float,t:Float,r:Float,b:Float)=canvas.drawRoundRect(l,t,r,b,1.5f,1.5f,paint)
        fun undo(){line(3f,4f,3f,10f,9f,10f);val p=Path();p.moveTo(3f,10f);p.cubicTo(12f,0f,27f,11f,17f,21f);canvas.drawPath(p,paint)}
        when(symbol){
            "open"->{line(2f,8f,2f,4f,9f,4f,12f,8f,22f,8f,19f,20f,2f,20f,2f,8f)}
            "undo"->undo()
            "redo"->{canvas.scale(-1f,1f,12f,12f);undo()}
            "export"->{line(4f,15f,4f,21f,20f,21f,20f,15f);line(12f,16f,12f,3f);line(7f,8f,12f,3f,17f,8f)}
            "more"->{paint.style=Paint.Style.FILL;for(y in listOf(5f,12f,19f))canvas.drawCircle(12f,y,1.6f,paint)}
            "play"->{paint.style=Paint.Style.FILL;val p=Path();p.moveTo(6f,3f);p.lineTo(21f,12f);p.lineTo(6f,21f);p.close();canvas.drawPath(p,paint)}
            "stop"->{paint.style=Paint.Style.FILL;box(5f,5f,19f,19f)}
            "select"->{paint.pathEffect=DashPathEffect(floatArrayOf(3f,2f),0f);box(3f,4f,21f,20f);paint.pathEffect=null;line(8f,7f,8f,17f);line(16f,7f,16f,17f)}
            "effects"->{for(x in listOf(5f,12f,19f))line(x,3f,x,21f);paint.style=Paint.Style.FILL;canvas.drawCircle(5f,8f,2.5f,paint);canvas.drawCircle(12f,16f,2.5f,paint);canvas.drawCircle(19f,10f,2.5f,paint)}
            "channels"->{line(3f,6f,9f,6f,13f,3f,21f,3f);line(9f,6f,13f,10f,21f,10f);line(21f,17f,15f,17f,11f,14f,3f,14f);line(15f,17f,11f,21f,3f,21f)}
            "tracks"->{box(3f,3f,21f,9f);box(3f,15f,21f,21f);line(6f,6f,9f,6f,11f,4f,14f,8f,16f,6f,18f,6f)}
            "generate"->{line(2f,12f,5f,12f,8f,3f,12f,21f,16f,6f,19f,12f,22f,12f)}
            "view"->{canvas.drawCircle(10f,10f,7f,paint);line(15f,15f,22f,22f);line(6f,10f,14f,10f);line(10f,6f,10f,14f)}
            "fit"->{line(3f,9f,3f,3f,9f,3f);line(15f,3f,21f,3f,21f,9f);line(21f,15f,21f,21f,15f,21f);line(9f,21f,3f,21f,3f,15f)}
            "range"->{line(4f,3f,4f,21f);line(20f,3f,20f,21f);line(7f,12f,17f,12f);line(10f,9f,7f,12f,10f,15f);line(14f,9f,17f,12f,14f,15f)}
            "back"->line(15f,4f,7f,12f,15f,20f)
            "close"->{line(5f,5f,19f,19f);line(19f,5f,5f,19f)}
        }
        paint.alpha=255;canvas.restore()
    }
    override fun onInitializeAccessibilityNodeInfo(info:android.view.accessibility.AccessibilityNodeInfo){super.onInitializeAccessibilityNodeInfo(info);info.className="android.widget.Button"}
    override fun performClick():Boolean{super.performClick();return true}
}
object WaveUi{
    val background=0xff101e28.toInt();val surface=0xff172b38.toInt();val raised=0xff253e4a.toInt()
    val text=0xffe5f7f4.toInt();val secondary=0xffa5bdc8.toInt();val accent=0xff58d5ca.toInt();val danger=0xffffaaa0.toInt()
}

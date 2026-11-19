package com.infiniwave

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*

/** A compact navigable tool dialog; subpages keep Back/Close consistent across every tool. */
class WaveToolSheet(private val activity:Activity,private val available:()->Boolean,private val error:(String)->Unit,private val closed:()->Unit){
    private data class Page(val name:String,val build:WaveToolSheet.()->Unit)
    private val pages=mutableListOf<Page>()
    private lateinit var dialog:AlertDialog
    private val content=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));setBackgroundColor(WaveUi.surface)}
    private val body=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL}
    private val heading=TextView(activity).apply{setTextColor(WaveUi.text);textSize=17f;typeface=Typeface.DEFAULT_BOLD}
    private val progress=ProgressBar(activity).apply{isIndeterminate=true;indeterminateTintList=ColorStateList.valueOf(WaveUi.accent)}
    private val back=WaveIcon(activity,"back","Back to previous tools").apply{setOnClickListener{if(pages.size>1){pages.removeAt(pages.lastIndex);refresh()}}}
    private var scroll:ScrollView?=null
    private fun dp(n:Int)=(n*activity.resources.displayMetrics.density).toInt()
    fun show(name:String,build:WaveToolSheet.()->Unit){
        val header=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL}
        header.addView(back,LinearLayout.LayoutParams(dp(48),dp(48)));header.addView(heading,LinearLayout.LayoutParams(0,-2,1f));header.addView(progress,LinearLayout.LayoutParams(dp(24),dp(24)))
        header.addView(WaveIcon(activity,"close","Close tools").apply{setOnClickListener{dismiss()}},LinearLayout.LayoutParams(dp(48),dp(48)));content.addView(header)
        val limit=dp((activity.resources.configuration.screenHeightDp*.52).toInt().coerceAtLeast(120))
        scroll=object:ScrollView(activity){override fun onMeasure(w:Int,h:Int){val max=if(View.MeasureSpec.getMode(h)==View.MeasureSpec.UNSPECIFIED)limit else minOf(limit,View.MeasureSpec.getSize(h));super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(max,View.MeasureSpec.AT_MOST))}}.apply{addView(body)}
        content.addView(scroll,LinearLayout.LayoutParams(-1,-2));pages+=Page(name,build)
        dialog=AlertDialog.Builder(activity).setView(content).create();dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener{closed()};dialog.setOnKeyListener{_,key,event->if(key==android.view.KeyEvent.KEYCODE_BACK&&event.action==android.view.KeyEvent.ACTION_UP&&pages.size>1){pages.removeAt(pages.lastIndex);refresh();true}else false}
        dialog.show();dialog.window?.apply{setLayout(dp(minOf(activity.resources.configuration.screenWidthDp-16,460)),ViewGroup.LayoutParams.WRAP_CONTENT);setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL);addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);attributes=attributes.apply{dimAmount=.12f};setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)}
        refresh()
    }
    fun dismiss(){if(::dialog.isInitialized)dialog.dismiss()}
    fun push(name:String,build:WaveToolSheet.()->Unit){pages+=Page(name,build);scroll?.scrollTo(0,0);refresh()}
    fun refresh(){if(pages.isEmpty())return;heading.text=pages.last().name;progress.visibility=if(available())View.GONE else View.VISIBLE;back.visibility=if(pages.size>1)View.VISIBLE else View.INVISIBLE;body.removeAllViews();pages.last().build(this)}
    fun note(text:String){body.addView(TextView(activity).apply{this.text=text;setTextColor(WaveUi.secondary);textSize=13f;setPadding(dp(8),dp(6),dp(8),dp(10))})}
    fun action(title:String,detail:String="",enabled:Boolean=true,danger:Boolean=false,run:()->Unit){
        val usable=enabled&&available()
        val tile=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;minimumHeight=dp(52);setPadding(dp(12),dp(9),dp(12),dp(9));isEnabled=usable;alpha=if(usable)1f else .4f;isClickable=true;isFocusable=true;contentDescription=if(detail.isEmpty())title else "$title. $detail"
            background=RippleDrawable(ColorStateList.valueOf(WaveUi.raised),GradientDrawable().apply{setColor(WaveUi.raised);cornerRadius=dp(10).toFloat()},null)
            setOnClickListener{if(usable&&available())try{run()}catch(e:Exception){val message=e.message?:"Operation failed";error(message);Toast.makeText(activity,message,Toast.LENGTH_LONG).show()}}}
        tile.addView(TextView(activity).apply{text=title;setTextColor(if(danger)WaveUi.danger else WaveUi.text);textSize=15f})
        if(detail.isNotEmpty())tile.addView(TextView(activity).apply{text=detail;setTextColor(WaveUi.secondary);textSize=12f})
        body.addView(tile,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(3),0,dp(3))})
    }
}

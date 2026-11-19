package com.infiniwave

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import com.infinifolder.audio.processing.PcmEffects
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.*
import com.infiniwave.editor.*
import io.github.gretchycat.infiniselect.*
import io.github.gretchycat.infinisynth.*
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.*

/** Multitrack audio workspace. Providers own source access; edits and projects remain app-owned. */
class InfiniWaveActivity : Activity() {
    private val history=ProjectHistory()
    private val worker=Executors.newSingleThreadExecutor()
    private val peakCache=Collections.synchronizedMap(WeakHashMap<AudioBuffer,WaveformView.Peaks>())
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var rows:LinearLayout
    private lateinit var status:TextView
    private lateinit var selectionLabel:TextView
    private val controls=mutableListOf<View>()
    private lateinit var deleteButton:Button
    private lateinit var undoButton:WaveIcon
    private lateinit var redoButton:WaveIcon
    private lateinit var playButton:WaveIcon
    private lateinit var stopButton:WaveIcon
    private lateinit var cancelButton:Button
    private lateinit var detailLabel:TextView
    private val tabs=mutableMapOf<String,WaveIcon>()
    private var toolSheet:WaveToolSheet?=null
    private var activeTool:String?=null
    private var panMode=false
    private val waves=mutableListOf<WaveformView>()
    private var selected:String?=null
    private var from=0.0
    private var to=0.0
    private var viewFrom=0.0
    private var viewSeconds=5.0
    private var clipboard:AudioBuffer?=null
    private var jobGeneration=0
    private var busy=false
    private var pickerPending=false
    private var savePending=false
    private var pendingFile:File?=null
    private var directory:Uri?=null
    private var bank:SoundFontBank?=null
    private var bankSample=0
    private var task:Future<*>?=null
    private class Playback {
        @Volatile var active=true
        @Volatile var player:AudioTrack?=null
        @Volatile var frame=0
        var thread:Thread?=null
    }
    @Volatile private var playback:Playback?=null
    private val playing:Boolean get()=playback?.active==true
    private var resumed=false
    private val recovery:File get()=File(filesDir,"workspace.iwave")

    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        window.statusBarColor=0xff101e28.toInt();window.navigationBarColor=0xff101e28.toInt()
        if(android.os.Build.VERSION.SDK_INT>=30)window.setDecorFitsSystemWindows(false)
        else window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        panMode=state?.getBoolean("panMode")?:false
        buildUi()
        pendingFile=state?.getString("pendingFile")?.let(::File)?.takeIf(File::isFile)
        savePending=state?.getBoolean("savePending")?:false
        pickerPending=state?.getBoolean("pickerPending")?:false
        directory=state?.getString("directory")?.let(Uri::parse)
        selected=state?.getString("selected");from=state?.getDouble("from")?:0.0;to=state?.getDouble("to")?:0.0
        fun incoming(){state?.takeIf{it.containsKey("viewFrom")}?.let{saved->runCatching{showView(TimelineViewport(saved.getDouble("viewFrom"),saved.getDouble("viewSeconds",5.0)))}};if(intent?.action==Intent.ACTION_VIEW&&intent.data!=null)receiveIncoming(intent)else refresh()}
        if(recovery.isFile){job("Restoring workspace",{
            val p=recovery.inputStream().buffered().use(ProjectFile::read);preparePeaks(p);p
        }){p->history.reset(p);fitView();incoming()}}
        else incoming()
    }
    override fun onSaveInstanceState(out:Bundle){
        out.putBoolean("panMode",panMode);out.putDouble("viewFrom",viewFrom);out.putDouble("viewSeconds",viewSeconds)
        out.putString("pendingFile",pendingFile?.path);out.putBoolean("savePending",savePending);out.putBoolean("pickerPending",pickerPending)
        out.putString("directory",directory?.toString());out.putString("selected",selected);out.putDouble("from",from);out.putDouble("to",to);super.onSaveInstanceState(out)
    }
    override fun onNewIntent(incoming:Intent){super.onNewIntent(incoming);setIntent(incoming);if(!busy&&!savePending)receiveIncoming(incoming)}
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun canAct()=!busy&&!savePending&&!pickerPending
    private fun runAction(action:()->Unit){if(canAct())try{action()}catch(e:Exception){message(e.message?:"Operation failed")}}
    private fun icon(symbol:String,label:String,action:()->Unit)=WaveIcon(this,symbol,label).apply{setOnClickListener{runAction(action)};controls+=this}
    private fun row()=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
    private fun label(value:String,size:Float=13f)=TextView(this).apply{text=value;textSize=size;setTextColor(WaveUi.text)}
    private fun buildUi(){
        val outer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(WaveUi.background)}
        outer.setOnApplyWindowInsetsListener{view,insets->
            if(android.os.Build.VERSION.SDK_INT>=30){val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout());view.setPadding(bars.left,bars.top,bars.right,bars.bottom)}
            else view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            insets
        }
        fun LinearLayout.addIcon(icon:WaveIcon){addView(icon,LinearLayout.LayoutParams(dp(48),dp(48)))}
        val heading=row().apply{setBackgroundColor(WaveUi.surface)}
        heading.addIcon(icon("open","Import audio or SoundFont"){dismissTools();open(false)})
        val titles=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setOnClickListener{quickHelp()};contentDescription="InfiniWave help"}
        titles.addView(label("InfiniWave",15f).apply{typeface=Typeface.DEFAULT_BOLD})
        detailLabel=label("Audio workspace",11f).apply{setTextColor(WaveUi.secondary);maxLines=1};titles.addView(detailLabel);heading.addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        undoButton=icon("undo","Undo edit"){history.undo();changed()};heading.addIcon(undoButton)
        redoButton=icon("redo","Redo edit"){history.redo();changed()};heading.addIcon(redoButton)
        heading.addIcon(icon("export","Export audio"){dismissTools();exportMenu()})
        val more=icon("more","Project and more options"){};more.setOnClickListener{if(canAct())moreMenu(more)};heading.addIcon(more)
        outer.addView(heading,LinearLayout.LayoutParams(-1,dp(maxOf(48,(36*resources.configuration.fontScale).toInt()))))
        val selectionBar=row().apply{setPadding(dp(10),dp(6),dp(8),dp(6));setBackgroundColor(WaveUi.surface)}
        selectionLabel=label("Drag across sound to select it",12f).apply{setTextColor(WaveUi.secondary);maxLines=3}
        selectionBar.addView(selectionLabel,LinearLayout.LayoutParams(0,-2,1f));selectionBar.addIcon(icon("range","Set exact selection time"){rangeDialog()})
        deleteButton=Button(this).apply{text="Delete selection";textSize=12f;isAllCaps=false;minHeight=dp(48);setTextColor(WaveUi.danger);backgroundTintList=ColorStateList.valueOf(WaveUi.raised);contentDescription="Delete selected audio and close the gap in this waveform";tooltipText=contentDescription;setOnClickListener{runAction{if(selectedTrack()?.profile()!=null){renderProfile();message("Ready for sample editing • Drag to select, then Delete selection")}else deleteSelection()}}}
        controls+=deleteButton;selectionBar.addView(deleteButton,LinearLayout.LayoutParams(dp(116),dp(48)));outer.addView(selectionBar)
        rows=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),0,dp(8),dp(12))}
        outer.addView(WaveformWorkspace(this).apply{
            isFillViewport=true;waveformInset=dp(8);canZoom=::canAct
            onPinch={factor,previousFocus,focus->showView(viewport().zoom(factor,previousFocus,focus))}
            addView(rows)
        },LinearLayout.LayoutParams(-1,0,1f))
        val footer=row().apply{setPadding(dp(10),dp(4),dp(8),dp(4))}
        status=label("Drag to select • Pinch to zoom • Select a tool below",12f).apply{setTextColor(WaveUi.secondary);accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        footer.addView(status,LinearLayout.LayoutParams(0,-2,1f));cancelButton=Button(this).apply{text="Cancel task";isAllCaps=false;setOnClickListener{cancelJob()};visibility=View.GONE};footer.addView(cancelButton);outer.addView(footer)
        val transport=row().apply{setBackgroundColor(WaveUi.surface)}
        playButton=icon("play","Play audible waveforms"){play()};transport.addIcon(playButton)
        stopButton=WaveIcon(this,"stop","Stop playback").apply{setOnClickListener{stop();updateControls()}};transport.addIcon(stopButton)
        val strip=row()
        for((key,name) in listOf("select" to "Select","effects" to "Effects","channels" to "Channels","tracks" to "Tracks","generate" to "Generate","view" to "View")){
            val tile=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER}
            val tool=icon(key,"$name tools"){openTool(key)};tabs[key]=tool;tile.addIcon(tool)
            tile.addView(label(name,10f).apply{gravity=Gravity.CENTER;setTextColor(WaveUi.secondary);setOnClickListener{runAction{openTool(key)}}},LinearLayout.LayoutParams(-1,dp(16)))
            strip.addView(tile,LinearLayout.LayoutParams(dp(60),dp(64)))
        }
        transport.addView(HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=true;addView(strip)},LinearLayout.LayoutParams(0,dp(64),1f))
        outer.addView(transport,LinearLayout.LayoutParams(-1,dp(64)));setContentView(outer);outer.requestApplyInsets()
    }
    private fun selectedTrack()=selected?.let{history.current().tracks().firstOrNull{t->t.id()==it}}
    private fun selectionFrames():Pair<Int,Int> {val t=selectedTrack()?:return Pair(0,0);return Pair(max(t.offset(),(from*WaveProject.RATE).roundToInt()),min(t.offset()+t.audio().frames(),(to*WaveProject.RATE).roundToInt()))}
    private fun hasSelection():Boolean{val(a,b)=selectionFrames();return to>from&&b>a}
    private fun updateControls(){
        val allowed=canAct();controls.forEach{it.isEnabled=allowed}
        undoButton.isEnabled=allowed&&history.canUndo();redoButton.isEnabled=allowed&&history.canRedo()
        val profile=selectedTrack()?.profile()!=null
        deleteButton.text=if(profile)"Render to audio" else "Delete selection"
        deleteButton.contentDescription=if(profile)"Render parameter profile to an editable audio waveform" else "Delete selected audio and close the gap in this waveform"
        deleteButton.tooltipText=deleteButton.contentDescription
        deleteButton.isEnabled=allowed&&(profile||hasSelection())
        playButton.isEnabled=allowed&&history.current().tracks().isNotEmpty();stopButton.isEnabled=playing
        cancelButton.visibility=if(busy)View.VISIBLE else View.GONE
        detailLabel.text="${history.current().tracks().size} waveforms · ${if(panMode)"Pan" else "Select"}"
        tabs.forEach{(key,view)->view.isSelected=key==activeTool}
        waves.forEach{it.panMode=panMode;it.interactionEnabled=allowed}
    }
    private fun refresh(){
        if(isDestroyed)return
        val p=history.current();if(p.tracks().none{it.id()==selected})selected=p.tracks().firstOrNull()?.id()
        rows.removeAllViews();waves.clear()
        if(p.tracks().isEmpty())rows.addView(TextView(this).apply{text="Your audio workspace\n\nImport several files, generate a sound, or pull a sample from a SoundFont. Each becomes a separate waveform track.\n\nDrag across a waveform, then press Delete selection to remove that sound. Pinch with two fingers to zoom.\n\nUse Effects for inversion, noise filtering and normalization. Use Channels to split Left/Right and recombine chosen waveforms.";setTextColor(0xff98b4c0.toInt());textSize=18f;setPadding(dp(16),dp(30),dp(16),dp(20))})
        p.tracks().forEach{track->
            val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(10),0,0)}
            head.addView(Button(this).apply{text="${track.name()}  ·  ${track.profile()?.engine()?.name?.plus(" profile")?:"audio"}  ·  ${track.audio().channels()} ch";textSize=13f;setTextColor(WaveUi.text);isAllCaps=false;backgroundTintList=ColorStateList.valueOf(if(track.id()==selected)WaveUi.raised else WaveUi.surface);setOnClickListener{if(canAct()){selected=track.id();refresh()}};setOnLongClickListener{if(canAct()){selected=track.id();refresh();openTool("tracks")};true}},LinearLayout.LayoutParams(0,-2,1f))
            fun toggle(label:String,value:Boolean,change:(Boolean)->WaveProject.Track){head.addView(CheckBox(this).apply{text=label;isChecked=value;setTextColor(0xffb4cbd3.toInt());isEnabled=canAct();tooltipText=if(label=="M")"Mute waveform" else "Solo waveform";contentDescription=tooltipText;setOnCheckedChangeListener{_,checked->if(canAct()){stop();history.apply(history.current().replace(change(checked)));changed()}}})}
            toggle("M",track.mute()){track.settings(track.name(),track.offset(),track.gain(),track.pan(),it,track.solo(),track.route())}
            toggle("S",track.solo()){track.settings(track.name(),track.offset(),track.gain(),track.pan(),track.mute(),it,track.route())}
            rows.addView(head)
            rows.addView(TextView(this).apply{text="%.2fs start  ·  %.2fs audio  ·  %.1f dB  ·  %s".format(track.offset().toDouble()/WaveProject.RATE,track.audio().seconds(),20*log10(max(.00001,track.gain())),if(track.route()<0)"pan %.2f".format(track.pan()) else "output ${track.route()+1}");setTextColor(0xff8fadb9.toInt());textSize=12f})
            val wave=WaveformView(this).apply{
                this.track=track;peaks=peakCache[track.audio()];trackSelected=track.id()==this@InfiniWaveActivity.selected
                onSelection={a,b->if(!busy&&!playing){this@InfiniWaveActivity.selected=track.id();from=a;to=b;refreshWaves();toolSheet?.refresh()}}
                onZoom={start,span->viewFrom=start;viewSeconds=span;refreshWaves()}
                panMode=this@InfiniWaveActivity.panMode;interactionEnabled=canAct()
                contentDescription="${track.name()} waveform, ${track.audio().channels()} channels. Drag to select a time range."
            }
            rows.addView(wave,LinearLayout.LayoutParams(-1,dp(110+min(6,track.audio().channels())*24)) );waves+=wave
        }
        refreshWaves();toolSheet?.refresh()
    }
    private fun refreshWaves(){
        val t=selectedTrack()
        selectionLabel.text=when{
            t==null->"Import or generate a waveform\nDrag to select • Pinch to zoom"
            t.profile()!=null->"Parameter profile · Render to audio\nThen select and edit its samples"
            to>from&&hasSelection()->"${t.name().take(24)} · %.3f–%.3f s\nHighlighted audio • Delete closes the gap".format(from,to)
            to>from->"Selection misses this waveform\nUse Select → Whole waveform"
            panMode->"Drag to pan • Pinch to zoom\nView %.2f–%.2f s".format(viewFrom,viewFrom+viewSeconds)
            else->"Drag across sound, then Delete selection\nCursor %.3f s • Pinch to zoom".format(from)
        }
        waves.forEach{it.viewFrom=viewFrom;it.viewSeconds=viewSeconds;it.selectionFrom=from;it.selectionTo=to;it.trackSelected=it.track.id()==selected;it.invalidate()}
        updateControls()
    }
    private fun viewport()=TimelineViewport(viewFrom,viewSeconds)
    private fun showView(view:TimelineViewport){viewFrom=view.start();viewSeconds=view.seconds();refreshWaves()}
    private fun fitView(){val view=TimelineViewport.fit(history.current().endFrame().toDouble()/WaveProject.RATE);viewFrom=view.start();viewSeconds=view.seconds()}
    private fun zoom(factor:Double){showView(viewport().zoom(1/factor,.5,.5))}
    private fun selectWhole(){val t=selectedTrack()?:error("Select a waveform");from=t.offset().toDouble()/WaveProject.RATE;to=from+t.audio().seconds();refreshWaves();toolSheet?.refresh()}
    private fun rangeDialog(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,12,24,12)}
        fun field(label:String,value:Double):EditText{box.addView(TextView(this).apply{text=label});return EditText(this).apply{inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;setText(value.toString());box.addView(this)}}
        val a=field("Start seconds",from);val b=field("End seconds",to)
        validatedDialog("Select time",box){val x=a.text.toString().toDouble();val y=b.text.toString().toDouble();require(x.isFinite()&&y.isFinite()&&x>=0&&y>=x&&y<=3600){"Range must be between 0 and 3600 seconds"};from=x;to=y;refreshWaves();toolSheet?.refresh()}
    }
    private fun validatedDialog(title:String,view:android.view.View,action:()->Unit){
        val d=AlertDialog.Builder(this).setTitle(title).setView(view).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
        d.setOnShowListener{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{try{action();d.dismiss()}catch(e:Exception){message(e.message?:"Invalid settings");Toast.makeText(this,e.message?:"Invalid settings",Toast.LENGTH_LONG).show()}}};d.show();d.window?.apply{setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL);setLayout(dp(minOf(resources.configuration.screenWidthDp-16,460)),ViewGroup.LayoutParams.WRAP_CONTENT);setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)}
    }
    private fun dismissTools(){toolSheet?.dismiss();toolSheet=null;activeTool=null}
    private fun openTool(key:String){
        if(activeTool==key&&toolSheet!=null){dismissTools();updateControls();return}
        dismissTools();activeTool=key
        val sheet=WaveToolSheet(this,::canAct,::message){toolSheet=null;activeTool=null;updateControls()};toolSheet=sheet
        val title=when(key){"select"->"Selection & editing";"effects"->"Waveform effects";"channels"->"Channels";"tracks"->"Waveforms";"generate"->"Generate & profiles";else->"Timeline view"}
        sheet.show(title){when(key){"select"->selectionTools(this);"effects"->effectTools(this);"channels"->channelTools(this);"tracks"->trackTools(this);"generate"->generationTools(this);else->viewTools(this)}};updateControls()
    }
    private fun selectionTools(sheet:WaveToolSheet)=with(sheet){
        val t=selectedTrack();val audio=t!=null&&t.profile()==null
        note(if(t?.profile()!=null)"This is a parameter profile. Render it to audio before deleting or editing its samples." else "Drag across a waveform, then press Delete selection above. Edits affect the selected waveform; Undo restores them.")
        action("Select whole waveform",enabled=t!=null){selectWhole()}
        action("Exact time range…","Enter start/end times in seconds",t!=null){rangeDialog()}
        action("Delete selected audio","Remove highlighted sound and close the gap in this waveform",audio&&hasSelection(),true){deleteSelection()}
        action("Clipboard ›","Copy, cut and paste",audio||clipboard!=null){push("Clipboard"){clipboardTools(this)}}
        action("Trim to selection","Keep highlighted sound at its original timeline position",audio&&hasSelection()){editSelection(WaveProject.Edit.TRIM)}
        action("Silence selection","Replace highlighted samples with silence; keep duration",audio&&hasSelection()){editSelection(WaveProject.Edit.SILENCE)}
        if(t?.profile()!=null)action("Render profile to audio","Keep its preview as an editable waveform"){renderProfile()}
        action("Clear selection","Keep a cursor at the selection start",to>from){to=from;refreshWaves();refresh()}
    }
    private fun clipboardTools(sheet:WaveToolSheet)=with(sheet){
        val audio=selectedTrack()?.let{it.profile()==null}==true
        action("Copy selection",enabled=audio&&hasSelection()){copySelection(false)}
        action("Cut selection","Copy it and close the gap",audio&&hasSelection()){copySelection(true)}
        action("Paste at cursor","Insert clipboard audio in the selected waveform",audio&&clipboard!=null){paste(false)}
        action("Paste as a new waveform","Keep the clipboard's channel layout",clipboard!=null){paste(true)}
    }
    private fun trackTools(sheet:WaveToolSheet)=with(sheet){
        val t=selectedTrack();note(t?.let{"Selected: ${it.name()} · ${it.audio().channels()} channels"}?:"Tap a waveform header to select it.")
        action("Waveform settings…","Name, gain, pan, start time and output routing",t!=null){trackSettings()}
        action("Duplicate waveform",enabled=t!=null){val id=selected?:return@action;val p=history.current().duplicate(id);history.apply(p);selected=p.tracks().last().id();changed()}
        action("Channel tools ›","Split Left/Right or combine chosen mono waveforms",t!=null){push("Channels"){channelTools(this)}}
        if(t?.profile()!=null)action("Edit profile parameters…"){dismissTools();editProfile()}
        action("Remove whole waveform","Remove this track from the workspace",t!=null,true){val id=selected?:return@action;AlertDialog.Builder(this@InfiniWaveActivity).setTitle("Remove ${selectedTrack()?.name()}?").setMessage("This removes the whole waveform. To remove only part, highlight the sound and use Delete selection.").setNegativeButton("Cancel",null).setPositiveButton("Remove"){_,_->history.apply(history.current().remove(id));changed()}.show()}
        action("Mix audible waveforms to stereo","Replace the workspace with a stereo mix; Undo restores tracks",history.current().tracks().isNotEmpty()){val p=history.current();val(a,b)=renderRange();job("Mixing waveforms",{p.mix(a,b,2)}){prepareNewDocument(WaveProject().add("Stereo mix",it,a))}}
    }
    private fun channelTools(sheet:WaveToolSheet)=with(sheet){
        val t=selectedTrack();note("Split stereo audio into independent Left and Right waveforms. Recombine any chosen pair; other waveforms stay in the workspace.")
        action(if(t?.audio()?.channels()==2)"Split Left / Right" else "Split channels to mono","Create one editable waveform per source channel",t!=null&&t.profile()==null&&t.audio().channels()>1){splitChannels()}
        action("Recombine Left + Right…","Choose which mono waveform becomes each stereo side",monoTracks().size>=2){stereoDialog()}
        action("Combine multiple channels…","Choose mono waveforms in workspace order",monoTracks().size>=2){combineDialog()}
        if(t?.profile()!=null)action("Render profile to audio","Required before splitting its waveform"){renderProfile()}
    }
    private fun generationTools(sheet:WaveToolSheet)=with(sheet){
        action("Generate a sound…","Oscillators, FM, SoundFont, OPL3, SID and CMS"){dismissTools();showGenerator()}
        action("Profile tools ›","Edit parameters, render to audio, save or load JSON"){push("Rendering profiles"){
            val profile=selectedTrack()?.profile()!=null
            action("Edit selected profile…",enabled=profile){dismissTools();editProfile()}
            action("Render profile to audio","Enable sample editing; Undo restores parameters",profile){renderProfile()}
            action("Save profile JSON…",enabled=profile){dismissTools();saveProfile()}
            action("Load profile JSON…"){dismissTools();openProfile()}
        }}
        action("Extract original SoundFont sample…","Create an editable waveform from an imported SF2 bank",bank!=null){dismissTools();extractSample()}
    }
    private fun viewTools(sheet:WaveToolSheet)=with(sheet){
        note("Pinch with two fingers to zoom around the sound under your fingers. Your time selection is preserved. All waveform views stay aligned.")
        action(if(panMode)"Use drag to select" else "Use drag to pan",if(panMode)"One-finger drags will highlight audio" else "Move around the timeline without changing the selection"){panMode=!panMode;refreshWaves();refresh()}
        action("Zoom in"){zoom(.5)};action("Zoom out"){zoom(2.0)}
        action("Fit all waveforms"){fitView();refreshWaves()}
        action("Fit selection",enabled=hasSelection()){val span=(to-from)*1.1;showView(TimelineViewport(0.0,span.coerceIn(TimelineViewport.MIN_SECONDS,TimelineViewport.MAX_SECONDS)).pan(max(0.0,from-span*.045)))}
        action("Pan left"){showView(viewport().pan(-viewSeconds/2))};action("Pan right"){showView(viewport().pan(viewSeconds/2))}
    }
    private fun selectedAudio():WaveProject.Track{val t=selectedTrack()?:error("Select a waveform");require(t.profile()==null){"Render this profile to audio before editing its samples"};return t}
    private fun editSelection(edit:WaveProject.Edit){
        val t=selectedAudio();val(a,b)=selectionFrames();require(to>from&&b>a){"Drag across sound to select the part to edit"};val p=history.current()
        job(if(edit==WaveProject.Edit.DELETE)"Deleting selected audio" else "Editing selected audio",{p.edit(t.id(),a,b,edit).also(::preparePeaks)}){
            history.apply(it);if(edit==WaveProject.Edit.DELETE){from=a.toDouble()/WaveProject.RATE;to=from};changed();message(if(edit==WaveProject.Edit.DELETE)"Selected audio deleted • Undo restores it" else "Waveform updated • Undo restores it")
        }
    }
    private fun deleteSelection()=editSelection(WaveProject.Edit.DELETE)
    private fun copySelection(cut:Boolean){
        val t=selectedAudio();val(a,b)=selectionFrames();require(to>from&&b>a){"Select a time range"};val p=history.current()
        job(if(cut)"Cutting selection" else "Copying selection",{val clip=p.copyRange(t.id(),a,b);val next=if(cut)p.edit(t.id(),a,b,WaveProject.Edit.DELETE)else p;preparePeaks(next);Pair(clip,next)}){(clip,next)->clipboard=clip;if(cut){history.apply(next);from=a.toDouble()/WaveProject.RATE;to=from;changed()}else{updateControls();toolSheet?.refresh()};message(if(cut)"Audio cut to clipboard • Undo restores it" else "Selection copied")}
    }
    private fun paste(asNew:Boolean){
        val clip=clipboard?:error("Copy some audio first");val offset=(from*WaveProject.RATE).roundToInt()
        if(asNew)job("Preparing pasted waveform",{clip.also{peakCache[it]=WaveformView.Peaks.of(it)}}){addAudio("Pasted waveform",it,offset)}
        else{val t=selectedAudio();val p=history.current();job("Pasting audio",{p.paste(t.id(),offset,clip).also(::preparePeaks)}){history.apply(it);changed()}}
    }
    private fun renderProfile(){val t=selectedTrack()?:error("Select a profile");require(t.profile()!=null);history.apply(history.current().renderToAudio(t.id()));changed()}
    private fun monoTracks()=history.current().tracks().filter{it.profile()==null&&it.audio().channels()==1}
    private fun splitChannels(){
        val t=selectedAudio();val p=history.current();val index=p.tracks().indexOf(t)
        job("Splitting channels",{p.splitChannels(t.id()).also(::preparePeaks)}){history.apply(it);selected=it.tracks()[index].id();changed();message("Channels split into separate waveforms • Choose Recombine Left + Right to join them")}
    }
    private fun combine(ids:List<String>,name:String){
        val p=history.current();job("Combining chosen waveforms",{p.combineChannels(ids,name).also(::preparePeaks)}){next->val previous=p.tracks().map{it.id()}.toSet();history.apply(next);selected=next.tracks().first{it.id() !in previous}.id();changed();message("Chosen waveforms combined • Other waveforms preserved")}
    }
    private fun stereoDialog(){
        val mono=monoTracks();require(mono.size>=2){"Split stereo to Left/Right, or add two mono waveforms"}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(8))}
        box.addView(label("Choose the left and right waveforms. Their start times, gain and mute/solo choices are rendered into the new stereo waveform. Other tracks stay as they are."))
        fun side(title:String,index:Int):Spinner{box.addView(label(title));return Spinner(this).apply{adapter=ArrayAdapter(this@InfiniWaveActivity,android.R.layout.simple_spinner_dropdown_item,mono.map{it.name()});setSelection(index);box.addView(this)}}
        val selectedIndex=mono.indexOfFirst{it.id()==selected}.coerceAtLeast(0);val leftIndex=if(mono[selectedIndex].route()==1&&selectedIndex>0)selectedIndex-1 else selectedIndex
        val left=side("Left channel",leftIndex);val right=side("Right channel",if(leftIndex+1<mono.size)leftIndex+1 else 0)
        validatedDialog("Recombine Left + Right",box){require(left.selectedItemPosition!=right.selectedItemPosition){"Choose a different waveform for each side"};combine(listOf(mono[left.selectedItemPosition].id(),mono[right.selectedItemPosition].id()),"Stereo · ${mono[left.selectedItemPosition].name().substringBeforeLast(" · Left")}")}
    }
    private fun combineDialog(){
        val mono=monoTracks();val selectedIds=linkedSetOf<String>();selected?.takeIf{id->mono.any{it.id()==id}}?.let{selectedIds+=it}
        val dialog=AlertDialog.Builder(this).setTitle("Combine chosen mono waveforms").setMultiChoiceItems(mono.map{it.name()}.toTypedArray(),BooleanArray(mono.size){mono[it].id() in selectedIds}){_,index,checked->if(checked)selectedIds+=mono[index].id()else selectedIds-=mono[index].id()}.setNegativeButton("Cancel",null).setPositiveButton("Combine",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{try{require(selectedIds.size in 2..32){"Choose 2..32 mono waveforms"};combine(mono.filter{it.id() in selectedIds}.map{it.id()},"Combined channels");dialog.dismiss()}catch(e:Exception){Toast.makeText(this,e.message,Toast.LENGTH_LONG).show()}}};dialog.show()
    }
    private fun effectScope():Triple<WaveProject.Track,Int,Int>{
        val t=selectedAudio()
        if(to>from){val(a,b)=selectionFrames();require(b>a){"Selection misses this waveform. Select its sound or choose Select whole waveform."};return Triple(t,a,b)}
        require(t.audio().frames()>0){"This waveform is empty"};return Triple(t,t.offset(),t.offset()+t.audio().frames())
    }
    private fun effectCaption():String{val t=selectedTrack()?:return "Select a waveform first.";return if(to>from)"Applies to highlighted time %.3f–%.3f s on ${t.name()}.".format(from,to) else "Applies to the whole ${t.name()} waveform. Highlight a range to limit the effect."}
    private fun effectTools(sheet:WaveToolSheet)=with(sheet){
        val t=selectedTrack();val audio=t!=null&&t.profile()==null&&t.audio().frames()>0&&(to<=from||hasSelection())
        note(if(t?.profile()!=null)"Render this parameter profile to audio before applying sample effects." else effectCaption()+" Every effect can be undone.")
        action("Waveform operations ›","Invert polarity, reverse time and fades",audio){push("Waveform operations"){
            note(effectCaption())
            action("Invert polarity","Flip samples above/below zero; keep time order"){processAudio("Inverting polarity"){input,a,b->PcmEffects.invert(input.samples(),input.channels(),a,b)}}
            action("Reverse audio","Play the selected sound backwards"){editEffect(WaveProject.Edit.REVERSE)}
            action("Fade in"){editEffect(WaveProject.Edit.FADE_IN)}
            action("Fade out"){editEffect(WaveProject.Edit.FADE_OUT)}
        }}
        action("Normalize peaks…","Set peak level, link channels and optionally remove DC offset",audio){normalizationDialog()}
        action("Noise filtering ›","Hiss, rumble, mains hum and quiet-passage noise",audio){push("Noise filtering"){
            note(effectCaption())
            action("Low-pass · reduce hiss…","Reduce frequencies above the chosen cutoff"){filterDialog(PcmEffects.Filter.LOW_PASS,6000.0)}
            action("High-pass · reduce rumble…","Reduce frequencies below the chosen cutoff"){filterDialog(PcmEffects.Filter.HIGH_PASS,80.0)}
            action("50 Hz hum notch…","Narrow-band filter with adjustable frequency and width"){filterDialog(PcmEffects.Filter.NOTCH,50.0)}
            action("60 Hz hum notch…","Narrow-band filter with adjustable frequency and width"){filterDialog(PcmEffects.Filter.NOTCH,60.0)}
            action("Noise gate…","Reduce quiet background noise between louder sounds"){gateDialog()}
        }}
        action("Select whole waveform",enabled=t!=null){selectWhole()}
        if(t?.profile()!=null)action("Render profile to audio"){renderProfile()}
    }
    private fun processAudio(label:String,operation:(AudioBuffer,Int,Int)->FloatArray){
        val(t,a,b)=effectScope();val p=history.current()
        job(label,{p.process(t.id(),a,b){input,first,end->AudioBuffer(input.sampleRate(),input.channels(),operation(input,first,end))}.also(::preparePeaks)}){history.apply(it);changed();message("$label complete • Undo restores the original")}
    }
    private fun editEffect(operation:WaveProject.Edit){val(t,a,b)=effectScope();val p=history.current();job("Applying ${operation.name.lowercase().replace('_',' ')}",{p.edit(t.id(),a,b,operation).also(::preparePeaks)}){history.apply(it);changed()}}
    private inner class EffectSettings(val title:String){
        val body=LinearLayout(this@InfiniWaveActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(8))}
        private val values=linkedMapOf<String,EditText>();private val flags=linkedMapOf<String,CheckBox>()
        fun note(text:String){body.addView(label(text).apply{setTextColor(WaveUi.secondary);setPadding(0,dp(4),0,dp(12))})}
        fun number(key:String,title:String,value:Double){body.addView(label(title));values[key]=EditText(this@InfiniWaveActivity).apply{setText(value.toString());inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED;contentDescription=title;body.addView(this)}}
        fun flag(key:String,title:String,value:Boolean){flags[key]=CheckBox(this@InfiniWaveActivity).apply{text=title;isChecked=value;minHeight=dp(48);body.addView(this)}}
        fun n(key:String)=values.getValue(key).text.toString().toDoubleOrNull()?.takeIf{it.isFinite()}?:throw IllegalArgumentException("Enter a valid number for $key")
        fun b(key:String)=flags.getValue(key).isChecked
        fun show(action:EffectSettings.()->Unit){validatedDialog(title,ScrollView(this@InfiniWaveActivity).apply{addView(body)}){action(this)}}
    }
    private fun normalizationDialog(){
        EffectSettings("Normalize peaks").apply{
            note(effectCaption());number("peak","Target peak (dBFS, -60..0)",-1.0);flag("linked","Link channels to preserve stereo balance",true);flag("dc","Remove DC offset",false)
            show{val peak=n("peak");require(peak in -60.0..0.0){"Peak must be -60..0 dBFS"};val linked=b("linked");val dc=b("dc");processAudio("Normalizing peaks"){input,a,z->PcmEffects.normalize(input.samples(),input.channels(),a,z,peak,linked,dc)}}
        }
    }
    private fun filterDialog(type:PcmEffects.Filter,frequency:Double){
        val title=when(type){PcmEffects.Filter.LOW_PASS->"Low-pass / hiss filter";PcmEffects.Filter.HIGH_PASS->"High-pass / rumble filter";else->"Hum notch filter"}
        EffectSettings(title).apply{
            note(effectCaption());number("frequency",if(type==PcmEffects.Filter.NOTCH)"Hum frequency (Hz)" else "Cutoff frequency (Hz)",frequency)
            number("q",if(type==PcmEffects.Filter.NOTCH)"Q (larger = narrower notch, 0.1..30)" else "Q / resonance (0.1..30, 0.707 = smooth)",if(type==PcmEffects.Filter.NOTCH)10.0 else kotlin.math.sqrt(.5))
            show{val hz=n("frequency");val q=n("q");require(hz>0&&hz<WaveProject.RATE/2.0){"Frequency must be between 0 and 22050 Hz"};require(q in .1..30.0){"Q must be 0.1..30"};processAudio(title){input,a,z->PcmEffects.filter(input.samples(),input.channels(),input.sampleRate(),a,z,type,hz,q)}}
        }
    }
    private fun gateDialog(){
        EffectSettings("Noise gate").apply{
            note(effectCaption()+" The gate reduces audio below the threshold. Lower thresholds preserve softer sounds; this does not remove noise during louder passages.")
            number("threshold","Threshold (dBFS, -100..0)",-45.0);number("reduction","Noise reduction (dB, 0..100)",28.0)
            number("attack","Attack / opening time (ms, 0..500)",2.0);number("hold","Hold open (ms, 0..5000)",40.0);number("release","Release / closing time (ms, 1..5000)",100.0)
            show{val threshold=n("threshold");val reduction=n("reduction");val attack=n("attack");val hold=n("hold");val release=n("release")
                require(threshold in -100.0..0.0&&reduction in 0.0..100.0&&attack in 0.0..500.0&&hold in 0.0..5000.0&&release in 1.0..5000.0){"Check the threshold, reduction and envelope ranges"}
                processAudio("Filtering quiet noise"){input,a,z->PcmEffects.noiseGate(input.samples(),input.channels(),input.sampleRate(),a,z,threshold,reduction,attack,hold,release)}
            }
        }
    }
    private fun quickHelp(){AlertDialog.Builder(this).setTitle("Editing audio").setMessage("1. Tap a waveform header, then drag across the sound you want to remove.\n2. Press Delete selection above the waveforms. The gap closes in that waveform; Undo restores it.\n\nPinch with two fingers to zoom without changing the selection. View → Use drag to pan moves the timeline.\n\nChannels → Split Left / Right separates stereo into mono waveforms. Recombine Left + Right lets you choose the two sides.\n\nEffects contains polarity inversion, reverse, fades, normalization and noise filters. Effects use the highlighted time, or the whole waveform when no range is selected. Profiles must be rendered to audio for sample editing.\n\nTool dialogs have Back and Close buttons. Long-press toolbar icons for their names.").setPositiveButton("Done",null).show()}
    private fun moreMenu(anchor:View){
        PopupMenu(this,anchor).apply{
            listOf("New workspace","Open project…","Save project…","Import audio…","Load profile JSON…","Editing help","About / licenses").forEachIndexed{i,text->menu.add(0,i,i,text)}
            setOnMenuItemClickListener{item->runAction{dismissTools();when(item.itemId){0->newWorkspace();1->open(true);2->saveProject();3->open(false);4->openProfile();5->quickHelp();6->about()}};true};show()
        }
    }
    private fun trackSettings(){
        val t=selected?.let{history.current().track(it)}?:run{message("Select a track");return}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,12,24,12)}
        fun field(label:String,text:String,numeric:Boolean=true):EditText{box.addView(TextView(this).apply{this.text=label});return EditText(this).apply{setText(text);if(numeric)inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED;box.addView(this)}}
        val name=field("Track name",t.name(),false);val offset=field("Start time (seconds)",(t.offset().toDouble()/WaveProject.RATE).toString())
        val gain=field("Gain (0..8)",t.gain().toString());val pan=field("Pan (-1 left .. 1 right)",t.pan().toString())
        val route=field("Output channel (0 = normal stereo / preserve channels, 1..32 = mono to that channel)",(t.route()+1).toString())
        validatedDialog("Track settings",ScrollView(this).apply{addView(box)}){
            val time=offset.text.toString().toDouble();require(time.isFinite()&&time in 0.0..3600.0){"Start time must be 0..3600 seconds"}
            val outputChannel=route.text.toString().toInt();require(outputChannel in 0..32)
            history.apply(history.current().replace(t.settings(name.text.toString().take(200),(time*WaveProject.RATE).roundToInt(),gain.text.toString().toDouble(),pan.text.toString().toDouble(),t.mute(),t.solo(),outputChannel-1)));changed()
        }
    }
    private fun prepareNewDocument(p:WaveProject){job("Preparing mixed waveform",{preparePeaks(p);p}){history.apply(it);changed();fitView();refreshWaves()}}
    private fun showGenerator(profile:RenderProfile?=null,target:WaveProject.Track?=null){
        try{GeneratorDialog(this,bank,bankSample,profile){name,settings,render->
            val offset=(from*WaveProject.RATE).roundToInt()
            job("Rendering $name",{render().also{peakCache[it]=WaveformView.Peaks.of(it)}}){audio->
                val p=when{
                    target!=null->history.current().replace(target.withProfile(settings,audio))
                    settings!=null->history.current().addProfile(name,settings,audio,offset)
                    else->history.current().add(name,audio,offset)
                }
                history.apply(p);selected=target?.id()?:p.tracks().last().id();changed();fitView();refreshWaves()
            }
        }.show()}catch(e:Exception){message(e.message?:"Invalid profile")}
    }
    private fun editProfile(){val t=selected?.let{history.current().track(it)};val profile=t?.profile()?:run{message("Select a rendering profile");return};showGenerator(profile,t)}
    private fun saveProfile(){
        val profile=selected?.let{history.current().track(it).profile()}?:run{message("Select a rendering profile");return}
        val file=File.createTempFile("wave-profile-",".json",cacheDir)
        job("Saving profile JSON",{try{file.outputStream().use{ProfileFile.write(profile,it)};file}catch(e:Throwable){file.delete();throw e}}){save(it,"infiniwave-${profile.engine().name.lowercase()}.iwprofile.json","application/json")}
    }
    private fun openProfile(){
        pickerPending=true;refresh()
        try{InfiniSelect.open(this,OpenRequest(clientId="infiniwave",displayName="InfiniWave profile",mimeTypes=listOf("*/*"),allowMultiple=false,supportedExtensions=listOf("json")),REQUEST_PROFILE)}catch(e:Exception){pickerPending=false;refresh();message(e.message?:"Picker unavailable")}
    }
    private fun addAudio(name:String,audio:AudioBuffer,offset:Int){
        stop();val p=history.current().add(name,audio,offset);history.apply(p);selected=p.tracks().last().id();changed();fitView();refreshWaves()
    }
    private fun extractSample(){
        val sf=bank?:run{message("Import an SF2 SoundFont first");return}
        AlertDialog.Builder(this).setTitle("Extract original SoundFont sample").setItems(sf.samples().map{it.name()}.toTypedArray()){_,index->
            bankSample=index;val offset=(from*WaveProject.RATE).roundToInt()
            job("Extracting sample",{val original=sf.extract(index);resample(original).also{peakCache[it]=WaveformView.Peaks.of(it)}}){addAudio(sf.samples()[index].name(),it,offset)}
        }.show()
    }
    private fun resample(audio:AudioBuffer):AudioBuffer{
        if(audio.sampleRate()==WaveProject.RATE)return audio
        val frames=ceil(audio.frames().toDouble()*WaveProject.RATE/audio.sampleRate()).toInt();require(frames.toLong()*audio.channels()<=AudioBuffer.MAX_SAMPLES){"Resampled sample exceeds PCM budget"}
        val out=FloatArray(frames*audio.channels())
        repeat(frames){f->val p=f.toDouble()*audio.sampleRate()/WaveProject.RATE;val a=min(audio.frames()-1,p.toInt());val b=min(audio.frames()-1,a+1);repeat(audio.channels()){ch->out[f*audio.channels()+ch]=(audio.sample(a,ch)*(1-(p-a))+audio.sample(b,ch)*(p-a)).toFloat()}}
        return AudioBuffer(WaveProject.RATE,audio.channels(),out)
    }
    private fun open(project:Boolean){
        if(busy||pickerPending||savePending)return;pickerPending=true;refresh()
        try{InfiniSelect.open(this,OpenRequest(clientId="infiniwave",displayName="InfiniWave",mimeTypes=listOf("*/*"),allowMultiple=!project,supportedExtensions=if(project)listOf("iwave")else listOf("wav","wave","mp3","flac","aac","m4a","sf2","iwave")),if(project)REQUEST_PROJECT else REQUEST_IMPORT)}catch(e:Exception){pickerPending=false;refresh();message(e.message?:"Picker unavailable")}
    }
    @Deprecated("Platform picker interoperability")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==REQUEST_SAVE){savePending=false;pendingFile?.delete();pendingFile=null;refresh();message(if(resultCode==RESULT_OK&&data?.getBooleanExtra(FileSelectionContract.EXTRA_SAVE_SUCCESS,false)==true)"Saved"else data?.getStringExtra(FileSelectionContract.EXTRA_SAVE_ERROR)?:"Save cancelled");return}
        if(requestCode!=REQUEST_IMPORT&&requestCode!=REQUEST_PROJECT&&requestCode!=REQUEST_PROFILE)return
        pickerPending=false;refresh();if(resultCode!=RESULT_OK||data==null)return
        if(requestCode==REQUEST_PROFILE){
            job("Loading profile JSON",{
                StorageAccess.persist(this,data);val item=InfiniSelect.readResult(this,data).items.firstOrNull()?:error("Select a profile JSON file")
                val source=InfiniSelect.requireSelectedSource(this,item.contentUri,item.authoritativeUri.toString(),item.name,item.mimeType,item.sizeBytes)
                StorageAccess.openRead(this,source.contentUri).use(ProfileFile::read)
            }){showGenerator(it)};return
        }
        val p=history.current();val offset=(from*WaveProject.RATE).roundToInt()
        job("Importing selected resources",{
            StorageAccess.persist(this,data);val result=InfiniSelect.readResult(this,data)
            var document=p;var importedBank:SoundFontBank?=null
            for(item in result.items){
                val source=InfiniSelect.requireSelectedSource(this,item.contentUri,item.authoritativeUri.toString(),item.name,item.mimeType,item.sizeBytes)
                val temp=stage(source.contentUri,if(item.name.endsWith(".iwave",true))160L*1024*1024 else 64L*1024*1024)
                try{when(item.name.substringAfterLast('.').lowercase()){
                    "sf2"->importedBank=temp.inputStream().use(SoundFontBank::read)
                    "iwave"->document=temp.inputStream().buffered().use(ProjectFile::read)
                    else->document=document.add(item.name,AudioIO.decode(temp,item.name),offset)
                }}finally{temp.delete()}
            }
            preparePeaks(document);Pair(document,importedBank)
        }){(document,sf)->if(sf!=null){bank=sf;bankSample=0;message("SoundFont loaded: ${sf.samples().size} samples. Use Generate → Extract original SoundFont sample.")};history.apply(document);changed();fitView();refreshWaves()}
    }
    private fun receiveIncoming(incoming:Intent){
        val uri=incoming.data?:return;val name=incoming.getStringExtra(FileSelectionContract.EXTRA_RESULT_NAME)?:uri.lastPathSegment?.substringAfterLast('/')?:"audio.wav"
        if(name.endsWith(".json",true)){
            job("Loading profile JSON",{val source=InfiniSelect.requireSelectedSource(this,uri,incoming.getStringExtra(FileSelectionContract.EXTRA_RESULT_AUTHORITATIVE_URI),name,incoming.type,null);StorageAccess.openRead(this,source.contentUri).use(ProfileFile::read)}){showGenerator(it)};return
        }
        val reference=incoming.getStringExtra(FileSelectionContract.EXTRA_RESULT_AUTHORITATIVE_URI)
        val p=history.current();job("Importing $name",{
            val source=InfiniSelect.requireSelectedSource(this,uri,reference,name,incoming.type,null);val tmp=stage(source.contentUri,if(name.endsWith(".iwave",true))160L*1024*1024 else 64L*1024*1024)
            try{when(name.substringAfterLast('.').lowercase()){
                "iwave"->Pair(tmp.inputStream().buffered().use(ProjectFile::read),null)
                "sf2"->Pair(p,tmp.inputStream().use(SoundFontBank::read))
                else->Pair(p.add(name,AudioIO.decode(tmp,name),0),null)
            }.also{preparePeaks(it.first)}}finally{tmp.delete()}
        }){(document,sf)->sf?.let{bank=it};history.apply(document);changed();fitView();refreshWaves()}
    }
    private fun stage(uri:Uri,maxBytes:Long):File{
        val f=File.createTempFile("wave-import-",".bin",cacheDir)
        try{StorageAccess.openRead(this,uri).use{input->f.outputStream().buffered().use{out->val block=ByteArray(8192);var total=0L;while(true){if(Thread.currentThread().isInterrupted)throw InterruptedIOException("Import cancelled");val n=input.read(block);if(n<0)break;total+=n;require(total<=maxBytes){"Input exceeds its import budget"};out.write(block,0,n)}}};return f}catch(e:Throwable){f.delete();throw e}
    }
    private fun renderRange():Pair<Int,Int>{val p=history.current();val a=if(to>from)(from*WaveProject.RATE).roundToInt()else 0;val b=if(to>from)(to*WaveProject.RATE).roundToInt()else p.endFrame();require(b>a){"Add audio or select a nonempty range"};return Pair(a,b)}
    private fun exportMenu(){AlertDialog.Builder(this).setTitle("Export audible tracks / selected time").setItems(arrayOf("Mono WAV","Stereo WAV","Multichannel WAV")){_,choice->
        if(choice<2)exportWave(choice+1)else{
            val field=EditText(this).apply{inputType=InputType.TYPE_CLASS_NUMBER;setText(max(2,history.current().tracks().maxOfOrNull{max(it.audio().channels(),it.route()+1)}?:2).toString())}
            validatedDialog("Output channels (1..32)",field){exportWave(field.text.toString().toInt())}
        }
    }.show()}
    private fun exportWave(channels:Int){
        try{require(channels in 1..32);val (a,b)=renderRange();val p=history.current();val f=File.createTempFile("wave-export-",".wav",cacheDir)
            job("Exporting WAV",{try{AudioIO.writeWave(f,p.mix(a,b,channels));f}catch(e:Throwable){f.delete();throw e}}){save(it,"infiniwave-mix.wav","audio/wav")}
        }catch(e:Exception){message(e.message?:"Export failed")}
    }
    private fun newWorkspace(){AlertDialog.Builder(this).setMessage("Start a new workspace? Save your project first if you want to keep it.").setNegativeButton("Cancel",null).setPositiveButton("New"){_,_->stop();history.apply(WaveProject());selected=null;from=0.0;to=0.0;fitView();changed()}.show()}
    private fun saveProject(){val p=history.current();val f=File.createTempFile("wave-project-",".iwave",cacheDir);job("Saving project",{try{f.outputStream().buffered().use{ProjectFile.write(p,it)};f}catch(e:Throwable){f.delete();throw e}}){save(it,"infiniwave-project.iwave","application/vnd.infiniforge.wave-project")}}
    private fun about(){AlertDialog.Builder(this).setTitle("InfiniWave 0.3.1").setMessage("Multitrack editing, waveform effects and parameter-based synthesis. Original app/library code is MIT. Native cores: Nuked-OPL3 (LGPL-2.1+), chips m6581 (zlib), SAA1099 (BSD-3-Clause).\n\nSource and notices: github.com/infiniforge/infiniwave and github.com/infiniforge/libinfinisynth\n\nImported files stay untouched. Noise filters and gating are adjustable; learned-profile spectral denoising is not implemented.").setPositiveButton("Done",null).show()}
    private fun save(file:File,name:String,mime:String){
        try{val uri=InfiniSelect.createFileDocument(this,file,name,mime);pendingFile=file;savePending=true
            InfiniSelect.save(this,SaveRequest(clientId="infiniwave",displayName="InfiniWave",fileName=name,mimeType=mime,sourceUri=uri,sourceSizeBytes=file.length(),initialDirectory=directory),REQUEST_SAVE);refresh()
        }catch(e:Exception){savePending=false;pendingFile=null;file.delete();refresh();message(e.message?:"Save picker unavailable")}
    }
    private fun preparePeaks(p:WaveProject){p.tracks().forEach{t->if(peakCache[t.audio()]==null)peakCache[t.audio()]=WaveformView.Peaks.of(t.audio())}}
    private fun changed(){stop();refresh();val p=history.current();worker.submit{try{val temp=File(filesDir,"workspace.next");temp.outputStream().buffered().use{ProjectFile.write(p,it)};Files.move(temp.toPath(),recovery.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)}catch(e:Exception){runOnUiThread{if(!isDestroyed)message("Workspace backup failed: ${e.message}")}}}}
    private fun cancelJob(){jobGeneration++;task?.cancel(true);task=null;busy=false;refresh();message("Operation cancelled")}
    private fun <T> job(label:String,work:()->T,done:(T)->Unit){
        if(busy)return;stop();busy=true;refresh();status.text=label
        val generation=++jobGeneration
        task=worker.submit{
            val result=try{Result.success(work())}catch(e:Exception){Result.failure(e)}catch(e:LinkageError){Result.failure(IllegalStateException("Synthesis backend could not load: ${e.message}"))}catch(e:OutOfMemoryError){Result.failure(IllegalStateException("Not enough memory; remove audio or shorten the selected range"))}
            runOnUiThread{if(isDestroyed||generation!=jobGeneration){result.getOrNull()?.let{if(it is File)it.delete()};return@runOnUiThread};busy=false;refresh();result.fold(onSuccess={try{done(it);if(status.text.toString()==label)message("Ready")}catch(e:Exception){message(e.message?:"Operation failed")}},onFailure={message(it.message?:"Operation failed")})}
        }
    }
    private fun play(){
        if(playing){stop();return}
        try{val (a,b)=renderRange();val p=history.current()
            // Monitor named multichannel routes through the corresponding stereo side.
            val monitor=WaveProject(p.tracks().map{t->if(t.route()>1)t.settings(t.name(),t.offset(),t.gain(),t.pan(),t.mute(),t.solo(),t.route()%2)else t})
            job("Preparing playback",{monitor.mix(a,b,2)}){audio->startPlayback(audio,a.toDouble()/WaveProject.RATE)}
        }catch(e:Exception){message(e.message?:"Playback unavailable")}
    }
    private fun startPlayback(audio:AudioBuffer,start:Double){
        if(!resumed)return
        val session=Playback();playback=session;updateControls()
        session.thread=Thread({
            var player:AudioTrack?=null
            try{
                player=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(WaveProject.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(max(32768,AudioTrack.getMinBufferSize(WaveProject.RATE,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_FLOAT))).build()
                session.player=player;check(player.state==AudioTrack.STATE_INITIALIZED){"Audio output unavailable"}
                if(!session.active)return@Thread
                player.play();val chunk=FloatArray(4096*2);var frame=0
                while(session.active&&frame<audio.frames()){
                    val count=min(4096,audio.frames()-frame);repeat(count){f->repeat(2){ch->chunk[f*2+ch]=audio.sample(frame+f,ch).coerceIn(-1f,1f)}}
                    var offset=0;while(session.active&&offset<count*2){val n=player.write(chunk,offset,count*2-offset,AudioTrack.WRITE_NON_BLOCKING);check(n>=0){"Audio output write failed: $n"};if(n==0)Thread.sleep(5)else offset+=n}
                    frame+=offset/2;session.frame=player.playbackHeadPosition
                }
                while(session.active&&player.playbackHeadPosition<audio.frames()){session.frame=player.playbackHeadPosition;Thread.sleep(10)}
            }catch(e:Exception){if(session.active)runOnUiThread{if(!isDestroyed&&playback===session)message(e.message?:"Playback stopped")}}
            finally{
                session.active=false;runCatching{player?.stop()};player?.release()
                runOnUiThread{if(playback===session){playback=null;updateControls();waves.forEach{it.playSeconds=null;it.invalidate()}}}
            }
        },"InfiniWavePlayback").apply{start()}
        handler.post(object:Runnable{override fun run(){if(playback!==session||!session.active||isDestroyed)return;waves.forEach{it.playSeconds=start+session.frame.toDouble()/WaveProject.RATE;it.invalidate()};handler.postDelayed(this,40)}})
    }
    private fun stop(){
        val previous=playback;playback=null;previous?.active=false
        runCatching{previous?.player?.pause();previous?.player?.flush()};previous?.thread?.interrupt()
        handler.removeCallbacksAndMessages(null);waves.forEach{it.playSeconds=null;it.invalidate()};if(::stopButton.isInitialized)updateControls()
    }
    private fun message(text:String){if(!isDestroyed)status.text=text}
    override fun onResume(){super.onResume();resumed=true}
    override fun onPause(){resumed=false;stop();super.onPause()}
    override fun onDestroy(){dismissTools();stop();task?.cancel(true);worker.shutdown();super.onDestroy()}
    companion object{private const val REQUEST_IMPORT=7201;private const val REQUEST_SAVE=7202;private const val REQUEST_PROJECT=7203;private const val REQUEST_PROFILE=7204}
}

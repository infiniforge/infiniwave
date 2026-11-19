package com.infiniwave

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
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
    private val controls=mutableListOf<Button>()
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
        buildUi()
        pendingFile=state?.getString("pendingFile")?.let(::File)?.takeIf(File::isFile)
        savePending=state?.getBoolean("savePending")?:false
        pickerPending=state?.getBoolean("pickerPending")?:false
        directory=state?.getString("directory")?.let(Uri::parse)
        selected=state?.getString("selected");from=state?.getDouble("from")?:0.0;to=state?.getDouble("to")?:0.0
        fun incoming(){if(intent?.action==Intent.ACTION_VIEW&&intent.data!=null)receiveIncoming(intent)else refresh()}
        if(recovery.isFile){job("Restoring workspace",{
            val p=recovery.inputStream().buffered().use(ProjectFile::read);preparePeaks(p);p
        }){p->history.reset(p);fitView();incoming()}}
        else incoming()
    }
    override fun onSaveInstanceState(out:Bundle){
        out.putString("pendingFile",pendingFile?.path);out.putBoolean("savePending",savePending);out.putBoolean("pickerPending",pickerPending)
        out.putString("directory",directory?.toString());out.putString("selected",selected);out.putDouble("from",from);out.putDouble("to",to);super.onSaveInstanceState(out)
    }
    override fun onNewIntent(incoming:Intent){super.onNewIntent(incoming);setIntent(incoming);if(!busy&&!savePending)receiveIncoming(incoming)}
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun buildUi(){
        val outer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(4));setBackgroundColor(0xff101e28.toInt())}
        outer.addView(TextView(this).apply{text="InfiniWave";textSize=25f;setTextColor(0xffe5f7f4.toInt())})
        outer.addView(TextView(this).apply{text="Multitrack audio editor  •  synthesis workspace";textSize=13f;setTextColor(0xff9bb7c3.toInt())})
        fun toolbar(vararg items:Pair<String,()->Unit>){
            val line=LinearLayout(this);items.forEach{(label,action)->val b=Button(this).apply{text=label;textSize=12f;setOnClickListener{if(!busy&&!savePending&&!pickerPending)action()}};controls+=b;line.addView(b)}
            outer.addView(HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(line)})
        }
        toolbar("Import" to {open(false)},"Generate" to {showGenerator()},"Play" to {play()},"Stop" to {stop()},"Export" to {exportMenu()},"Project" to {projectMenu()})
        toolbar("Undo" to {history.undo();changed()},"Redo" to {history.redo();changed()},"Edit" to {editMenu()},"Tracks" to {trackMenu()},"Zoom +" to {zoom(.5)},"Zoom −" to {zoom(2.0)},"←" to {viewFrom=max(0.0,viewFrom-viewSeconds/2);refreshWaves()},"→" to {viewFrom=min(3600.0-viewSeconds,viewFrom+viewSeconds/2).coerceAtLeast(0.0);refreshWaves()},"Fit" to {fitView();refreshWaves()},"Range" to {rangeDialog()})
        selectionLabel=TextView(this).apply{textSize=14f;setTextColor(0xffffc777.toInt());setPadding(0,dp(6),0,dp(8))};outer.addView(selectionLabel)
        rows=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        outer.addView(ScrollView(this).apply{isFillViewport=true;addView(rows)},LinearLayout.LayoutParams(-1,0,1f))
        val footer=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        status=TextView(this).apply{text="Import audio, or generate your first waveform.";setTextColor(0xffa5bdc8.toInt());textSize=13f}
        footer.addView(status,LinearLayout.LayoutParams(0,-2,1f))
        footer.addView(Button(this).apply{text="Cancel";setOnClickListener{cancelJob();stop()}})
        outer.addView(footer);setContentView(outer)
    }
    private fun refresh(){
        if(isDestroyed)return
        val p=history.current();if(p.tracks().none{it.id()==selected})selected=p.tracks().firstOrNull()?.id()
        rows.removeAllViews();waves.clear()
        if(p.tracks().isEmpty())rows.addView(TextView(this).apply{text="Your audio workspace\n\nImport several files, generate a sound, or pull a sample from a SoundFont. Each becomes a separate waveform track.\n\nDrag across a waveform to select time. Pinch to zoom. Open Tracks to mix or split channels.";setTextColor(0xff98b4c0.toInt());textSize=18f;setPadding(dp(16),dp(30),dp(16),dp(20))})
        p.tracks().forEach{track->
            val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(10),0,0)}
            head.addView(Button(this).apply{text="${track.name()}  ·  ${track.profile()?.engine()?.name?.plus(" profile")?:"audio"}  ·  ${track.audio().channels()} ch";textSize=13f;setOnClickListener{if(!busy){selected=track.id();refresh()}}},LinearLayout.LayoutParams(0,-2,1f))
            fun toggle(label:String,value:Boolean,change:(Boolean)->WaveProject.Track){head.addView(CheckBox(this).apply{text=label;isChecked=value;setTextColor(0xffb4cbd3.toInt());isEnabled=!busy;setOnCheckedChangeListener{_,checked->if(!busy){stop();history.apply(history.current().replace(change(checked)));changed()}}})}
            toggle("M",track.mute()){track.settings(track.name(),track.offset(),track.gain(),track.pan(),it,track.solo(),track.route())}
            toggle("S",track.solo()){track.settings(track.name(),track.offset(),track.gain(),track.pan(),track.mute(),it,track.route())}
            rows.addView(head)
            rows.addView(TextView(this).apply{text="%.2fs start  ·  %.2fs audio  ·  %.1f dB  ·  %s".format(track.offset().toDouble()/WaveProject.RATE,track.audio().seconds(),20*log10(max(.00001,track.gain())),if(track.route()<0)"pan %.2f".format(track.pan()) else "output ${track.route()+1}");setTextColor(0xff8fadb9.toInt());textSize=12f})
            val wave=WaveformView(this).apply{
                this.track=track;peaks=peakCache[track.audio()];trackSelected=track.id()==this@InfiniWaveActivity.selected
                onSelection={a,b->if(!busy&&!playing){this@InfiniWaveActivity.selected=track.id();from=a;to=b;refreshWaves()}}
                onZoom={start,span->viewFrom=start;viewSeconds=span;refreshWaves()}
                contentDescription="${track.name()} waveform, ${track.audio().channels()} channels. Drag to select a time range."
            }
            rows.addView(wave,LinearLayout.LayoutParams(-1,dp(110+min(6,track.audio().channels())*24)) );waves+=wave
        }
        refreshWaves();controls.forEach{it.isEnabled=!busy&&!savePending&&!pickerPending}
    }
    private fun refreshWaves(){
        selectionLabel.text="Selection %.3f–%.3f s  ·  View %.2f–%.2f s  ·  %d tracks".format(from,to,viewFrom,viewFrom+viewSeconds,history.current().tracks().size)
        waves.forEach{it.viewFrom=viewFrom;it.viewSeconds=viewSeconds;it.selectionFrom=from;it.selectionTo=to;it.trackSelected=it.track.id()==selected;it.invalidate()}
    }
    private fun fitView(){viewFrom=0.0;viewSeconds=max(5.0,history.current().endFrame().toDouble()/WaveProject.RATE*1.03)}
    private fun zoom(factor:Double){val center=if(to>from)(from+to)/2 else viewFrom+viewSeconds/2;viewSeconds=(viewSeconds*factor).coerceIn(.02,3600.0);viewFrom=max(0.0,center-viewSeconds/2);refreshWaves()}
    private fun rangeDialog(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,12,24,12)}
        fun field(label:String,value:Double):EditText{box.addView(TextView(this).apply{text=label});return EditText(this).apply{inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;setText(value.toString());box.addView(this)}}
        val a=field("Start seconds",from);val b=field("End seconds",to)
        validatedDialog("Select time",box){val x=a.text.toString().toDouble();val y=b.text.toString().toDouble();require(x.isFinite()&&y.isFinite()&&x>=0&&y>=x&&y<=3600){"Range must be between 0 and 3600 seconds"};from=x;to=y;refreshWaves()}
    }
    private fun validatedDialog(title:String,view:android.view.View,action:()->Unit){
        val d=AlertDialog.Builder(this).setTitle(title).setView(view).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
        d.setOnShowListener{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{try{action();d.dismiss()}catch(e:Exception){message(e.message?:"Invalid settings")}}};d.show()
    }
    private fun editMenu(){
        val current=selected?.let{history.current().track(it)};if(current?.profile()!=null){editProfile();return}
        val operations=WaveProject.Edit.values()
        val labels=listOf("Copy selection","Cut selection","Paste at cursor","Paste as new track","Select whole track")+operations.map{it.name.lowercase().replace('_',' ')}
        AlertDialog.Builder(this).setTitle("Edit waveform").setItems(labels.toTypedArray()){_,which->
            try{
                val p=history.current();val id=selected
                val a=(from*WaveProject.RATE).roundToInt();val b=(to*WaveProject.RATE).roundToInt()
                when(which){
                    0,1->{require(id!=null){"Select a track"};job("Copying selection",{
                        val clip=p.copyRange(id,a,b);val updated=if(which==1)p.edit(id,a,b,WaveProject.Edit.DELETE)else p
                        preparePeaks(updated);Pair(clip,updated)
                    }){(clip,updated)->clipboard=clip;history.apply(updated);changed();message("Selection copied")}}
                    2->{require(id!=null){"Select a track"};val clip=clipboard?:error("Copy a selection first");job("Pasting audio",{p.paste(id,a,clip).also(::preparePeaks)}){history.apply(it);changed()}}
                    3->{val clip=clipboard?:error("Copy a selection first");job("Preparing pasted waveform",{clip.also{peakCache[it]=WaveformView.Peaks.of(it)}}){addAudio("Pasted waveform",it,a)}}
                    4->{require(id!=null){"Select a track"};val t=p.track(id);from=t.offset().toDouble()/WaveProject.RATE;to=from+t.audio().seconds();refreshWaves()}
                    else->{require(id!=null){"Select a track"};job("Editing waveform",{p.edit(id,a,b,operations[which-5]).also(::preparePeaks)}){history.apply(it);changed()}}
                }
            }catch(e:Exception){message(e.message?:"Edit failed")}
        }.show()
    }
    private fun trackMenu(){AlertDialog.Builder(this).setTitle("Track operations").setItems(arrayOf("Track settings / channel routing","Duplicate selected waveform","Split selected track into mono channels","Remove selected track","Mix audible tracks into one stereo waveform","Combine mono tracks as separate channels","Extract raw sample from SoundFont","Edit profile parameters","Render selected profile to audio","Save selected profile JSON","Load profile JSON")){_,which->
        val p=history.current();val id=selected
        try{when(which){
            0->trackSettings()
            1->{require(id!=null){"Select a track"};history.apply(p.duplicate(id));changed()}
            2->{require(id!=null){"Select a track"};job("Splitting channels",{p.splitChannels(id).also(::preparePeaks)}){history.apply(it);changed()}}
            3->{require(id!=null){"Select a track"};history.apply(p.remove(id));changed()}
            4->{val (a,b)=renderRange();job("Mixing tracks",{p.mix(a,b,2)}){audio->val mixed=WaveProject().add("Stereo mix",audio,a);prepareNewDocument(mixed)}}
            5->job("Combining channels",{p.combineAsChannels().also(::preparePeaks)}){history.apply(it);changed();fitView();refreshWaves()}
            6->extractSample()
            7->editProfile()
            8->{require(id!=null&&p.track(id).profile()!=null){"Select a rendering profile"};history.apply(p.renderToAudio(id));changed()}
            9->saveProfile()
            10->openProfile()
        }}catch(e:Exception){message(e.message?:"Track operation failed")}
    }.show()}
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
        }){(document,sf)->if(sf!=null){bank=sf;bankSample=0;message("SoundFont loaded: ${sf.samples().size} samples. Use Generate or Tracks → Extract sample.")};history.apply(document);changed();fitView();refreshWaves()}
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
    private fun projectMenu(){AlertDialog.Builder(this).setTitle("Project").setItems(arrayOf("New workspace","Open project","Save project","About / licenses")){_,which->when(which){
        0->AlertDialog.Builder(this).setMessage("Start a new workspace? Save your project first if you want to keep it.").setNegativeButton("Cancel",null).setPositiveButton("New"){_,_->stop();history.apply(WaveProject());changed();fitView()}.show()
        1->open(true)
        2->{val p=history.current();val f=File.createTempFile("wave-project-",".iwave",cacheDir);job("Saving project",{try{f.outputStream().buffered().use{ProjectFile.write(p,it)};f}catch(e:Throwable){f.delete();throw e}}){save(it,"infiniwave-project.iwave","application/vnd.infiniforge.wave-project")}}
        3->AlertDialog.Builder(this).setTitle("InfiniWave 0.2.0").setMessage("Multitrack editing and sound generation. libinfinisynth supplies FM, SoundFont sample extraction, Nuked-OPL3 (LGPL-2.1+) chips m6581 (zlib), and libvgm SAA1099 (BSD-3-Clause). Original app/library code is MIT.\n\nSource and complete notices: github.com/infiniforge/infiniwave and github.com/infiniforge/libinfinisynth\n\nImported audio is edited in a separate workspace. Export creates a new file. Chip rendering is offline; SID models 6581, not every analog revision.").setPositiveButton("OK",null).show()
    }}.show()}
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
        val session=Playback();playback=session
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
                runOnUiThread{if(playback===session){playback=null;waves.forEach{it.playSeconds=null;it.invalidate()}}}
            }
        },"InfiniWavePlayback").apply{start()}
        handler.post(object:Runnable{override fun run(){if(playback!==session||!session.active||isDestroyed)return;waves.forEach{it.playSeconds=start+session.frame.toDouble()/WaveProject.RATE;it.invalidate()};handler.postDelayed(this,40)}})
    }
    private fun stop(){
        val previous=playback;playback=null;previous?.active=false
        runCatching{previous?.player?.pause();previous?.player?.flush()};previous?.thread?.interrupt()
        handler.removeCallbacksAndMessages(null);waves.forEach{it.playSeconds=null;it.invalidate()}
    }
    private fun message(text:String){if(!isDestroyed)status.text=text}
    override fun onResume(){super.onResume();resumed=true}
    override fun onPause(){resumed=false;stop();super.onPause()}
    override fun onDestroy(){stop();task?.cancel(true);worker.shutdown();super.onDestroy()}
    companion object{private const val REQUEST_IMPORT=7201;private const val REQUEST_SAVE=7202;private const val REQUEST_PROJECT=7203;private const val REQUEST_PROFILE=7204}
}

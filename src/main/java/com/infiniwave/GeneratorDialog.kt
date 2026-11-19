package com.infiniwave

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.*
import io.github.gretchycat.infinisynth.*
import com.infiniwave.editor.RenderProfile
import kotlin.math.*

/** Sound-design controls map directly to public library patch parameters. */
class GeneratorDialog(private val activity:Activity,private val bank:SoundFontBank?,private val sampleIndex:Int,
    private val initial:RenderProfile?=null,
    private val generate:(String,RenderProfile?,()->AudioBuffer)->Unit) {
    private class Form(val activity:Activity,val title:String,val initial:Map<String,String>) {
        val body=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;setPadding(24,16,24,16)}
        private val numbers=linkedMapOf<String,EditText>()
        private val flags=linkedMapOf<String,CheckBox>()
        private val choices=linkedMapOf<String,Spinner>()
        fun heading(text:String){body.addView(TextView(activity).apply {this.text=text;textSize=18f;setPadding(0,24,0,8)})}
        fun number(key:String,label:String,value:Double):Form {body.addView(TextView(activity).apply{text=label});numbers[key]=EditText(activity).apply {setText(initial[key]?:value.toString());contentDescription=label;inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED;body.addView(this)};return this}
        fun flag(key:String,label:String,value:Boolean):Form {flags[key]=CheckBox(activity).apply{text=label;isChecked=initial[key]?.toBooleanStrict()?:value;body.addView(this)};return this}
        fun choice(key:String,label:String,values:List<String>,selected:Int=0){body.addView(TextView(activity).apply{text=label});choices[key]=Spinner(activity).apply{adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,values);setSelection((initial[key]?.toIntOrNull()?:selected).also{require(it in values.indices){"Invalid choice for $key"}});body.addView(this)}}
        fun n(key:String)=numbers.getValue(key).text.toString().toDoubleOrNull()?.takeIf(Double::isFinite)?:throw IllegalArgumentException("Enter a valid number for $key")
        fun i(key:String):Int {val v=n(key);require(v==floor(v)&&v>=Int.MIN_VALUE&&v<=Int.MAX_VALUE){"$key requires a whole number"};return v.toInt()}
        fun b(key:String)=flags.getValue(key).isChecked
        fun c(key:String)=choices.getValue(key).selectedItemPosition
        fun envelope(prefix:String){heading("Envelope · seconds / level");number(prefix+"delay","Delay",0.0);number(prefix+"attack","Attack",.01);number(prefix+"hold","Hold",0.0);number(prefix+"decay","Decay",.15);number(prefix+"sustain","Sustain (0..1)",.7);number(prefix+"release","Release",.3);number(prefix+"curve","Curve (0.1..10)",1.0)}
        fun env(p:String)=Envelope(n(p+"delay"),n(p+"attack"),n(p+"hold"),n(p+"decay"),n(p+"sustain"),n(p+"release"),n(p+"curve"))
        fun profile(engine:RenderProfile.Engine,operators:Int?=null):RenderProfile {
            val params=linkedMapOf<String,String>();numbers.forEach{(key,field)->n(key);params[key]=field.text.toString().trim()};flags.forEach{(key,field)->params[key]=field.isChecked.toString()};choices.forEach{(key,field)->params[key]=field.selectedItemPosition.toString()};operators?.let{params["operators"]=it.toString()};return RenderProfile(engine,params)
        }
        fun show(action:()->Unit){
            val dialog=AlertDialog.Builder(activity).setTitle(title).setView(ScrollView(activity).apply{addView(body)}).setNegativeButton("Cancel",null).setPositiveButton(if(initial.isEmpty())"Add track" else "Apply parameters",null).create()
            dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{try{action();dialog.dismiss()}catch(e:Exception){Toast.makeText(activity,e.message?:"Invalid synthesis settings",Toast.LENGTH_LONG).show()}}};dialog.show()
        }
    }
    private fun form(title:String)=Form(activity,title,initial?.parameters()?:emptyMap())
    fun show(){
        if(initial!=null){when(initial.engine()){
            RenderProfile.Engine.FM->fm(initial.parameters().getValue("operators").toInt())
            RenderProfile.Engine.OPL3->opl(initial.parameters().getValue("operators").toInt())
            RenderProfile.Engine.SID6581->sid()
            RenderProfile.Engine.CMS->cms()
        };return}
        AlertDialog.Builder(activity).setTitle("Generate a track").setItems(arrayOf("Oscillator / noise profile","FM profile","SoundFont sample waveform","OPL3 · 2-operator profile","OPL3 · 4-operator profile","SID 6581 profile","CMS / Game Blaster profile")){_,which->when(which){0->fm(1);1->chooseOperators();2->sample();3->opl(2);4->opl(4);5->sid();6->cms()}}.show()
    }
    private fun chooseOperators(){AlertDialog.Builder(activity).setTitle("FM operators").setItems(arrayOf("2 operators","4 operators","6 operators","8 operators")){_,n->fm(listOf(2,4,6,8)[n])}.show()}
    private fun fm(count:Int){
        val f=form(if(count==1)"Oscillator" else "$count-operator FM")
        f.number("frequency","Base frequency (Hz)",440.0).number("duration","Note length (seconds)",2.0).number("gain","Output gain",.6).number("pan","Pan (-1 left .. 1 right)",0.0).number("seed","Noise seed",1.0)
        for(i in 0 until count){
            f.heading("Operator ${i+1}");f.choice("wave$i","Waveform",FmSynth.Waveform.values().map{it.name.lowercase()})
            f.number("ratio$i","Frequency ratio",if(i==0)1.0 else 2.0).number("fixed$i","Fixed frequency Hz (0 = use ratio)",0.0)
                .number("detune$i","Detune (cents)",0.0).number("level$i","Operator level",1.0).number("phase$i","Initial phase (0..1)",0.0).number("pulse$i","Pulse width (0..1)",.5)
                .number("carrier$i","Output contribution",if(i==0)1.0 else 0.0)
            f.envelope("e$i")
        }
        if(count>1){f.heading("Modulation matrix · radians");for(dest in 0 until count)for(source in 0 until count)f.number("m$dest-$source","Operator ${source+1} → ${dest+1}",if(source==1&&dest==0)3.0 else 0.0)}
        f.show{
            val operators=(0 until count).map{i->FmSynth.Operator(f.n("ratio$i"),f.n("detune$i"),f.n("fixed$i"),f.n("level$i"),f.n("phase$i"),FmSynth.Waveform.values()[f.c("wave$i")],f.n("pulse$i"),f.env("e$i"))}
            val matrix=Array(count){dest->DoubleArray(count){source->if(count>1)f.n("m$dest-$source") else 0.0}}
            val patch=FmSynth.Patch(operators,matrix,DoubleArray(count){f.n("carrier$it")},f.n("gain"),f.n("pan"),f.n("seed").toLong());val frequency=f.n("frequency");val duration=f.n("duration")
            generate(if(count==1)"Oscillator" else "FM $count operators",f.profile(RenderProfile.Engine.FM,count)){FmSynth.render(patch,frequency,duration,44100)}
        }
    }
    private fun sample(){
        val soundFont=bank?:run{Toast.makeText(activity,"Import an SF2 SoundFont first",Toast.LENGTH_LONG).show();return}
        val f=form("SoundFont sample")
        f.choice("sample","Sample",soundFont.samples().map{it.name()},sampleIndex.coerceIn(0,soundFont.samples().lastIndex))
        f.number("pitch","Pitch shift (semitones)",0.0).number("duration","Note length (seconds)",2.0).number("gain","Output gain",1.0).number("pan","Pan (-1..1)",0.0)
        f.flag("loop","Use sample loop",false);f.envelope("sample")
        f.show{
            val index=f.c("sample");val meta=soundFont.samples()[index];val patch=SampleSynth.Patch(f.n("pitch"),f.n("gain"),f.n("pan"),meta.loopStart(),meta.loopEnd(),f.b("loop"),f.env("sample"));val duration=f.n("duration")
            generate(meta.name(),null){SampleSynth.render(soundFont.extract(index),patch,duration,44100)}
        }
    }
    private fun opl(count:Int){
        val f=form("OPL3 · $count operators")
        f.number("frequency","Frequency (Hz)",440.0).number("duration","Note length (seconds)",2.0).number("tail","Release render tail (seconds)",1.0)
        f.number("algorithm","Algorithm (0..${if(count==2)1 else 3})",0.0).number("feedback","Feedback (0..7)",2.0)
        f.flag("left","Left output",true).flag("right","Right output",true).flag("am","Deep tremolo",false).flag("vib","Deep vibrato",false)
        for(i in 0 until count){
            f.heading("Operator ${i+1} · chip register values")
            f.number("mult$i","Multiplier (0..15)",if(i==0)2.0 else 1.0).number("tl$i","Attenuation (0..63)",if(i==0)24.0 else 0.0).number("ksl$i","Key scale level (0..3)",0.0)
            f.number("a$i","Attack rate (0..15)",15.0).number("d$i","Decay rate (0..15)",5.0).number("s$i","Sustain attenuation (0..15)",4.0).number("r$i","Release rate (0..15)",7.0).number("w$i","Waveform (0..7)",0.0)
            f.flag("hold$i","Hold sustain",true).flag("ksr$i","Key scale rate",false).flag("am$i","Tremolo",false).flag("vib$i","Vibrato",false)
        }
        f.show{
            val operators=(0 until count).map{i->ChipPatches.OplOperator(f.i("mult$i"),f.i("tl$i"),f.i("ksl$i"),f.i("a$i"),f.i("d$i"),f.i("s$i"),f.i("r$i"),f.i("w$i"),f.b("hold$i"),f.b("ksr$i"),f.b("am$i"),f.b("vib$i"))}
            val p=ChipPatches.OplPatch(operators,f.i("algorithm"),f.i("feedback"),f.b("left"),f.b("right"),f.b("am"),f.b("vib"));val program=ChipPatches.opl(p,f.n("frequency"),f.n("duration"),f.n("tail"),44100)
            generate("OPL3 $count operators",f.profile(RenderProfile.Engine.OPL3,count)){ChipSynth.render(program)}
        }
    }
    private fun sid(){
        val f=form("SID 6581")
        f.number("duration","Note length (seconds)",2.0).number("tail","Release render tail (seconds)",1.0)
        f.choice("clock","Clock",listOf("PAL · 985248 Hz","NTSC · 1022727 Hz"))
        f.number("cutoff","Filter cutoff (0..2047)",1024.0).number("resonance","Filter resonance (0..15)",0.0)
        f.number("modes","Filter mode bits: 1 low, 2 band, 4 high",0.0).number("volume","Master volume (0..15)",15.0).flag("voice3off","Disconnect voice 3 output",false)
        for(i in 0..2){
            f.heading("Voice ${i+1}");f.flag("enabled$i","Enable voice ${i+1}",i==0)
            f.number("freq$i","Frequency (Hz)",440.0*(i+1)).number("pulse$i","Pulse width (0..4095)",2048.0)
                .number("wave$i","Waveform bits: 1 triangle, 2 saw, 4 pulse, 8 noise",2.0)
                .number("a$i","Attack rate (0..15)",2.0).number("d$i","Decay rate (0..15)",5.0).number("s$i","Sustain level (0..15)",10.0).number("r$i","Release rate (0..15)",6.0)
                .flag("sync$i","Oscillator sync",false).flag("ring$i","Ring modulation",false).flag("test$i","Test/reset oscillator",false).flag("filter$i","Route through filter",false)
        }
        f.show{
            // Preserve voice numbers for sync/ring connections; disabled voices have zero frequency and gate removed below.
            val enabled=(0..2).map{f.b("enabled$it")};require(enabled.any{it}){"Enable at least one SID voice"}
            val voices=(0..2).map{i->ChipPatches.SidVoice(if(enabled[i])f.n("freq$i") else 0.0,f.i("pulse$i"),f.i("wave$i"),f.i("a$i"),f.i("d$i"),if(enabled[i])f.i("s$i") else 0,f.i("r$i"),f.b("sync$i"),f.b("ring$i"),!enabled[i]||f.b("test$i"),f.b("filter$i"))}
            val patch=ChipPatches.SidPatch(voices,f.i("cutoff"),f.i("resonance"),f.i("modes"),f.i("volume"),f.b("voice3off"),if(f.c("clock")==0)985248 else 1022727)
            val program=ChipPatches.sid(patch,f.n("duration"),f.n("tail"),44100);generate("SID 6581",f.profile(RenderProfile.Engine.SID6581)){ChipSynth.render(program)}
        }
    }
    private fun cms(){
        val f=form("CMS / Game Blaster · dual SAA1099")
        f.number("duration","Note length (seconds)",2.0).number("clockHz","Chip clock (Hz)",8000000.0).number("gain","Output gain (0..8)",1.0)
        f.envelope("master")
        for(chip in 0..1){
            f.heading("SAA1099 chip ${chip+1}");f.number("envHz$chip","Shared external envelope clock Hz (0 = hold)",50.0)
            for(v in 0..5){val i=chip*6+v
                f.heading("Voice ${i+1}");f.number("freq$i","Frequency register (0..255)",100.0).number("oct$i","Octave register (0..7)",4.0)
                    .number("left$i","Left amplitude (0..15)",if(i==0)12.0 else 0.0).number("right$i","Right amplitude (0..15)",if(i==0)12.0 else 0.0)
                    .flag("tone$i","Tone enabled",i==0).flag("noise$i","Noise enabled",false)
            }
            for(group in 0..1){val i=chip*2+group
                f.heading("Noise / envelope group ${i+1}")
                f.number("noiseRate$i","Noise rate (0..2 clock divisions, 3 follows tone)",0.0)
                f.flag("envOn$i","Enable chip envelope",false).number("envMode$i","Envelope mode (0 zero, 1 max, 2 single decay, 3 repeat decay, 4 single triangle, 5 repeat triangle, 6 single attack, 7 repeat attack)",3.0)
                    .flag("envBits$i","3-bit envelope resolution",false).flag("envClock$i","External envelope clock",false).flag("envInvert$i","Invert right envelope",false)
            }
        }
        f.show{
            val voices=(0..11).map{i->ChipPatches.CmsVoice(f.i("freq$i"),f.i("oct$i"),f.i("left$i"),f.i("right$i"),f.b("tone$i"),f.b("noise$i"))}
            val envelopes=(0..3).map{i->ChipPatches.CmsEnvelope(f.b("envOn$i"),f.i("envMode$i"),f.b("envBits$i"),f.b("envClock$i"),f.b("envInvert$i"))}
            val patch=ChipPatches.CmsPatch(voices,(0..3).map{f.i("noiseRate$it")},envelopes,f.i("clockHz"),(0..1).map{f.i("envHz$it")})
            val duration=f.n("duration");val env=f.env("master");val gain=f.n("gain");require(gain in 0.0..8.0){"Gain must be 0..8"}
            val program=ChipPatches.cms(patch,duration+env.release(),44100)
            generate("CMS / Game Blaster",f.profile(RenderProfile.Engine.CMS)){
                val rendered=ChipSynth.render(program);val data=rendered.samples()
                for(frame in 0 until rendered.frames()){
                    if(frame and 1023==0&&Thread.currentThread().isInterrupted)throw java.io.InterruptedIOException("CMS render cancelled")
                    val scale=env.value(frame/44100.0,duration)*gain;data[frame*2]=(data[frame*2]*scale).toFloat();data[frame*2+1]=(data[frame*2+1]*scale).toFloat()
                }
                AudioBuffer(44100,2,data)
            }
        }
    }

}

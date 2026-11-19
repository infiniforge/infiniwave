# Rendering profiles

FM, OPL3, SID6581 and CMS profiles are shown alongside audio waveform tracks. Their
waveform is an offline render preview. Select a profile and use Tracks → Edit profile parameters (or Generate →
Profile tools → Edit selected profile) to reopen its controls; Apply parameters renders a new
preview while retaining track settings. Undo restores both settings and preview.
Use the selection bar’s Render to audio action or Generate → Profile tools →
Render profile to audio to enable sample edits on its output.

Generate → Profile tools → Save profile JSON exports .iwprofile.json through the normal save
picker. Generate → Profile tools → Load profile JSON opens a saved profile in its parameter editor;
applying adds it as a new track at the cursor. Existing tracks are preserved.
A project embeds each track's parameters and PCM cache; a standalone JSON profile
has no PCM, source paths, executable expressions or bank references.

Schema version 1 uses UTF-8, a format identifier, engine enum and a flat parameters
object. Numeric controls are JSON numbers, checkboxes booleans and choice controls
zero-based numeric indices. Numeric/boolean strings are also accepted for hand edits.
Missing controls take the engine's displayed defaults; changing parameter meaning
requires a schema version change. FM supports 1/2/4/6/8 operators, OPL3 supports 2/4.
Unknown engines/versions, duplicate keys, non-finite values, arrays, malformed JSON
and oversized files are rejected. Native patch validation checks ranges on Apply.

A loadable [FM bell profile](../examples/fm-bell.iwprofile.json) is included.
Minimal example (other FM controls use displayed defaults):

```json
{
  "format": "infiniforge.synth-profile",
  "version": 1,
  "engine": "FM",
  "parameters": {
    "operators": 2,
    "frequency": 440,
    "duration": 2,
    "m0-1": 3,
    "e0attack": 0.01,
    "e0release": 0.3
  }
}
```

FM modulation keys are mDEST-SOURCE (zero-based); envelope keys start eINDEX.
OPL controls use native 0..15 envelope rates and sustain attenuation; SID uses native
rates and sustain level. CMS exposes native envelope modes plus a master DAHDSR in
seconds. Its external envelope clocks accept 0 (hold) to 20 kHz, with a 200,000-event
render budget; lower rates or shorter notes may be needed. CMS frequency controls
are native frequency/octave registers, not Hz. JSON currently describes a static
sound plus note/tail duration; time-varying automation is future work.

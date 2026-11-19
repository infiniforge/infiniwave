# Editing waveforms

## Delete part of a sample

Choose **Edit** beside the waveform (or tap its header), drag across the sound to
highlight a time range, then
press **Delete selection** above the workspace. The highlighted frames are removed
from that waveform and its later samples move left to close the gap. Other tracks
stay at their original times. Undo restores the edit. Empty or non-overlapping
selections disable deletion; the time range control can set exact boundaries.
Select → Select whole waveform highlights an entire clip; Tracks → Remove whole
waveform removes the whole track after confirmation. Silence keeps duration while
replacing the selected sound with zeros; Trim keeps only the highlighted sound.

Parameter profiles first show **Render to audio** in the selection bar. This keeps
the rendered waveform as ordinary editable samples. The conversion is undoable;
Undo restores profile parameters as well as its cached audio.

## Zoom and select

Spread two fingers to zoom in; pinch them together to zoom out around the sound
under your fingers. The fingers can land on the same waveform or different tracks,
and horizontal, vertical and diagonal pinches work. Move both fingers together
to pan the timeline. The viewport stays aligned across all waveform lanes. Pinching, including lifting one finger
before the other, leaves the time selection untouched. View offers fit all, fit
selection, zoom and pan buttons. View → Use drag to pan changes one-finger drags
to horizontal timeline movement; switch back to drag-to-select for editing.

## Split and recombine stereo

Select a stereo waveform and open Channels → **Split Left / Right**. Its L/R lanes
become two independent mono waveforms named Left and Right. Their gains and output
routes preserve the source's audible stereo levels. The same split control is
visible beneath each stereo header. Choose **Edit** beside Left or Right to target
only that waveform; only the active waveform shows the committed time highlight.
A drag on another waveform previews locally and selects that waveform on release.

Channels → **Recombine Left + Right** lets you choose which mono waveform is Left
and which is Right. Only those two are replaced. Their relative start times are
preserved; their gains and mute/solo choices are baked into the new stereo PCM.
Other waveforms remain in the workspace. Undo restores the separate source tracks.
Combine multiple channels assigns chosen mono waveforms in workspace order.

## Invert one side and mix to mono

1. Press **Split Left / Right to edit separately** below the stereo header.
2. Choose **Edit** beside Right. Select → **Select whole waveform** targets the
   entire channel; highlight a shorter range to invert only that part.
3. Effects → Waveform operations → **Invert polarity** changes only Right. Left
   and every other waveform retain their original samples.
4. Channels → **Mix chosen to mono**: check Left and Right, leaving other tracks
   unchecked, then Apply. This replaces only those sources with one mono waveform.

The default **Average levels** setting divides the sum by the number of checked
waveforms: for this pair it produces `(Left - Right) / 2`. Uncheck it for a summed
`Left - Right` mix. Identical samples on both sides cancel to zero. This is a
stereo difference operation; other sound shared by the channels also cancels.

Mixing uses each chosen waveform's full length, preserving relative start times
and rendering its gain and mute/solo settings into PCM. Solo considers only checked
waveforms; an unrelated solo track cannot suppress the render. The average divisor
includes checked muted/non-solo waveforms. Output destination routes and pan are
ignored when folding to mono, so split Right's channel-2 route works automatically.
The result starts at the earliest chosen start, uses unity gain and normal output
routing, and carries solo if any source was solo. Unrelated tracks are preserved.
Float samples remain unclipped internally; summed levels may clip during output.
Undo restores mixing, inversion and splitting separately; source files stay intact.

## Mute and solo during playback

Use **M** to mute a waveform and **S** to solo it while sound is playing. These
controls update the running mix without stopping, restarting or moving playback.
When any track is solo, only solo tracks are audible; mute still silences a solo
track. Multiple tracks can be solo together. Muting every audible track keeps
the playhead moving through silence; unmute to hear the current point in time.
Playback ends at its original range boundary. Output buffering can delay a change
briefly. These flags remain saved and undoable; Undo and sample edits retain their
usual stop behavior.

## Effects

Effects act on the highlighted time in the selected waveform. With only a cursor,
they act on the whole waveform. A selection missing the track disables effects;
select the whole waveform or highlight the desired sound. All effects are undoable.

- Waveform operations → Invert polarity flips samples above/below zero without
  changing time. Reverse changes time order. Fade in/out change the selected level.
- Normalize peaks sets the selected peak level in dBFS. Link channels keeps stereo
  balance; unlinking normalizes each side independently. Remove DC offset centers
  each channel around zero before calculating gain. Silence remains silent.
- Noise filtering → Low-pass reduces high-frequency hiss; High-pass reduces low
  rumble. Cutoff and Q/resonance are adjustable.
- Hum notch filters start at 50 or 60 Hz. Adjust frequency to match the hum and Q
  to choose notch width (larger Q is narrower).
- Noise gate reduces audio below a threshold. Reduction controls attenuation;
  attack, hold and release control transitions. Lower thresholds preserve softer
  sounds. The gate uses linked channels to preserve stereo balance. It reduces
  quiet-passage noise; it does not remove background noise during louder passages.

Filters/gates blend over up to 5 ms at selection edges adjoining unchanged audio.
They can alter desired frequencies too; use Undo to compare. These are standard
filters and gating, not learned-profile spectral noise reduction.

The header has Import, Undo, Redo, Export and More project options. Bottom tools
open navigable dialogs; Back returns to the previous page and Close returns to the
workspace. Long-press icons for their names. More → Editing help repeats the main
steps inside the app. Import/export retain InfiniSelect and optional InfiniFolder.

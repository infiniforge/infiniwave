# InfiniWave application

InfiniWave owns an Android audio editor and sound-generation workspace. Its model
is Audacity: several waveforms on a shared time axis, editable selections and
independent tracks/channels. It remains independently installable and buildable.

Implemented in 0.4.0: importing multiple audio files, channel-lane waveform previews,
time selections, zoom/pan/reset-to-fit, playback, track gain/pan/offset/mute/solo,
explicit destination channels, duplicate/remove, split to mono, combine mono as
channels, chosen-track mono mixdown, stereo mixdown, copy/cut/paste, delete/trim/silence/reverse/normalize/fades,
undo/redo, project save/open/recovery, and mono/stereo/multichannel PCM16 WAV export.
There is no fixed track limit; a workspace allows 32 million unique float samples,
a clip/render 16 million, and an output clip up to 32 channels. Long material must
be shortened or exported; disk-backed streaming editing is future work.

Generation supports arbitrary FM matrices with per-operator DAHDSR envelopes,
ratio/fixed frequency/detune/phase/waveform/pulse-width/level, carrier weights,
feedback, gain/pan and deterministic noise; OPL3 2/4-operator algorithms, feedback,
waveforms, native ADSR rates/levels, key scaling, tremolo/vibrato and stereo routing;
SID's three voices with waveform combinations, pulse width, native ADSR, oscillator
sync/ring/test, filter routing/modes/cutoff/resonance and PAL/NTSC clocks; and dual
SAA1099 CMS with 12 tone/noise voices, stereo amplitudes, octaves/frequency registers,
four noise generators, four native envelopes/external clocks and a master DAHDSR.

The interface has a document header (import, undo/redo, export and project options),
a persistent selection and Delete selection bar, and bottom Select/Effects/Channels/
Tracks/Generate/View tools. Tool dialogs have Back/Close navigation and subpages.
Delete removes only highlighted frames in the selected waveform, closing its gap.
The other tracks stay positioned. Empty selections disable deletion. Profiles offer
an explicit Render to audio action first. Finger drags preview and commit selection
on release; any multitouch cancels that pending selection. Pinching zooms around
its moving focus across all waveforms, with fingers on the same or separate tracks.
Horizontal, vertical and diagonal finger separation drives zoom; translating both
fingers pans. The workspace retains the gesture until all fingers lift. View also
offers drag-to-pan and fit controls.

Every waveform has an Edit radio control, and committed selection highlights
appear only on the active waveform. Effects and sample edits target only its ID.
Stereo headers expose Split Left / Right for independent editing directly.
Channels splits stereo into named Left/Right mono waveforms, preserving audible
levels and routes. Recombining chosen mono waveforms bakes their gains and mute/solo
choices into output channels, starts at their earliest offset, and preserves every
unrelated track. Channel order is explicit for stereo, workspace order for multiple
channels. Mix chosen to mono instead sums checked mono waveforms into one channel,
ignoring output routing/pan, and preserves unrelated tracks. It renders complete
source lengths with gains and chosen mute/solo flags; averaging divides by checked
count by default and can be disabled. Split → Edit Right → select whole waveform →
Invert polarity → Mix chosen to mono produces a stereo difference. These operations
are undoable and retain original PCM in history.

Effects include inversion of polarity (independent of time reversal), configurable
peak normalization (-60..0 dBFS), optional per-channel DC removal and linked channel
balance; second-order low/high-pass and adjustable hum notch filters; and a linked
noise gate with threshold, reduction, attack, hold and release. The selected time
is processed, or the whole waveform when only a cursor is present. Filtering/gating
crossfade at partial selection edges to limit clicks. These tools do not implement
learned-profile spectral denoising; the gate reduces quiet passages. Processing
belongs to libinfinisound's public portable PCM API. Inputs remain untouched.

These engines create parameter profiles. Their displayed PCM is a render cache;
Generate → Profile tools → Edit selected profile changes parameters, preserving the original profile until explicitly rendered
to an audio track. Both profile edits and conversion are undoable. JSON files make
profiles portable. SF2 extraction and sample pitch/loop/envelope rendering create
ordinary editable waveforms; sample bank/preset editing is not implemented.

libinfinisynth owns generation and chip cores; libinfinisound decodes portable
WAV/MP3/AAC/FLAC-family inputs as supported by its registry. Export is PCM16 WAV,
not a claim of matching every import format. libinfiniselect owns file selection,
source resolution and saves, using optional InfiniFolder or Android SAF.

Recording, plugin effects, automation/sequencing, full SoundFont preset/modulator
playback and complete Audacity feature parity remain planned. No microphone access
is requested. SID analog revision fidelity and every model of sound chip are not
claimed. No instrument/sample bank is bundled.

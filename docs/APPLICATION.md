# InfiniWave application

InfiniWave owns an Android audio editor and sound-generation workspace. Its model
is Audacity: several waveforms on a shared time axis, editable selections and
independent tracks/channels. It remains independently installable and buildable.

Implemented in 0.2.0: importing multiple audio files, channel-lane waveform previews,
time selections, zoom/pan/reset-to-fit, playback, track gain/pan/offset/mute/solo,
explicit destination channels, duplicate/remove, split to mono, combine mono as
channels, stereo mixdown, copy/cut/paste, delete/trim/silence/reverse/normalize/fades,
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

These engines create parameter profiles. Their displayed PCM is a render cache;
Edit changes parameters, preserving the original profile until explicitly rendered
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

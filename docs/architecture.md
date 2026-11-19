# InfiniWave architecture

The app owns UI, lifecycle, playback, clipboard, projects, profile serialization,
undo/redo and editing workflows. `editor-core` is app-owned Java without Android;
its immutable WaveProject contains tracks with immutable AudioBuffer PCM and
optional RenderProfile settings. Track identity, offset, routing and mixer settings
are independent of source channel layout. Native Android views draw precomputed
peak caches, with one shared time selection/viewport and vertically scrolling tracks.

A profile is authoritative parameters plus a replaceable PCM preview. GeneratorDialog
maps parameter controls to library patches. Applying parameters renders on the worker
and replaces the selected track without losing mixer settings or identity. Sample
edits are rejected for profiles until an explicit, undoable render-to-audio conversion.
Profile JSON and the versioned project codec live in editor-core. Imported profiles
open controls for inspection/validation before rendering; JSON cannot execute code.

ProjectHistory shares immutable PCM across snapshots, retains up to 30 undo states
and 64 million unique float samples, and discards redo after new edits. The current
project allows 32 million unique float samples; individual renders allow 16 million.
Counts are memory budgets, not limits on the number of workspace tracks. Output
supports 1..32 channels. Float mix levels remain unclipped until device/WAV output.

One worker performs decoding, generation, edits, peaks and ordered recovery writes.
Cancellation invalidates stale completions and interrupts cooperative renderers.
Playback owns a separate session/thread and stops when the Activity pauses. The
activity preserves save cache sources through the picker result and cleans up after.

The standalone Gradle build uses four pinned public library source composites:
libinfiniselect (selection, grants, source resolution and saving), libinfinisound
(portable audio decoding), libinfinistream (sound's stream dependency), and
libinfinisynth (FM, envelopes, SF2 extraction/sample rendering and native chip cores).
No app imports another app's source or build output. Infinifolder is optional.

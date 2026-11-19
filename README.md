# InfiniWave

InfiniWave 0.3.0 is an independent Android multitrack audio editor and sound-design
workspace, inspired by Audacity. Import several files or generate sounds; each
track appears on a shared timeline with separate channel lanes. There is no fixed
workspace track count; memory/render budgets still apply.

The interface uses a compact document toolbar, a persistent selection/Delete bar,
and bottom tools with navigable Back/Close dialogs, following InfiniCanvas's layout.
Drag across sound, then press **Delete selection** to remove it and close the gap
in that waveform. Undo restores it. Pinch with two fingers to zoom without changing
the selection; View switches one-finger dragging between selection and timeline pan.

Channels → **Split Left / Right** creates separate mono waveforms. **Recombine
Left + Right** assigns any chosen pair to stereo while preserving other tracks.
Multiple chosen mono waveforms can also become independent output channels.

Effects includes polarity inversion, reverse, fades, configurable peak normalization
(with linked channels/DC removal), low/high-pass filters, 50/60 Hz hum notch filters,
and a configurable linked noise gate. Effects use highlighted time, or the whole
waveform when no range is selected. Tracks retain gain, pan, timing, mute/solo and
routing. Copy/cut/paste, trim, silence, playback, undo/redo and PCM16 WAV export remain.

Generate creates editable FM (1/2/4/6/8 operators), OPL3 (2/4 operators), SID 6581
and CMS/Game Blaster profiles, with waveform previews. Generate → Profile tools reopens parameters, saves/loads JSON, or renders a
profile to audio. The selection bar also offers Render to audio for profiles,
followed by the ordinary Delete selection control. Profiles remain undoable. Projects preserve both parameters and render caches. SF2 banks can supply
original samples or pitched/looped/enveloped sample waveforms; banks are not bundled.

Import/export use libinfiniselect, with optional InfiniFolder integration and
Android SAF fallback. Portable audio decoding comes from libinfinisound; generation
comes from the new reusable [libinfinisynth](https://github.com/infiniforge/libinfinisynth).
Inputs are read-only; edits, backups and exports are separate app-owned data.

This is a first editor implementation. Recording, plugin effects, automation lanes,
MIDI/preset-level SoundFont playback and full Audacity parity are future work.
Chip rendering is offline. SID models 6581; CMS uses two SAA1099 cores.

## Standalone build

Use a POSIX host with JDK 17/21, Android SDK platform 36/build-tools 35.0.0,
Python 3, Git, and clang for native host regression tests. Normal Android builds
also need NDK 27.2.12479018 and CMake 3.22.1. Termux uses installed clang/aapt2 and
packages arm64-v8a. Set ANDROID_HOME or ANDROID_SDK_ROOT. The Gradle 9.7.1 wrapper
and four public source dependencies are pinned in dependencies.lock.json.

Run `./build.sh --max-workers=1 --console=plain`. It initializes/checks pinned
submodules, runs editor, audio and synthesis tests, builds the APK, and runs lint.
Output: `InfiniWave-debug.apk`. No sibling application build/source is required.
An initial build needs network access for Maven/Gradle artifacts.

The transparent icon base is `artwork/wave.svg` / `wave.png`. Run
`python3 scripts/build-app-icons.py` with CairoSVG and Pillow to regenerate the
suite compositor's density assets; it combines the base with the shared Infini
emblem and outline, following InfiniFolder's established method.

[Editing guide](docs/editing.md) · [Validation](docs/validation.md) ·
[Application behavior](docs/APPLICATION.md) · [Architecture](docs/architecture.md) ·
[Storage](docs/storage.md) · [JSON profiles](docs/profiles.md) · [Documentation](docs/README.md)

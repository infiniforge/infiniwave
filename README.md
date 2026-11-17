# InfiniWave
InfiniWave is a planned sound-generation and instrument-creation application, distinct from
InfiniTrack's composition workflows. This repository is currently a placeholder; see the
[application scope and implementation status](docs/APPLICATION.md).

**Status: planned placeholder.** This checkout currently contains project guidance
only. It has no Android application source, Gradle build, app-level `build.sh`, or
APK, so it cannot be built or installed yet.

The planned app focuses on sound generation, software instruments, sound design, effects, and presets. It should own those workflows and its project state in an independent Android application, using shared audio-processing APIs through public libraries. InfiniTrack owns composition and arrangement; InfiniMedia owns playback.

No synthesis UI, instrument editor, effect workflow, project format, or preset format is implemented in this placeholder. The earlier Audacity-style audio-file-editor description is superseded; see [the application description](docs/APPLICATION.md) for the current direction and historical note.

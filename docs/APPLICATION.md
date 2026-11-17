# InfiniWave application

## Purpose

InfiniWave is the planned InfiniForge sound-generation and instrument-creation environment. It should focus on making, shaping, and managing sounds and instruments. InfiniTrack has the separate responsibility for arranging sounds into musical compositions; InfiniMedia plays supported audio outputs.

## Primary use cases

- Design sounds with oscillators, modulation, envelopes, and software instruments.
- Create and edit instruments, presets, and sample-based or soundfont-based configurations where supported.
- Apply sound-design effects and audition generated audio.
- Exchange reusable instrument/preset data with InfiniTrack through a documented file format or public contract when one is designed.
- Export supported audio or instrument data for use by InfiniMedia and other compatible applications.

## Current implementation status

This repository is a planned placeholder. It contains no Android application source, Gradle build, app build script, installable APK, application API, or declared library dependency. None of the synthesis, instrument, effect, or preset-management functions above is implemented by this app.

The current product direction in this document supersedes an earlier placeholder description of InfiniWave as an Audacity-style audio file editor. That older description is retained here as historical context only; no editor implementation was present in either direction.

## Intended responsibilities and shared libraries

InfiniWave should own sound-generation workflows, instrument editing UI, app-owned presets/project state, audition controls, and export. Reusable audio synthesis, decoding, processing, and PCM APIs belong in libinfinisound; portable byte/resource contracts may use libinfinistream, and user-selected projects/presets may use libinfiniselect and Android SAF. These are candidate integrations, not current dependencies.

Do not duplicate a synthesis engine, decoder, or reusable effect processor already owned by a library. Do not make InfiniTrack or InfiniMedia a build/runtime prerequisite. A shared sound-engine API should be agreed with libinfinisound and, where needed, a serialized instrument/project format before committing to cross-app interoperability.

## Planned and exploratory functionality

Potential work includes oscillator and sample-based synthesis, instrument parameter editing, envelopes, modulation, effects, soundfont integration, preset management, and interoperability with tracker/DAW sessions. The exact instrument model, realtime rendering constraints, automation representation, sample licensing, and project interchange format remain open design questions.

## Interoperability

The [generated ecosystem map](ECOSYSTEM.md) provides the wider application/library map. Future instrument exchange with InfiniTrack should use a documented versioned format or public API and preserve compatibility independently of library version numbers. InfiniMedia may consume exported audio through normal supported formats. These are intended scenarios only; no InfiniWave integration exists today. The app must keep a standalone install/build path and use Android resource grants for imported/exported content.

## Documentation audit

- Contradictory: the earlier placeholder called InfiniWave an Audacity-style audio file editor; the current product direction is synthesis and instrument creation. The prior wording is retained as historical, not active scope.
- Incomplete: no app code or design establishes synthesis APIs, instruments, presets, or project formats.
- Unverified: all described functions are plans; libinfinisound does not prove an InfiniWave UI or workflow exists.

## Limitations and outstanding work

- No app source, build, audio engine integration, file format, preset model, or release version exists in this repository.
- libinfinisound capabilities do not mean InfiniWave already exposes a synthesizer UI or instrument editor.
- No compatibility contract with InfiniTrack or InfiniMedia has been defined.
- Define the instrument/project data model, preset exchange, realtime rendering requirements, and the boundary between app workflows and reusable shared audio processors before implementation.
- The earlier audio-file-editor description is superseded and should not be presented as current scope.

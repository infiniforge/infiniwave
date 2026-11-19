# Editing validation

The standalone build runs editor-core tests, app audio tests, libinfinisynth tests,
APK assembly and Android lint. The linked libinfinisound portable API/core suites
are also run from that library's own checkout for its new PCM processing API.

Regression coverage includes selection clipping/deletion, immutable sources,
effect save/undo/redo, explicit stereo pair ordering, preserving unrelated tracks,
relative offsets, split/recombine mixer levels, mute behavior, profile guards and
project round-trips. Mono mix regressions cover explicit chosen IDs, split routing
folded to mono, offset/gain preservation, average/sum levels, chosen mute/solo flags,
unrelated-track isolation, unclipped float samples, invalid selections/profiles,
render budgets, cancellation and undo. An app integration test splits stereo,
inverts only Right with libinfinisound, mixes to mono, checks common-signal
cancellation and unrelated PCM, undoes all three operations and verifies mono WAV
export/import. Gesture models cover deferred selection commits, cancellation,
second-pointer selection suppression, anchored pinch zoom, two-finger pan and
viewport bounds. Pinch geometry also covers fingers in separate tracks, vertical
and diagonal gestures, small movements, changing pointer pairs and invalid/nearby
points without zoom jumps. DSP checks measure wanted/unwanted tone levels for low/high-pass
and hum notch filters, channel isolation, gate attenuation and stereo linking,
DC/peak normalization, finite values, invalid parameters and cancellation.

Live playback mixer tests verify consecutive block positions while muting,
unmuting, soloing and muting a solo track; silent blocks that continue to the
original range end; rapid/latest settings updates; simultaneous solos; high-route
stereo monitoring; bounded blocks and a partial final block; equality with offline
mixing for nonzero selections; and cancellation without cursor advancement.

No Android device is connected in the current build environment. Handset acceptance
is still required for toolbar/dialog sizing and Back navigation, dragging/deleting
sound, pinch focus and vertical workspace scrolling, audible filtering and playback,
selecting different stereo pairs, Edit radio states and isolated highlights, the
split/invert/mono dialog workflow, mute/solo response without device underruns or
transport resets, and SAF/InfiniFolder open/save round-trips.
Automated model/DSP tests do not substitute for those touch/audio checks.

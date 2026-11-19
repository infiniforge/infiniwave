# Editing validation

The standalone build runs editor-core tests, app audio tests, libinfinisynth tests,
APK assembly and Android lint. The linked libinfinisound portable API/core suites
are also run from that library's own checkout for its new PCM processing API.

Regression coverage includes selection clipping/deletion, immutable sources,
effect save/undo/redo, explicit stereo pair ordering, preserving unrelated tracks,
relative offsets, split/recombine mixer levels, mute behavior, profile guards and
project round-trips. Gesture models cover deferred selection commits, cancellation,
second-pointer selection suppression, anchored pinch zoom, two-finger pan and
viewport bounds. Pinch geometry also covers fingers in separate tracks, vertical
and diagonal gestures, small movements, changing pointer pairs and invalid/nearby
points without zoom jumps. DSP checks measure wanted/unwanted tone levels for low/high-pass
and hum notch filters, channel isolation, gate attenuation and stereo linking,
DC/peak normalization, finite values, invalid parameters and cancellation.

No Android device is connected in the current build environment. Handset acceptance
is still required for toolbar/dialog sizing and Back navigation, dragging/deleting
sound, pinch focus and vertical workspace scrolling, audible filtering and playback,
selecting different stereo pairs, and SAF/InfiniFolder open/save round-trips.
Automated model/DSP tests do not substitute for those touch/audio checks.

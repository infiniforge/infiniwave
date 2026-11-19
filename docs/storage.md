# Storage and handoff

Import, project open, profile JSON load and every export use libinfiniselect.
InfiniFolder provides its picker/source resolver when available; Android SAF is
the fallback. The app asks for audio/SF2/project extensions or profile JSON through
OpenRequest, reads SelectionResult, persists only provider-granted permissions,
and resolves selected sources through requireSelectedSource. Content URI identities
remain opaque; provider IDs are never interpreted as filesystem paths.

Source audio is staged to an app cache file (at most 64 MiB encoded) for the portable
decoder (currently up to eight input channels), converted to finite float PCM at the workspace rate of 44.1 kHz, and deleted
from cache after decoding. SF2 banks are bounded to 64 MiB and are session-local.
Raw samples can be extracted to permanent project tracks. JSON profiles are bounded
to 128 KiB; projects are staged at most 160 MiB and validate PCM/resource budgets.
External ACTION_VIEW audio/project URIs use the same granted-source resolution.

Project files (.iwave) are versioned binary snapshots with shared/deduplicated PCM,
track identities/mixer metadata, and embedded profile JSON. Version 2 writes profiles;
version 1 waveform-only files remain readable. Profiles store parameters, while the
project also stores render caches for playback without regenerating every track.
Standalone .iwprofile.json files contain only parameters and need no bank or audio.

Workspace changes write a private recovery snapshot through an ordered worker and
atomic file replacement. Reopening the app restores it. Explicit project saves
provide a portable user-owned copy. The original imported files are never edited.
Exports create PCM16 WAV, .iwave or profile JSON in app cache, publish a temporary
read URI with InfiniSelect.createFileDocument, and request a destination through
SaveRequest. Cache files live until the save result, then are removed. Cancelled
exports never overwrite source files. Storage failures are reported to the UI.

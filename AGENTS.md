# InfiniWave agent guidance

InfiniWave is an independent Android audio editor in early implementation. This
checkout contains a multitrack editor and parameter-based synthesis profiles. Keep documentation clear about implemented behavior and planned
work; its direction is documented in docs/APPLICATION.md.

## Independence and integration

- Build this repository from its own checkout with only its declared and pinned library dependencies and documented SDK/toolchain prerequisites.
- Keep its Gradle build independent from every other application. Do not include another app as a Gradle project or import sibling app source/build outputs.
- Use reusable audio processing, decoding, PCM, and synthesis APIs through linked libraries such as libinfinisound when suitable. Keep editor workflows, UI, and app-owned project state in InfiniWave.
- Use Android SAF and granted content URIs for import and export. Infinifolder may improve file access when installed, but must remain optional.

## Project focus

- Audio editing and processing are the central workflows; audio and instrument-sample generation support that work.
- Preserve parameter profiles as source data; PCM previews are render caches. Sample edits require explicit render-to-audio conversion.
- Keep waveform editing and profile changes undoable; source URIs stay read-only.
- Do not claim recording, plugin effects, complete format coverage, or Audacity parity.
- Version JSON/project formats and validate parameters, file sizes and PCM budgets.
- Document standalone build instructions and exact library revisions.

## Ecosystem workflow for agents

1. Read docs/ECOSYSTEM.md for component ownership and suite boundaries.
2. Read docs/APPLICATION.md and the architecture/storage docs before changing public behavior.
3. Check shared library APIs before implementing reusable audio or storage behavior.
4. Keep app responsibilities, public library APIs, and other applications' private data/source separate.
5. Check exact dependency pins and interoperability contracts before cross-repository changes.
6. Update the documentation index and relevant API/behavior documentation. docs/ECOSYSTEM.md is generated from the central manifest and must not be edited locally.

## Documentation index

- README.md — project purpose, prerequisites, and standalone build.
- docs/README.md — local documentation index.
- docs/APPLICATION.md — purpose and implementation status.
- docs/architecture.md — ownership and audio workflow.
- docs/storage.md — URI selection, grants, and save fallback.
- docs/profiles.md — rendering profiles and versioned JSON.
- docs/ECOSYSTEM.md — generated ecosystem inventory; do not edit locally.

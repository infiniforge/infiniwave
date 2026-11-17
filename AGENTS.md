# InfiniWave agent guidance

InfiniWave is a planned Infiniforge audio file editor, with Audacity as a product reference. This directory is a project placeholder; do not describe features as implemented until they exist in this app.

## Independence and integration

- Establish a standalone Android application build in this repository. It must build from its own checkout with only its declared and pinned library dependencies and documented toolchain.
- Keep its Gradle build independent from every other application. Do not import sibling app source or depend on sibling build outputs.
- Use reusable audio processing and decoding through linked libraries such as libinfinisound when suitable. Keep editing workflow, UI, and app-owned project state in this application.
- Use Android SAF and granted content URIs for import and export. Infinifolder may improve file access when installed, but must remain optional.

## Project focus

- The intended role is audio editing. Define editing operations, project format, and export behavior as implementation grows; do not imply complete Audacity feature parity.
- Document the app's standalone build and keep library revisions explicit.

## Ecosystem workflow for agents

1. Read `docs/ECOSYSTEM.md` first to understand the component map and ownership boundaries.
2. Before changing a public interface, read this repository's architecture and API documentation listed below and inspect its compatibility notes.
3. Check the owning shared library repository before implementing behavior that may already exist elsewhere. Do not conclude a capability is unavailable just because its implementation is outside the current repository.
4. Keep application responsibilities, shared library APIs, and other applications' private data/source separate.
5. Before a cross-repository change, check the exact dependency pin and the relevant interoperability contract. Dependency versions and protocol versions are separate; do not infer protocol incompatibility from a library-version mismatch.
6. Update the local documentation index and relevant API/behavior documentation when public behavior changes. `docs/ECOSYSTEM.md` is generated from the central manifest and must not be edited locally.

## Documentation index

- `README.md` — project purpose, prerequisites, and standalone build instructions.
- `AGENTS.md` — repository-specific development rules and this ecosystem workflow.
- `docs/README.md` — local documentation index.
- `docs/ECOSYSTEM.md` — generated ecosystem-wide inventory, boundaries, dependencies, versions, and protocols.
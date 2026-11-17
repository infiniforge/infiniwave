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

# AGENTS.md — Android FTP ingest reference

## Mission

This repository is the Android FTP ingest reference implementation for
AlphaHalf. It proves camera-to-phone transfer, foreground-service lifecycle,
SAF/MediaStore storage, OIDC, and DDNS integration. Product integration belongs
in the host `../ah_kotlin` repository and requires an explicit cross-repository
task.

## Read first

1. `README.md`
2. `settings.gradle.kts` and `app/build.gradle.kts`
3. Relevant code/tests under `app/src/`
4. For P0 integration, the sibling coordination repository:
   `../../../alpha-half-platform/docs/product/p0-scope.md`,
   `../../../alpha-half-platform/docs/architecture/decisions/ADR-0002-ftp-network-boundary.md`,
   and `../../../alpha-half-platform/docs/contracts/media-ingest.v1.md`

## Security and media invariants

- P0 FTP is limited to a trusted LAN or phone hotspot. Do not expose plaintext
  FTP to the public Internet.
- Anonymous access is off by default. Use session-specific credentials and do
  not log them.
- Bind and advertise only the intended local interface. DDNS is address
  discovery, not encryption, authorization, NAT traversal, or a tunnel.
- Never delete, overwrite, or silently mutate an original photo. Duplicate-name
  behavior must be explicit and tested.
- Publish an upload-completed fact only after the file is closed and stable.
  Interrupted/partial uploads must not appear as completed assets.
- Use user-approved SAF/MediaStore locations. Do not log image bytes, private
  absolute paths, EXIF GPS, OIDC tokens, or other credentials.

## Architecture and change boundaries

- Keep Compose callbacks thin; service/network/storage behavior belongs in
  ViewModels, services, repositories, or focused use cases with tests.
- Keep FTP transport, DDNS/OIDC clients, storage, and UI separable so the ingest
  feature can later be extracted into a portable Android library.
- Do not edit the host app, DDNS service, or selection engine unless the task
  explicitly names those repositories and their contract/merge order.
- `media-ingest.v1` is draft. Do not freeze or incompatibly change it without a
  coordinated host/FTP task and an integration-layer update.
- Do not introduce Sony SDK work, public FTP, FTPS/VPN/relay, or production
  deployment under a generic FTP task.

## Working rules

- Make the smallest task-scoped diff and match the existing Kotlin/Compose
  style.
- Record `git status --short` before work; preserve and separately report any
  pre-existing dirty files.
- Do not commit, push, deploy, use production credentials, or use private user
  photos unless the user explicitly requests and authorizes that action.
- New fixtures must be synthetic and contain no private metadata.

## Validation

- Quick gate:
  `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --stacktrace`
- Service, permission, SAF/MediaStore, network, authentication, or lifecycle
  changes also require:
  `.\gradlew.bat connectedDebugAndroidTest --stacktrace`
- Camera compatibility, large RAW, interrupted upload, duplicate filename,
  hotspot/Wi-Fi reconnect, and Android process recreation require a dated
  real-device acceptance record.
- Finish with `git diff --check` and `git status --short`.

Compilation or emulator success is not evidence that a real camera can upload
reliably.

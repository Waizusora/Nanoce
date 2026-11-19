# Nanoce — Nano Code Editor

Nanoce is a native Android code editor focused on project-oriented source editing, hierarchical file navigation, and an integrated CLI terminal.

## Project goals

- Android application targeting **ARM64 (arm64-v8a)** and **x86-64 (x86_64)**.
- C++ is the primary implementation language.
- Android platform integration through the Android NDK and a minimal Kotlin/JNI bridge.
- Project explorer with a hierarchical directory tree.
- Code editor with syntax highlighting and multi-document tabs.
- Integrated CLI terminal using a PTY and native process execution.
- Git supplied as architecture-specific **statically linked musl binaries**.
- Shared-storage access through Android's Storage Access Framework (SAF), with broader access only where Android permissions allow it.
- No dependency on Termux or an installed Linux distribution.

## Architecture

The application core is implemented in C++. A thin Kotlin layer handles Android Activity lifecycle and platform integration; JNI connects it to the C++ core.

The Android application is built with Gradle, Android Gradle Plugin, Android NDK, and CMake. CI builds separate debug APKs for both supported Android ABIs.

Git runtime targets:

| Android ABI | Git binary |
| --- | --- |
| `arm64-v8a` | `git` built for AArch64 with musl static linking |
| `x86_64` | `git` built for x86-64 with musl static linking |

A successful cross-build is not considered proof of Android compatibility. Runtime validation must cover Android kernel behavior, SELinux, seccomp, executable placement, filesystem semantics, DNS, TLS, and Git transport/helper behavior.

## Development

Build both ABIs:

```sh
gradle :app:assembleDebug
```

Build one ABI:

```sh
gradle :app:assembleDebug -PnanoceAbi=arm64-v8a
gradle :app:assembleDebug -PnanoceAbi=x86_64
```

The initial application foundation requires JDK 17, Android SDK Platform 35, Android NDK, and CMake 3.22.1.

## License

Nanoce original code is licensed under the MIT License. Third-party components retain their respective licenses and notices.

## Development status

The Android application foundation is under active development. The current launcher validates loading the C++ core through JNI. File explorer, editor, terminal, and musl Git runtime are implemented in subsequent stages.


## Android Storage Access Framework

The Android layer includes a SAF workspace adapter. Users can choose a project directory with the system document-tree picker; Nanoce persists the granted read/write URI permission and can enumerate children, create files/directories, open streams, and delete documents through `DocumentsContract`.

SAF document URIs remain opaque content URIs. They are not converted to ordinary paths and cannot automatically be passed to `std::filesystem` or a CLI process. A later workspace/terminal integration must explicitly handle provider-backed documents and, where a real filesystem path is required, use an app-owned or otherwise accessible filesystem workspace. Android's picker and provider determine which locations are available.

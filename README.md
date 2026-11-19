# Nanoce — Nano Code Editor

Nanoce is a native Android code editor focused on project-oriented source editing, hierarchical file navigation, and an integrated CLI terminal.

## Project goals

- Android application targeting **ARM64 (arm64-v8a)** and **x86-64 (x86_64)**.
- C++ is the primary implementation language.
- Android platform integration through the Android NDK and a minimal JNI bridge.
- Project explorer with a hierarchical directory tree.
- Code editor with syntax highlighting and multi-document tabs.
- Integrated CLI terminal using a PTY and native process execution.
- Git supplied as architecture-specific **statically linked musl binaries**.
- Shared-storage access through Android's Storage Access Framework (SAF), with broader access only where Android permissions allow it.
- No dependency on Termux or an installed Linux distribution.

## Architecture

The application core is implemented in C++. Android-specific lifecycle, permission, and SAF integration may use a thin Kotlin/Java bridge. This bridge does not replace the C++ application core.

Git runtime targets:

| Android ABI | Git binary |
| --- | --- |
| `arm64-v8a` | `git` built for AArch64 with musl static linking |
| `x86_64` | `git` built for x86-64 with musl static linking |

A successful cross-build is not considered proof of Android compatibility. Runtime validation must cover Android kernel behavior, SELinux, seccomp, executable placement, filesystem semantics, DNS, TLS, and Git transport/helper behavior.

## License

Nanoce original code is licensed under the MIT License. Third-party components retain their respective licenses and notices.

## Development status

The repository is at its initial foundation stage. Features are added incrementally with CI validation for both Android ABIs.

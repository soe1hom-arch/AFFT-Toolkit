# Native helper binaries — built from source

F-Droid (and reproducible-build hygiene) forbids committing prebuilt
binaries. `app/src/main/jniLibs/` and `app/src/main/assets/bin/` are
**git-ignored** — the 9 helper binaries the app executes are compiled from
pinned upstream sources at build time.

## Quick start (maintainer / CI / F-Droid)

```bash
export NDK_DIR=/opt/android-ndk   # any recent NDK with ndk-build + LLVM clang
export SRC_DIR=$PWD
tools/build_all.sh --check   # verify pinned upstream refs (no NDK needed)
tools/build_all.sh           # build all 9 binaries into app/src/main/jniLibs/
tools/verify_binaries.sh     # sanity check before assembling the APK
./gradlew assembleDebug      # Gradle also runs the check automatically
```

Single targets: `tools/build_all.sh payload erofs` etc.

## What gets built

| APK name (`lib<name>.so`) | Upstream source (pinned, see `build_config.sh`) | License |
|---|---|---|
| `payload-dumper-go` | `ssut/payload-dumper-go` @ 2.1.0 (+ static liblzma/libzstd via CGO) | GPL-3.0 |
| `magiskboot` | `topjohnwu/Magisk` @ v25.1 via `ndk-build` (`B_BOOT=1 magiskboot`) | GPL-3.0 |
| `lpmake`, `lpunpack` | AOSP `system/extras/partition_tools` + `system/core/fs_mgr/liblp` @ android-14.0.0_r1 | Apache-2.0 |
| `simg2img` | AOSP `system/core/libsparse` @ android-14.0.0_r1 | Apache-2.0 |
| `mkfs.erofs`, `extract.erofs` | `erofs-utils` @ v1.9.1 (`mkfs/main.c`, `dump/main.c` → installed as `extract.erofs`) | GPL-2.0 |
| `debugfs`, `mke2fs` | `e2fsprogs` @ v1.47.2 | GPL-2.0 |

Shared static C deps (`build_deps.sh` → `$WORK_DIR/deps`): xz 5.4.6,
zstd v1.5.5, lz4 v1.9.4.

## Notes for reviewers

- `make_ext4fs` was **removed from AOSP before Android 9** and has no
  maintained upstream; the app now uses `mke2fs -t ext4 -d <dir>` from
  e2fsprogs for the ext4-repack fallback (`AFFTService.repackExt4`).
- Magisk v25.1 is the last upstream with a pure-C++ `magiskboot`
  (no Rust toolchain needed) and already supports boot/vendor_boot
  header v4. Upgrading to a newer `magiskboot` means driving Magisk's
  `build.py` with NDK + Rust + protobuf — documented here as a future
  step, not required for F-Droid acceptance.
- Binaries are installed as `lib<name>.so` so Android places them in
  `nativeLibraryDir`, which stays executable under SELinux on Android 14+
  (see `BinaryManager`). They are real executables, not JNI libraries.

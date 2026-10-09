#!/bin/bash
# Build debugfs + mke2fs from e2fsprogs for Android ARM64.
# Source: https://github.com/tytso/e2fsprogs (GPL-2.0)
# Pinned ref: see tools/build_config.sh (E2FSPROGS_REF).
# mke2fs replaces the deprecated make_ext4fs (removed from AOSP before
# Android 9; no maintained upstream source remains).
# Output: app/src/main/jniLibs/<arch>/lib{debugfs,mke2fs}.so
#
# Env: NDK_DIR (required), SRC_DIR (repo root). See tools/build_config.sh.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="${SRC_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
export SRC_DIR
# shellcheck source=build_config.sh
source "$SCRIPT_DIR/build_config.sh"
require_ndk

E2FS_WORK="$WORK_DIR/e2fsprogs"
mkdir -p "$E2FS_WORK"

echo "=== [1/3] Fetch e2fsprogs @ $E2FSPROGS_REF ==="
fetch_ref "$E2FSPROGS_URL" "$E2FS_WORK/e2fsprogs" "$E2FSPROGS_REF"
cd "$E2FS_WORK/e2fsprogs"

echo "=== [2/3] Configure for android/arm64 (static) ==="
# Static executables: no shared-library dependency at runtime.
./configure --host=aarch64-linux-android \
    --enable-static --disable-shared \
    --disable-tls \
    --disable-uuidd \
    --disable-e2initrd-helper \
    CC="$CC" AR="$AR" RANLIB="$RANLIB" \
    CFLAGS="-O2 -fPIC"

echo "=== [3/3] Build + install debugfs, mke2fs ==="
make -j"$(nproc)"
require_files "$E2FS_WORK/e2fsprogs/debugfs/debugfs" \
              "$E2FS_WORK/e2fsprogs/misc/mke2fs"
file "$E2FS_WORK/e2fsprogs/debugfs/debugfs"
file "$E2FS_WORK/e2fsprogs/misc/mke2fs"
install_jnilib "$E2FS_WORK/e2fsprogs/debugfs/debugfs" debugfs
install_jnilib "$E2FS_WORK/e2fsprogs/misc/mke2fs" mke2fs

echo "=== e2fsprogs DONE ==="

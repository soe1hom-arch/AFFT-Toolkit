#!/bin/bash
# Build mkfs.erofs + dump.erofs from erofs-utils for Android ARM64.
# Source: https://git.kernel.org/pub/scm/linux/kernel/git/xiang/erofs-utils (GPL-2.0)
# Pinned ref: see tools/build_config.sh (EROFS_UTILS_REF).
# dump.erofs is shipped as "extract.erofs" (name expected by the app).
# Output: app/src/main/jniLibs/<arch>/lib{mkfs.erofs,extract.erofs}.so
#
# Env: NDK_DIR (required), SRC_DIR (repo root). See tools/build_config.sh.
# Requires: autoconf, automake, libtool.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="${SRC_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
export SRC_DIR
# shellcheck source=build_config.sh
source "$SCRIPT_DIR/build_config.sh"
require_ndk

# Static C deps (liblzma, libzstd, liblz4) with pkg-config files.
bash "$SCRIPT_DIR/build_deps.sh"

for tool in autoreconf aclocal libtool pkg-config; do
    command -v "$tool" >/dev/null 2>&1 || {
        echo "ERROR: required build tool '$tool' not found. Install autoconf/automake/libtool/pkg-config." >&2
        exit 1
    }
done

EROFS_WORK="$WORK_DIR/erofs-utils"
mkdir -p "$EROFS_WORK"

echo "=== [1/3] Fetch erofs-utils @ $EROFS_UTILS_REF ==="
fetch_ref "$EROFS_UTILS_URL" "$EROFS_WORK/erofs-utils" "$EROFS_UTILS_REF"
cd "$EROFS_WORK/erofs-utils"

echo "=== [2/3] Configure for android/arm64 (static, no FUSE daemon) ==="
export PKG_CONFIG_PATH="$DEPS_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"
export PKG_CONFIG="pkg-config"
./autogen.sh
./configure --host=aarch64-linux-android \
    --enable-static --disable-shared \
    --disable-fuse \
    --without-uuid \
    CC="$CC" AR="$AR" RANLIB="$RANLIB" \
    CFLAGS="-O2 -fPIC -I$DEPS_PREFIX/include" \
    LDFLAGS="-L$DEPS_PREFIX/lib -static"

echo "=== [3/3] Build + install mkfs.erofs, dump.erofs ==="
make -j"$(nproc)"
MKFS_BIN="$(find "$EROFS_WORK/erofs-utils" -name mkfs.erofs -type f | head -n 1)"
DUMP_BIN="$(find "$EROFS_WORK/erofs-utils" -name dump.erofs -type f | head -n 1)"
require_files "${MKFS_BIN:-/nonexistent/mkfs.erofs}" "${DUMP_BIN:-/nonexistent/dump.erofs}"
file "$MKFS_BIN"
file "$DUMP_BIN"
install_jnilib "$MKFS_BIN" mkfs.erofs
install_jnilib "$DUMP_BIN" extract.erofs

echo "=== erofs-utils DONE ==="

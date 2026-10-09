#!/bin/bash
# Build static C dependencies (liblzma, libzstd, liblz4) for Android ARM64.
# Installed into $WORK_DIR/deps with pkg-config files so downstream builds
# (payload-dumper-go CGO, erofs-utils) can discover them.
#
# Env: NDK_DIR (required), SRC_DIR (repo root). See tools/build_config.sh.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="${SRC_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
export SRC_DIR
# shellcheck source=build_config.sh
source "$SCRIPT_DIR/build_config.sh"
require_ndk

DEPS_WORK="$WORK_DIR/deps-src"
mkdir -p "$DEPS_WORK"
cd "$DEPS_WORK"

export PKG_CONFIG_PATH="$DEPS_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"

echo "=== [deps/1] liblzma.a (xz $XZ_VER) ==="
if [ -f "$DEPS_PREFIX/lib/liblzma.a" ]; then
    echo "liblzma.a cached"
else
    [ -f "xz-${XZ_VER}.tar.xz" ] || curl -sLo "xz-${XZ_VER}.tar.xz" \
        "https://github.com/tukaani-project/xz/releases/download/v${XZ_VER}/xz-${XZ_VER}.tar.xz"
    rm -rf "xz-${XZ_VER}"
    tar -xf "xz-${XZ_VER}.tar.xz"
    cd "xz-${XZ_VER}"
    ./configure --host=aarch64-linux-android \
        --prefix="$DEPS_PREFIX" CC="$CC" AR="$AR" \
        --enable-static --disable-shared \
        --disable-xz --disable-xzdec --disable-lzmadec \
        --disable-lzmainfo --disable-lzma-links \
        --disable-scripts --disable-doc --with-pic
    make -j"$(nproc)"
    make install
    cd "$DEPS_WORK"
fi

echo "=== [deps/2] libzstd.a ($ZSTD_REF) ==="
if [ -f "$DEPS_PREFIX/lib/libzstd.a" ]; then
    echo "libzstd.a cached"
else
    rm -rf zstd
    git clone --depth=1 --branch "$ZSTD_REF" https://github.com/facebook/zstd.git
    CC="$CC" AR="$AR" RANLIB="$RANLIB" \
        make -C zstd/lib -j"$(nproc)" libzstd.a
    mkdir -p "$DEPS_PREFIX/lib" "$DEPS_PREFIX/include"
    cp zstd/lib/libzstd.a "$DEPS_PREFIX/lib/"
    cp zstd/lib/zstd.h zstd/lib/zdict.h zstd/lib/zstd_errors.h "$DEPS_PREFIX/include/"
    cat > "$DEPS_PREFIX/lib/pkgconfig/libzstd.pc" <<PC
prefix=$DEPS_PREFIX
exec_prefix=\${prefix}
libdir=\${exec_prefix}/lib
includedir=\${prefix}/include

Name: zstd
Description: Zstandard compression library
Version: 1.5.5
Libs: -L\${libdir} -lzstd
Cflags: -I\${includedir}
PC
fi

echo "=== [deps/3] liblz4.a ($LZ4_REF, for erofs-utils) ==="
if [ -f "$DEPS_PREFIX/lib/liblz4.a" ]; then
    echo "liblz4.a cached"
else
    rm -rf lz4
    git clone --depth=1 --branch "$LZ4_REF" https://github.com/lz4/lz4.git
    CC="$CC" AR="$AR" RANLIB="$RANLIB" \
        make -C lz4/lib -j"$(nproc)" liblz4.a
    mkdir -p "$DEPS_PREFIX/lib" "$DEPS_PREFIX/include"
    cp lz4/lib/liblz4.a "$DEPS_PREFIX/lib/"
    cp lz4/lib/lz4.h lz4/lib/lz4hc.h lz4/lib/lz4frame.h "$DEPS_PREFIX/include/"
    cat > "$DEPS_PREFIX/lib/pkgconfig/liblz4.pc" <<PC
prefix=$DEPS_PREFIX
exec_prefix=\${prefix}
libdir=\${exec_prefix}/lib
includedir=\${prefix}/include

Name: LZ4
Description: Extremely Fast Compression algorithm
Version: 1.9.4
Libs: -L\${libdir} -llz4
Cflags: -I\${includedir}
PC
fi

echo "=== deps DONE ==="
ls -lh "$DEPS_PREFIX/lib"/liblzma.a "$DEPS_PREFIX/lib"/libzstd.a "$DEPS_PREFIX/lib"/liblz4.a

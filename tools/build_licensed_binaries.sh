#!/bin/bash
# Refresh the GPL-covered native tools from pinned sources before packaging.

set -euo pipefail

: "${NDK_DIR:?Must set NDK_DIR to Android NDK path}"
: "${SRC_DIR:?Must set SRC_DIR to repo root}"
: "${WORK_DIR:=/tmp/afft-licensed-build}"

ARCH="arm64-v8a"
E2FSPROGS_VERSION="v1.47.0"
E2FSPROGS_COMMIT="25ad8a431331b4d1d444a70b6079456cc612ac40"
TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"
CC="$TOOLCHAIN/bin/aarch64-linux-android26-clang"
AR="$TOOLCHAIN/bin/llvm-ar"
RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
JNILIBS_DIR="${SRC_DIR}/app/src/main/jniLibs/${ARCH}"
mkdir -p "$JNILIBS_DIR" "$WORK_DIR"

if [ ! -x "$CC" ]; then
    echo "ERROR: Android NDK compiler not found: $CC" >&2
    exit 1
fi

echo "=== Build payload-dumper-go from pinned source ==="
NDK_DIR="$NDK_DIR" SRC_DIR="$SRC_DIR" WORK_DIR="$WORK_DIR/payload" \
    bash "$SRC_DIR/tools/build_payload_dumper.sh"

echo "=== Build debugfs from e2fsprogs $E2FSPROGS_VERSION ==="
E2FS_DIR="$WORK_DIR/e2fsprogs-$E2FSPROGS_VERSION"
if [ ! -d "$E2FS_DIR/.git" ]; then
    git clone https://github.com/tytso/e2fsprogs.git "$E2FS_DIR"
fi
git -C "$E2FS_DIR" fetch --depth=1 origin "$E2FSPROGS_COMMIT"
git -C "$E2FS_DIR" checkout --detach "$E2FSPROGS_COMMIT"
if [ "$(git -C "$E2FS_DIR" rev-parse HEAD)" != "$E2FSPROGS_COMMIT" ]; then
    echo "ERROR: e2fsprogs source revision mismatch." >&2
    exit 1
fi
if [ -n "$(git -C "$E2FS_DIR" status --porcelain)" ]; then
    echo "ERROR: e2fsprogs source tree is modified." >&2
    exit 1
fi
E2FS_PATCH="$SRC_DIR/tools/debugfs-fs-config.patch"
git -C "$E2FS_DIR" apply --check "$E2FS_PATCH"
git -C "$E2FS_DIR" apply "$E2FS_PATCH"
(
    cd "$E2FS_DIR"
    ./configure --host=aarch64-linux-android \
        --enable-static --disable-shared \
        --disable-tls --disable-uuidd --disable-e2initrd-helper \
        CC="$CC" AR="$AR" RANLIB="$RANLIB" \
        CFLAGS="-O2 -fPIC"
    make -j"$(nproc)" libs
    make -C debugfs -j"$(nproc)" debugfs
)
install -m 755 "$E2FS_DIR/debugfs/debugfs" "$JNILIBS_DIR/libdebugfs.so"
readelf -h "$JNILIBS_DIR/libdebugfs.so" | grep -q 'AArch64'

echo "=== Test patched debugfs fs_config extraction ==="
E2FS_HOST_DIR="$WORK_DIR/e2fsprogs-host-$E2FSPROGS_VERSION"
mkdir -p "$E2FS_HOST_DIR"
git -C "$E2FS_DIR" archive "$E2FSPROGS_COMMIT" | tar -x -C "$E2FS_HOST_DIR"
git -C "$E2FS_HOST_DIR" apply "$E2FS_PATCH"
(
    cd "$E2FS_HOST_DIR"
    ./configure --disable-tls --disable-uuidd --disable-e2initrd-helper CFLAGS="-std=gnu17"
    make -j"$(nproc)" libs
    make -C debugfs -j"$(nproc)" debugfs
)
bash "$SRC_DIR/tools/test_debugfs_fs_config.sh" "$E2FS_HOST_DIR/debugfs/debugfs"

echo "=== Build EROFS Tools from pinned source ==="
NDK_DIR="$NDK_DIR" SRC_DIR="$SRC_DIR" WORK_DIR="$WORK_DIR/erofs" \
    bash "$SRC_DIR/tools/build_erofs.sh"

echo "=== Licensed native binaries refreshed ==="

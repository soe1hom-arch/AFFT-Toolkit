#!/bin/bash
# Refresh the GPL-covered native tools from pinned sources before packaging.

set -euo pipefail

: "${NDK_DIR:?Must set NDK_DIR to Android NDK path}"
: "${SRC_DIR:?Must set SRC_DIR to repo root}"
: "${WORK_DIR:=/tmp/afft-licensed-build}"

ARCH="arm64-v8a"
E2FSPROGS_VERSION="v1.47.0"
E2FSPROGS_COMMIT="25ad8a431331b4d1d444a70b6079456cc612ac40"
EROFS_TAG="v1.8.10-251217"
EROFS_ASSET="erofs-utils-v1.8.10-gee46dd74-251217-Android_arm64-v8a.zip"
EROFS_SHA256="986a3880b7afc0b5efcc58f75268f2c9d03d86773ae8b7ab2c8692215740e21e"
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
(
    cd "$E2FS_DIR"
    ./configure --host=aarch64-linux-android \
        --enable-static --disable-shared \
        --disable-tls --disable-uuidd --disable-e2initrd-helper \
        CC="$CC" AR="$AR" RANLIB="$RANLIB" \
        CFLAGS="-O2 -fPIC"
    make -j"$(nproc)"
)
install -m 755 "$E2FS_DIR/debugfs/debugfs" "$JNILIBS_DIR/libdebugfs.so"
readelf -h "$JNILIBS_DIR/libdebugfs.so" | grep -q 'AArch64'

echo "=== Install verified EROFS Tools release $EROFS_TAG ==="
EROFS_DIR="$WORK_DIR/erofs-$EROFS_TAG"
mkdir -p "$EROFS_DIR"
curl --fail --location --silent --show-error \
    "https://github.com/sekaiacg/erofs-tools/releases/download/${EROFS_TAG}/${EROFS_ASSET}" \
    -o "$EROFS_DIR/$EROFS_ASSET"
echo "$EROFS_SHA256  $EROFS_DIR/$EROFS_ASSET" | sha256sum --check
unzip -p "$EROFS_DIR/$EROFS_ASSET" mkfs.erofs \
    > "$JNILIBS_DIR/libmkfs.erofs.so"
unzip -p "$EROFS_DIR/$EROFS_ASSET" extract.erofs \
    > "$JNILIBS_DIR/libextract.erofs.so"
chmod 755 "$JNILIBS_DIR/libmkfs.erofs.so" "$JNILIBS_DIR/libextract.erofs.so"
echo "e724b80d259d6c2947b9374a668eb5a09e5a8e4400ba6708aa5605a52e6adfb8  $JNILIBS_DIR/libmkfs.erofs.so" | sha256sum --check
echo "13fa27c5626766607e8ce5c58b1ba1710709cea732d8d380a57449db39b2eae0  $JNILIBS_DIR/libextract.erofs.so" | sha256sum --check
readelf -h "$JNILIBS_DIR/libmkfs.erofs.so" | grep -q 'AArch64'
readelf -h "$JNILIBS_DIR/libextract.erofs.so" | grep -q 'AArch64'

echo "=== Licensed native binaries refreshed ==="

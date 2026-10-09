#!/bin/bash
# Build payload-dumper-go from source for Android ARM64.
# Source: https://github.com/ssut/payload-dumper-go (GPL-3.0)
# Pinned ref: see tools/build_config.sh (PAYLOAD_DUMPER_GO_REF).
# Output: app/src/main/jniLibs/<arch>/libpayload-dumper-go.so
#
# Env: NDK_DIR (required), SRC_DIR (repo root). See tools/build_config.sh.
# Requires: go toolchain, curl, make.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="${SRC_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
export SRC_DIR
# shellcheck source=build_config.sh
source "$SCRIPT_DIR/build_config.sh"
require_ndk

# Static C deps (liblzma.a, libzstd.a) with pkg-config files.
bash "$SCRIPT_DIR/build_deps.sh"

PAYLOAD_WORK="$WORK_DIR/payload-dumper"
mkdir -p "$PAYLOAD_WORK"
export GOMODCACHE="${PAYLOAD_WORK}/go-mod-cache"
mkdir -p "$GOMODCACHE"

echo "=== [1/4] Fetch payload-dumper-go @ $PAYLOAD_DUMPER_GO_REF ==="
fetch_ref "$PAYLOAD_DUMPER_GO_URL" \
    "$PAYLOAD_WORK/payload-dumper-go" "$PAYLOAD_DUMPER_GO_REF"
cd "$PAYLOAD_WORK/payload-dumper-go"

echo "=== [2/4] Apply CGO patch (native liblzma via tools/cgo_lzma.go) ==="
sed -i '/"github.com\/ulikunitz\/xz"/d' payload.go 2>/dev/null || true
sed -i 's/xz\.NewReader(teeReader)/newXzReader(teeReader)/g' payload.go 2>/dev/null || true
go mod edit -droprequire github.com/ulikunitz/xz 2>/dev/null || true
if [ ! -f cgo_lzma.go ]; then
    cp "$SRC_DIR/tools/cgo_lzma.go" ./
fi

echo "=== [3/4] go mod tidy + patch gozstd static lib ($GOZSTD_VER) ==="
go mod tidy 2>&1 | tail -n 3
GOZSTD_DIR="$GOMODCACHE/github.com/valyala/gozstd@$GOZSTD_VER"
if [ -d "$GOZSTD_DIR" ]; then
    chmod -R u+w "$GOZSTD_DIR" 2>/dev/null || true
    rm -f "$GOZSTD_DIR"/libzstd_linux_arm64.a
    cp "$DEPS_PREFIX/lib/libzstd.a" "$GOZSTD_DIR/libzstd_linux_arm64.a"
    echo "gozstd patched: $(ls "$GOZSTD_DIR"/libzstd_*.a)"
else
    echo "WARNING: gozstd $GOZSTD_VER not in module cache yet; build may link host lib"
    find "$GOMODCACHE" -maxdepth 4 -name "gozstd*" -type d 2>/dev/null || true
fi

echo "=== [4/4] Compile with CGO for android/arm64 ==="
export GO111MODULE=on
export CGO_ENABLED=1
export GOOS=android
export GOARCH=arm64
export CGO_CFLAGS="-I$DEPS_PREFIX/include -O2"
export CGO_LDFLAGS="-L$DEPS_PREFIX/lib -l:liblzma.a -l:libzstd.a"

go build -ldflags='-s -w' -o payload-dumper-go .
file payload-dumper-go
install_jnilib payload-dumper-go payload-dumper-go

echo "=== payload-dumper-go DONE ==="

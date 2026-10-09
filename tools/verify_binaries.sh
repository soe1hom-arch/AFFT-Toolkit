#!/bin/bash
# Verify that all required native helper binaries are present in jniLibs
# (built from source via tools/build_all.sh) and that no prebuilt binary
# is tracked by git (F-Droid requirement).
#
# Usage: tools/verify_binaries.sh [arch, default arm64-v8a]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="${SRC_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
ARCH="${1:-arm64-v8a}"
JNILIBS_DIR="$SRC_DIR/app/src/main/jniLibs/$ARCH"

# Must match BinaryManager.REQUIRED_BINARIES (name -> lib<name>.so).
REQUIRED="debugfs lpunpack extract.erofs lpmake magiskboot mke2fs mkfs.erofs payload-dumper-go simg2img"

fail=0

echo "--- Checking jniLibs ($ARCH) ---"
for name in $REQUIRED; do
    f="$JNILIBS_DIR/lib${name}.so"
    if [ -s "$f" ]; then
        echo "OK    $name ($(du -h "$f" | cut -f1))"
    else
        echo "MISS  $name ($f not built — run tools/build_all.sh)" >&2
        fail=1
    fi
done

echo "--- Checking no binaries are tracked by git ---"
tracked="$(git -C "$SRC_DIR" ls-files | grep -E 'jniLibs/|assets/bin/' | grep -v '.gitkeep$' || true)"
if [ -n "$tracked" ]; then
    echo "FAIL: prebuilt binaries tracked by git:" >&2
    echo "$tracked" >&2
    fail=1
else
    echo "OK    no binaries tracked by git"
fi

if [ "$fail" -ne 0 ]; then
    echo "VERIFY FAILED" >&2
    exit 1
fi
echo "VERIFY OK"

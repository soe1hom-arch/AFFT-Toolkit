#!/bin/bash
# Build ALL AFFT native helper binaries from pinned upstream sources.
# Usage:
#   NDK_DIR=/path/to/ndk SRC_DIR=/path/to/AFFT-Toolkit tools/build_all.sh [target...]
# Targets: payload aosp e2fsprogs erofs magiskboot (default: all)
#   tools/build_all.sh --check   # verify pinned refs exist upstream (no NDK needed)
#
# Output: app/src/main/jniLibs/<arch>/lib<name>.so (9 files, see tools/README.md).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="${SRC_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
export SRC_DIR

check_ref() {
    local url="$1"
    local ref="$2"
    local what="$3"
    if git ls-remote --tags --heads "$url" 2>/dev/null | grep -E "refs/(tags|heads)/$ref$" >/dev/null; then
        echo "OK    $what: $ref"
    else
        echo "FAIL  $what: ref '$ref' not found at $url" >&2
        return 1
    fi
}

if [ "${1:-}" = "--check" ]; then
    # shellcheck source=build_config.sh
    # shellcheck source=build_config.sh
    source "$SCRIPT_DIR/build_config.sh"
    fail=0
    check_ref "$PAYLOAD_DUMPER_GO_URL" "$PAYLOAD_DUMPER_GO_REF" "payload-dumper-go" || fail=1
    check_ref "$MAGISK_URL" "$MAGISK_REF" "magiskboot" || fail=1
    check_ref "$AOSP_CORE_URL" "$AOSP_REF" "aosp/system_core" || fail=1
    check_ref "$AOSP_EXTRAS_URL" "$AOSP_REF" "aosp/system_extras" || fail=1
    check_ref "$AOSP_BASE_URL" "$AOSP_REF" "aosp/system_libbase" || fail=1
    check_ref "$EROFS_UTILS_URL" "$EROFS_UTILS_REF" "erofs-utils" || fail=1
    check_ref "$E2FSPROGS_URL" "$E2FSPROGS_REF" "e2fsprogs" || fail=1
    check_ref "https://github.com/tukaani-project/xz.git" "v$XZ_VER" "xz" || fail=1
    check_ref "https://github.com/facebook/zstd.git" "$ZSTD_REF" "zstd" || fail=1
    check_ref "https://github.com/lz4/lz4.git" "$LZ4_REF" "lz4" || fail=1
    if [ "$fail" -ne 0 ]; then
        echo "REF CHECK FAILED" >&2
        exit 1
    fi
    echo "ALL REFS OK"
    exit 0
fi

: "${NDK_DIR:?Must set NDK_DIR to Android NDK path}"

TARGETS="${*:-payload aosp e2fsprogs erofs magiskboot}"
for t in $TARGETS; do
    case "$t" in
        payload)  bash "$SCRIPT_DIR/build_payload_dumper.sh" ;;
        aosp)     bash "$SCRIPT_DIR/build_aosp.sh" ;;
        e2fsprogs) bash "$SCRIPT_DIR/build_e2fsprogs.sh" ;;
        erofs)    bash "$SCRIPT_DIR/build_erofs.sh" ;;
        magiskboot) bash "$SCRIPT_DIR/build_magiskboot.sh" ;;
        *) echo "ERROR: unknown target '$t' (payload|aosp|e2fsprogs|erofs|magiskboot)" >&2; exit 1 ;;
    esac
done

echo ""
echo "=== Verifying installed jniLibs ==="
bash "$SCRIPT_DIR/verify_binaries.sh"

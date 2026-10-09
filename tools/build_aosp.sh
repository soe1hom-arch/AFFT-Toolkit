#!/bin/bash
# Build AOSP host tools (lpmake, lpunpack, simg2img) from source for ARM64.
# Sources (Apache-2.0), all pinned to $AOSP_REF (see tools/build_config.sh):
#   system/core    -> fs_mgr/liblp (liblp) + libsparse
#   system/extras  -> partition_tools (lpmake.cc, lpunpack.cc)
#   system/libbase -> android-base (logging/file/strings/...)
# Output: app/src/main/jniLibs/<arch>/lib{lpmake,lpunpack,simg2img}.so
#
# Env: NDK_DIR (required), SRC_DIR (repo root). See tools/build_config.sh.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="${SRC_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
export SRC_DIR
# shellcheck source=build_config.sh
source "$SCRIPT_DIR/build_config.sh"
require_ndk

AOSP_WORK="$WORK_DIR/aosp"
mkdir -p "$AOSP_WORK"

echo "=== [1/4] Fetch AOSP @ $AOSP_REF ==="
fetch_ref "$AOSP_CORE_URL"   "$AOSP_WORK/system_core"   "$AOSP_REF"
fetch_ref "$AOSP_EXTRAS_URL" "$AOSP_WORK/system_extras" "$AOSP_REF"
fetch_ref "$AOSP_BASE_URL"   "$AOSP_WORK/system_libbase" "$AOSP_REF"

CORE="$AOSP_WORK/system_core"
EXTRAS="$AOSP_WORK/system_extras"
BASE="$AOSP_WORK/system_libbase"

echo "=== [2/4] Assert expected source layout ==="
require_files \
    "$EXTRAS/partition_tools/lpmake.cc" \
    "$EXTRAS/partition_tools/lpunpack.cc" \
    "$CORE/libsparse/simg2img.cpp" \
    "$CORE/libsparse/include/sparse/sparse.h" \
    "$CORE/fs_mgr/liblp/include/liblp/liblp.h" \
    "$BASE/include/android-base/logging.h" \
    "$BASE/include/android-base/unique_fd.h" \
    "$BASE/include/android-base/parseint.h"

INCLUDES=(
    "-I$CORE/libsparse/include"
    "-I$CORE/fs_mgr/liblp/include"
    "-I$CORE/fs_mgr/include"
    "-I$BASE/include"
)
CXXFLAGS=(-std=c++17 -O2 -Wall -D_FILE_OFFSET_BITS=64)

OBJDIR="$AOSP_WORK/obj"
rm -rf "$OBJDIR"
mkdir -p "$OBJDIR"

compile() {
    local src="$1"
    local obj="$OBJDIR/$(echo "$src" | tr '/.' '__').o"
    # Compiler stdout goes to stderr so $(compile) captures only the obj path.
    "$CXX" "${CXXFLAGS[@]}" "${INCLUDES[@]}" -c "$src" -o "$obj" >&2
    echo "$obj"
}

echo "=== [3/4] Compile libbase subset ==="
BASE_OBJS=""
for f in file logging mapped_file properties stringprintf strings \
         errors_unix chrono_utils threads hex utf8 parsebool; do
    require_files "$BASE/$f.cpp"
    BASE_OBJS="$BASE_OBJS $(compile "$BASE/$f.cpp")"
done

echo "=== [3/4] Compile libsparse ==="
SPARSE_OBJS=""
for f in backed_block output_file sparse sparse_crc32 sparse_err sparse_read; do
    require_files "$CORE/libsparse/$f.cpp"
    SPARSE_OBJS="$SPARSE_OBJS $(compile "$CORE/libsparse/$f.cpp")"
done

echo "=== [3/4] Compile liblp ==="
LP_OBJS=""
for f in builder images partition_opener property_fetcher reader \
         super_layout_builder utility writer; do
    require_files "$CORE/fs_mgr/liblp/$f.cpp"
    LP_OBJS="$LP_OBJS $(compile "$CORE/fs_mgr/liblp/$f.cpp")"
done

link_tool() {
    local main="$1"
    local name="$2"
    local extralibs="$3"
    local main_obj
    main_obj=$(compile "$main")
    # shellcheck disable=SC2086
    "$CXX" -O2 -static-libstdc++ $main_obj $extralibs \
        -llog -o "$AOSP_WORK/$name"
    file "$AOSP_WORK/$name"
    test -s "$AOSP_WORK/$name"
    install_jnilib "$AOSP_WORK/$name" "$name"
}

echo "=== [4/4] Link lpmake, lpunpack, simg2img ==="
link_tool "$EXTRAS/partition_tools/lpmake.cc"   lpmake   "$LP_OBJS $SPARSE_OBJS $BASE_OBJS"
link_tool "$EXTRAS/partition_tools/lpunpack.cc" lpunpack "$LP_OBJS $SPARSE_OBJS $BASE_OBJS"
link_tool "$CORE/libsparse/simg2img.cpp"        simg2img "$SPARSE_OBJS $BASE_OBJS"

echo "=== AOSP tools DONE ==="

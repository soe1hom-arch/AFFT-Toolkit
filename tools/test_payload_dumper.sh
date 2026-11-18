#!/bin/bash
set -euo pipefail

: "${SRC_DIR:?Must set SRC_DIR to repo root}"
binary="${1:?Pass the host payload dumper binary}"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT

go run "$SRC_DIR/tools/test_payload_fixture.go" "$tmp/payload.bin"
go run "$SRC_DIR/tools/test_payload_fixture.go" "$tmp/bad.bin" bad-hash

"$binary" -o "$tmp/good" -c 1 "$tmp/payload.bin" >"$tmp/good.log" 2>"$tmp/good.err"
grep -q 'Number of workers: 1' "$tmp/good.log"
grep -qx 'AFFT_PAYLOAD_EXTRACTION_COMPLETE' "$tmp/good.err"
test "$(wc -c < "$tmp/good/system.img")" -eq 4096

if "$binary" -o "$tmp/bad" -c 2 "$tmp/bad.bin" >"$tmp/bad.log" 2>"$tmp/bad.err"; then
    echo "Bad checksum was reported as success" >&2
    exit 1
fi
grep -q 'Checksum mismatch' "$tmp/bad.err"
! grep -q 'AFFT_PAYLOAD_EXTRACTION_COMPLETE' "$tmp/bad.err"

if "$binary" -o "$tmp/good" -c 1 "$tmp/payload.bin" >"$tmp/existing.log" 2>"$tmp/existing.err"; then
    echo "Existing image was overwritten" >&2
    exit 1
fi
grep -q 'file exists' "$tmp/existing.err"
test "$(wc -c < "$tmp/good/system.img")" -eq 4096

printf 'invalid' > "$tmp/invalid.bin"
if "$binary" -o "$tmp/invalid" -c 1 "$tmp/invalid.bin" >"$tmp/invalid.log" 2>"$tmp/invalid.err"; then
    echo "Invalid payload was reported as success" >&2
    exit 1
fi
grep -q 'Invalid payload magic' "$tmp/invalid.err"
echo "Payload dumper integration tests passed"

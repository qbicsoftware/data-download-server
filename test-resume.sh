#!/usr/bin/env bash
#
# Test resumable download by randomly interrupting a file transfer and
# verifying that resuming from the last byte count yields the exact file.
#
# Usage:
#   ./test-resume.sh <measurement-id> <access-token> <index> [iterations] [base-url]
#
# Examples:
#   ./test-resume.sh NGSQ0001006AO-25948529211108 $TOKEN 0
#   ./test-resume.sh NGSQ0001006AO-25948529211108 $TOKEN 2 5 https://localhost:8090
#
# Modes:
#   DEFAULT   - download a random prefix, then resume from the measured size.
#               Deterministic and fast; the interruptions happen between reads.
#   MODE=kill - start a full download, kill curl after a random delay, then
#               resume from the measured partial size. More realistic (abort
#               mid-stream) but timing-dependent.
#
# Verifies that the final byte count equals the manifest length AND that the
# CRC-32 of the assembled file matches the manifest value.
#
# Requires: curl, jq, awk (for random numbers), and a crc32 tool or python3.

set -euo pipefail

if [ "$#" -lt 3 ] || [ "$#" -gt 5 ]; then
  echo "Usage: $0 <measurement-id> <access-token> <index> [iterations] [base-url]" >&2
  exit 1
fi

MEASUREMENT_ID="$1"
ACCESS_TOKEN="$2"
INDEX="$3"
ITERATIONS="${4:-1}"
BASE_URL="${5:-http://localhost:8090}"
MODE="${MODE:-prefix}"
INSECURE="${INSECURE:-false}"

CURL_OPTS=(-H "Authorization: Bearer ${ACCESS_TOKEN}" --fail --silent --show-error)
if [ "${INSECURE}" = "true" ]; then
  CURL_OPTS+=(-k)
fi

OUT_FILE="resume-test-${MEASUREMENT_ID}-${INDEX}.bin"

# --- 1. Fetch the manifest and resolve the target file -----------------------
MANIFEST="$(curl "${CURL_OPTS[@]}" "${BASE_URL}/measurements/${MEASUREMENT_ID}/files")"
EXPECTED_LEN="$(jq -r --argjson i "$INDEX" '.files[$i].length' <<<"${MANIFEST}")"
EXPECTED_CRC="$(jq -r --argjson i "$INDEX" '.files[$i].crc32' <<<"${MANIFEST}")"
DOWNLOAD_URL="${BASE_URL}$(jq -r --argjson i "$INDEX" '.files[$i]._links.download.href' <<<"${MANIFEST}")"

echo "measurement : ${MEASUREMENT_ID}"
echo "index       : ${INDEX}"
echo "expected len: ${EXPECTED_LEN} bytes"
echo "expected crc: ${EXPECTED_CRC}"
echo "url         : ${DOWNLOAD_URL}"
echo "mode        : ${MODE}"

# --- 2. Local crc32 (use python3 if no crc32 tool) ----------------------------
crc_of() {
  if command -v crc32 >/dev/null 2>&1; then
    crc32 "$1"
  else
    python3 -c "import sys,zlib;print('%08x'%(zlib.crc32(open(sys.argv[1],'rb').read())&0xffffffff))" "$1"
  fi
}

crc32_dec() {
  # crc tools print hex; the manifest crc32 is a decimal unsigned long.
  printf '%d' "0x$(crc_of "$1")"
}

# --- 3. Interrupt + resume loop ------------------------------------------------
rm -f "${OUT_FILE}"
CURRENT=0

for iter in $(seq 1 "${ITERATIONS}"); do
  if [ "${CURRENT}" -ge "${EXPECTED_LEN}" ]; then
    break
  fi

  # Pick a random interruption point strictly after the current offset.
  REMAIN=$((EXPECTED_LEN - CURRENT))
  if [ "${MODE}" = "kill" ]; then
    # Realistic: start a full download, kill it after a random delay.
    curl "${CURL_OPTS[@]}" \
      --output "${OUT_FILE}" "${DOWNLOAD_URL}" &
    CURL_PID=$!
    SLEEP="$((1 + (RANDOM % 4)))"
    sleep "${SLEEP}"
    kill "${CURL_PID}" 2>/dev/null || true
    wait "${CURL_PID}" 2>/dev/null || true
  else
    # Deterministic: request a random prefix via a range.
    if [ "${REMAIN}" -le 1 ]; then
      STEP=${REMAIN}
    else
      STEP="$((1 + (RANDOM % REMAIN)))"
    fi
    END=$((CURRENT + STEP - 1))
    curl "${CURL_OPTS[@]}" \
      -H "Range: bytes=${CURRENT}-${END}" \
      --output - "${DOWNLOAD_URL}" >>"${OUT_FILE}"
  fi

  NEW_SIZE="$(stat -c %s "${OUT_FILE}")"
  echo "  [${iter}] interrupted at byte ${CURRENT}; file now ${NEW_SIZE}/${EXPECTED_LEN}"
  if [ "${NEW_SIZE}" -le "${CURRENT}" ]; then
    echo "  !! no progress made; aborting to avoid an infinite loop" >&2
    exit 1
  fi
  CURRENT="${NEW_SIZE}"
done

# --- 4. Resume the remainder (if any) and verify -------------------------------
if [ "${CURRENT}" -lt "${EXPECTED_LEN}" ]; then
  echo "resuming final ${EXPECTED_LEN} - ${CURRENT} bytes"
  curl "${CURL_OPTS[@]}" \
    -H "Range: bytes=${CURRENT}-" \
    --output - "${DOWNLOAD_URL}" >>"${OUT_FILE}"
fi

FINAL_LEN="$(stat -c %s "${OUT_FILE}")"
FINAL_CRC="$(crc32_dec "${OUT_FILE}")"

echo "----------------------------------------"
echo "final len : ${FINAL_LEN}  (expected ${EXPECTED_LEN})"
echo "final crc : ${FINAL_CRC}  (expected ${EXPECTED_CRC})"

if [ "${FINAL_LEN}" -eq "${EXPECTED_LEN}" ] && [ "${FINAL_CRC}" -eq "${EXPECTED_CRC}" ]; then
  echo "PASS: resumed file is byte-for-byte correct."
  exit 0
else
  echo "FAIL: resumed file does not match the manifest." >&2
  exit 1
fi
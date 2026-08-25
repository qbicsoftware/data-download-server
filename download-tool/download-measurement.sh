#!/usr/bin/env bash
#
# Download all files of a measurement without zipping, restoring the directory
# structure described by the manifest and resuming interrupted downloads.
#
# Usage:
#   ./download-measurement.sh <measurement-id> <access-token> [target-dir]
#
# Examples:
#   ./download-measurement.sh NGSQ0001006AO-25948529211108 2g2Yd84mn1n63068V67KQul9DQG5N4oR
#   ./download-measurement.sh NGSQ0001006AO-25948529211108 $TOKEN ./my-measurement
#
# The script:
#   1. fetches the manifest listing the measurement's files in stable order
#   2. for each file, creates the local directory implied by the manifest `path`
#      value so the remote folder structure is reproduced locally
#   3. downloads each file; if a local file already exists, it is resumed from
#      its current size via an HTTP Range request (RFC 7233)
#   4. verifies every completed file against the CRC-32 checksum from the
#      manifest and retries a few times if the transfer aborts or the checksum
#      does not match
#
# The default base URL is the public download server. Override with BASE_URL.
#
# Requires: curl, jq

set -euo pipefail

# --- Configuration ------------------------------------------------------------

# Public download server.
BASE_URL="${BASE_URL:-https://download.qbic.uni-tuebingen.de}"
# Set to "true" to skip TLS certificate verification (self-signed certs).
INSECURE="${INSECURE:-false}"
# How many times a single file transfer is retried before giving up.
MAX_RETRIES="${MAX_RETRIES:-5}"

if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
  echo "Usage: $0 <measurement-id> <access-token> [target-dir]" >&2
  exit 1
fi

MEASUREMENT_ID="$1"
ACCESS_TOKEN="$2"
TARGET_DIR="${3:-.}"

CURL_OPTS=(-H "Authorization: Bearer ${ACCESS_TOKEN}" --fail --silent --show-error --location)
if [ "${INSECURE}" = "true" ]; then
  CURL_OPTS+=(-k)
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "Error: jq is required to parse the manifest." >&2
  exit 1
fi

MANIFEST_URL="${BASE_URL}/measurements/${MEASUREMENT_ID}/files"

echo "Fetching manifest from ${MANIFEST_URL}"
MANIFEST="$(curl "${CURL_OPTS[@]}" "${MANIFEST_URL}")"

COUNT="$(jq '.files | length' <<<"${MANIFEST}")"
echo "Found ${COUNT} file(s)."
echo "Target directory: ${TARGET_DIR}"
echo

# --- Local CRC-32 (fall back to python3 if no crc32 tool is present) ---------
crc_of() {
  if command -v crc32 >/dev/null 2>&1; then
    crc32 "$1"
  else
    python3 -c "import sys,zlib;print('%08x'%(zlib.crc32(open(sys.argv[1],'rb').read())&0xffffffff))" "$1"
  fi
}

crc32_dec() {
  # Manifest crc32 is an unsigned decimal long; crc tools print hex.
  printf '%d' "0x$(crc_of "$1")"
}

download_one() {
  local index="$1" local_path="$2" file_url="$3" expected_len="$4" expected_crc="$5"

  mkdir -p "$(dirname "${local_path}")"

  # If the file already exists, resume it via a Range request. Files that
  # already match the expected length are considered complete and skipped.
  local resume_from=0
  if [ -f "${local_path}" ]; then
    resume_from="$(stat -c %s "${local_path}" 2>/dev/null || echo 0)"
  fi

  if [ "${resume_from}" -ge "${expected_len}" ]; then
    echo "  [${index}/${COUNT}] ${local_path} (already complete, ${resume_from}/${expected_len} bytes)"
    return 0
  fi

  echo "  [${index}/${COUNT}] ${local_path} <- ${file_url} (${expected_len} bytes)"

  local attempt
  for attempt in $(seq 1 "${MAX_RETRIES}"); do
    resume_from="$(stat -c %s "${local_path}" 2>/dev/null || echo 0)"

    # Abort if the local file has grown beyond the expected size (e.g. a
    # corrupted/duplicated partial transfer); truncate and start over.
    if [ "${resume_from}" -gt "${expected_len}" ]; then
      echo "    local file larger than expected; restarting from scratch"
      : >"${local_path}"
      resume_from=0
    fi

    local status=0
    if [ "${resume_from}" -gt 0 ]; then
      curl "${CURL_OPTS[@]}" \
        -H "Range: bytes=${resume_from}-" \
        --output - "${file_url}" >>"${local_path}" || status=$?
    else
      curl "${CURL_OPTS[@]}" \
        --output "${local_path}" "${file_url}" || status=$?
    fi

    # Detect abort: curl exited non-zero or the file is still shorter than
    # the expected length.
    local size=0
    size="$(stat -c %s "${local_path}" 2>/dev/null || echo 0)"
    if [ "${status}" -ne 0 ] || [ "${size}" -lt "${expected_len}" ]; then
      echo "    transfer aborted at ${size}/${expected_len} bytes (attempt ${attempt}/${MAX_RETRIES}); will resume"
      continue
    fi

    # Verify the checksum once the length matches.
    if [ -n "${expected_crc}" ] && [ "${expected_crc}" != "0" ]; then
      local actual_crc
      actual_crc="$(crc32_dec "${local_path}")"
      if [ "${actual_crc}" -ne "${expected_crc}" ]; then
        echo "    checksum mismatch (got ${actual_crc}, expected ${expected_crc}); restarting file"
        : >"${local_path}"
        continue
      fi
    fi

    echo "    done (${size}/${expected_len} bytes)"
    return 0
  done

  echo "    giving up after ${MAX_RETRIES} attempts" >&2
  return 1
}

# --- Main loop ----------------------------------------------------------------

FAILED=0
for i in $(seq 0 $((COUNT - 1))); do
  PATH_AT_INDEX="$(jq -r --argjson i "$i" '.files[$i].path' <<<"${MANIFEST}")"
  LINK_HREF="$(jq -r --argjson i "$i" '.files[$i]._links.download.href' <<<"${MANIFEST}")"
  EXPECTED_LEN="$(jq -r --argjson i "$i" '.files[$i].length' <<<"${MANIFEST}")"
  EXPECTED_CRC="$(jq -r --argjson i "$i" '.files[$i].crc32' <<<"${MANIFEST}")"

  # Reconstruct the local path from the manifest `path` value. Strip any leading
  # slash and join with the target directory to reproduce the remote structure.
  REL_PATH="${PATH_AT_INDEX#/}"
  if [ -z "${REL_PATH}" ]; then
    REL_PATH="file-${i}"
  fi
  LOCAL_PATH="${TARGET_DIR}/${REL_PATH}"

  FILE_URL="${BASE_URL}${LINK_HREF}"

  if ! download_one "${i}" "${LOCAL_PATH}" "${FILE_URL}" "${EXPECTED_LEN}" "${EXPECTED_CRC}"; then
    FAILED=$((FAILED + 1))
  fi
done

if [ "${FAILED}" -gt 0 ]; then
  echo
  echo "Completed with ${FAILED} failed file(s)." >&2
  exit 1
fi

echo
echo "Done. All files downloaded to ${TARGET_DIR}."
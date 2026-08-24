#!/usr/bin/env bash
#
# Download all files of a measurement without zipping.
#
# Usage:
#   ./download-measurement.sh <measurement-id> <access-token> [base-url]
#
# Examples:
#   ./download-measurement.sh NGSQ0001006AO-25948529211108 2g2Yd84mn1n63068V67KQul9DQG5N4oR
#   ./download-measurement.sh NGSQ0001006AO-25948529211108 $TOKEN https://localhost:8090
#
# The script:
#   1. fetches the manifest listing the measurement's files in stable order
#   2. downloads each file into the current directory using its file name
#   3. resumes interrupted files by requesting the missing byte range (RFC 7233)
#
# Requires: curl, jq

set -euo pipefail

if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
  echo "Usage: $0 <measurement-id> <access-token> [base-url]" >&2
  exit 1
fi

MEASUREMENT_ID="$1"
ACCESS_TOKEN="$2"
BASE_URL="${3:-http://localhost:8090}"

# Set to "true" to skip TLS certificate verification (useful for self-signed local certs).
INSECURE="${INSECURE:-false}"

CURL_OPTS=(-H "Authorization: Bearer ${ACCESS_TOKEN}" --fail --silent --show-error)
if [ "${INSECURE}" = "true" ]; then
  CURL_OPTS+=(-k)
fi

MANIFEST_URL="${BASE_URL}/measurements/${MEASUREMENT_ID}/files"

echo "Fetching manifest from ${MANIFEST_URL}"
MANIFEST="$(curl "${CURL_OPTS[@]}" "${MANIFEST_URL}")"

if ! command -v jq >/dev/null 2>&1; then
  echo "Error: jq is required to parse the manifest." >&2
  exit 1
fi

COUNT="$(jq '.files | length' <<<"${MANIFEST}")"
echo "Found ${COUNT} file(s)."

for i in $(seq 0 $((COUNT - 1))); do
  PATH_AT_INDEX="$(jq -r --argjson i "$i" '.files[$i].path' <<<"${MANIFEST}")"
  LINK_HREF="$(jq -r --argjson i "$i" '.files[$i]._links.download.href' <<<"${MANIFEST}")"

  # Keep the final path segment as the local file name.
  FILE_NAME="$(basename "${PATH_AT_INDEX}")"
  if [ -z "${FILE_NAME}" ] || [ "${FILE_NAME}" = "/" ]; then
    FILE_NAME="file-${i}"
  fi

  FILE_URL="${BASE_URL}${LINK_HREF}"
  echo "[$i/$((COUNT - 1))] ${FILE_NAME} <- ${FILE_URL}"

  # If the file already exists, resume it via a Range request.
  if [ -f "${FILE_NAME}" ]; then
    RESUME_FROM="$(stat -c %s "${FILE_NAME}" 2>/dev/null || echo 0)"
    echo "  resuming from byte ${RESUME_FROM}"
    curl "${CURL_OPTS[@]}" \
      -H "Range: bytes=${RESUME_FROM}-" \
      --output - "${FILE_URL}" >>"${FILE_NAME}"
  else
    curl "${CURL_OPTS[@]}" \
      --output "${FILE_NAME}" "${FILE_URL}"
  fi
done

echo "Done. All files downloaded to the current directory."
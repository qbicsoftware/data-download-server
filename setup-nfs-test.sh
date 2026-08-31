#!/bin/bash
# Setup script for testing OpenBisNfsStorageProvider locally
#
# This script creates a test directory structure matching openBIS file paths
# and sets environment variables for local testing.
#
# Usage:
#   ./setup-nfs-test.sh <measurement-id>
#
# Example:
#   ./setup-nfs-test.sh NGSQ27O50001A0-1481421688841090

set -e

if [ $# -lt 1 ]; then
  echo "Usage: $0 <measurement-id>"
  echo "Example: $0 NGSQ27O50001A0-1481421688841090"
  exit 1
fi

MEASUREMENT_ID="$1"
TEST_DIR="/tmp/openbis-nfs-test"

echo "Setting up NFS test environment for measurement: $MEASUREMENT_ID"
echo "Test directory: $TEST_DIR"

# Create test directory
mkdir -p "$TEST_DIR"

# Fetch the manifest to get file paths
echo "Fetching manifest..."
MANIFEST=$(curl -s -H "Authorization: Bearer ${ACCESS_TOKEN:-6lh19z738279j9737jdxtaJn1h9W6K9Z}" \
  "http://localhost:8090/measurements/$MEASUREMENT_ID/files")

if [ -z "$MANIFEST" ] || echo "$MANIFEST" | jq -e '.measurementId == null' > /dev/null 2>&1; then
  echo "Error: Could not fetch manifest. Is the server running and token valid?"
  exit 1
fi

# Extract file paths and create test files
echo "Creating test files..."
FILE_COUNT=$(echo "$MANIFEST" | jq '.files | length')
echo "Found $FILE_COUNT files"

for i in $(seq 0 $((FILE_COUNT - 1))); do
  FILE_PATH=$(echo "$MANIFEST" | jq -r ".files[$i].path")
  FILE_SIZE=$(echo "$MANIFEST" | jq -r ".files[$i].length")
  
  FULL_PATH="$TEST_DIR/$FILE_PATH"
  mkdir -p "$(dirname "$FULL_PATH")"
  
  # Create a test file with some content
  # For testing, we'll create files with predictable content
  if [ "$FILE_SIZE" -eq 0 ]; then
    # Empty file
    touch "$FULL_PATH"
  else
    # Create file with test content (use dd to create exact size)
    dd if=/dev/urandom of="$FULL_PATH" bs=1024 count=$((FILE_SIZE / 1024 + 1)) 2>/dev/null
    # Truncate to exact size
    truncate -s "$FILE_SIZE" "$FULL_PATH"
  fi
  
  echo "  Created: $FILE_PATH ($FILE_SIZE bytes)"
done

echo ""
echo "Test setup complete!"
echo ""
echo "To test with NFS provider, restart the server with:"
echo "  export OPENBIS_NFS_MOUNT_PATH=$TEST_DIR"
echo "  export DEFAULT_PROVIDER_ID=openbis-nfs-1"
echo "  cd rest-api && mvn spring-boot:run -DskipTests"
echo ""
echo "Then test downloads with:"
echo "  curl -H 'Authorization: Bearer ${ACCESS_TOKEN:-6lh19z738279j9737jdxtaJn1h9W6K9Z}' \\"
echo "    http://localhost:8090/measurements/$MEASUREMENT_ID/files"

#!/bin/bash
# Setup script for testing OpenBisNfsStorageProvider locally
#
# This script creates a test directory structure matching openBIS's sharded
# physical storage layout and sets environment variables for local testing.
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

# We need to get the physical location from openBIS
# For now, we'll create a placeholder and the user needs to update it
# In a real scenario, you'd query the openBIS API directly
echo ""
echo "IMPORTANT: You need to get the physical location from the server logs."
echo "Make a test request to see the physical location:"
echo "  curl -H 'Authorization: Bearer ${ACCESS_TOKEN:-6lh19z738279j9737jdxtaJn1h9W6K9Z}' \\"
echo "    http://localhost:8090/measurements/$MEASUREMENT_ID/files/0"
echo ""
echo "Then check the logs for:"
echo "  [NFS Provider] Physical location from openBIS for dataset $MEASUREMENT_ID: <location>"
echo ""
echo "Once you have the physical location, run:"
echo "  ./setup-nfs-test.sh $MEASUREMENT_ID <physical-location>"
echo ""

# Check if physical location was provided
if [ $# -lt 2 ]; then
  echo "Physical location not provided. Please check the logs and run again with the physical location."
  exit 0
fi

PHYSICAL_LOCATION="$2"
echo "Using physical location: $PHYSICAL_LOCATION"

# Create the sharded directory structure
PHYSICAL_DIR="$TEST_DIR/$PHYSICAL_LOCATION"
mkdir -p "$PHYSICAL_DIR"

# Extract file paths and create test files
echo "Creating test files in sharded structure..."
FILE_COUNT=$(echo "$MANIFEST" | jq '.files | length')
echo "Found $FILE_COUNT files"

for i in $(seq 0 $((FILE_COUNT - 1))); do
  FILE_PATH=$(echo "$MANIFEST" | jq -r ".files[$i].path")
  FILE_SIZE=$(echo "$MANIFEST" | jq -r ".files[$i].length")
  
  # Create file under the physical location directory
  FULL_PATH="$PHYSICAL_DIR/$FILE_PATH"
  mkdir -p "$(dirname "$FULL_PATH")"
  
  # Create a test file with some content
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
echo "Files created under: $PHYSICAL_DIR"
echo ""
echo "To test with NFS provider, restart the server with:"
echo "  export OPENBIS_NFS_MOUNT_PATH=$TEST_DIR"
echo "  export DEFAULT_PROVIDER_ID=openbis-nfs-1"
echo "  export DOWNLOAD_CONTROLLER_VERSION=v2"
echo "  cd rest-api && mvn spring-boot:run -DskipTests"
echo ""
echo "Then test downloads with:"
echo "  curl -H 'Authorization: Bearer ${ACCESS_TOKEN:-6lh19z738279j9737jdxtaJn1h9W6K9Z}' \\"
echo "    http://localhost:8090/measurements/$MEASUREMENT_ID/files/0 -o /tmp/test.gz"

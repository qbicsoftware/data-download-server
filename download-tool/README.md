# Measurement Download Tool

Downloads the files of a measurement from the QBiC data download server **without
zipping**, reproducing the original directory structure and resuming interrupted
downloads.

## Requirements

- `curl`
- `jq`
- optionally `crc32` (falls back to `python3` if not installed)

## Usage

```
./download-measurement.sh <measurement-id> <access-token> [target-dir]
```

The `<access-token>` is the QBiC data download access token. Files are written
under `target-dir` (defaults to the current directory). The directory layout
described by each file's manifest `path` value is recreated locally.

### Examples

Download a measurement into the current directory:

```bash
./download-measurement.sh NGSQ0001006AO-25948529211108 2g2Yd84mn1n63068V67KQul9DQG5N4oR
```

Download into a specific folder, reading the token from an environment variable:

```bash
./download-measurement.sh NGSQ0001006AO-25948529211108 "$TOKEN" ./my-measurement
```

If the remote folder structure of the manifest looks like `data/read1.fastq.gz`
and `qc/report.html`, the script creates:

```
my-measurement/
├── data/
│   └── read1.fastq.gz
└── qc/
    └── report.html
```

## Behavior

- **Manifest-driven**: the list of files, their expected size and CRC-32 are read
  from `GET {BASE_URL}/measurements/{id}/files`.
- **Directory structure restored**: each file's manifest `path` determines its
  local location (leading slashes are stripped).
- **Resume**: if a local file already exists, the download continues from its
  current byte size via an HTTP `Range: bytes=N-` request (RFC 7233). Already
  complete files are skipped.
- **Abort detection**: a transfer is retried if `curl` fails or the file is still
  shorter than expected. A local file that is *larger* than expected is truncated
  and restarted.
- **Integrity check**: each completed file is verified against the manifest CRC-32
  and re-downloaded from scratch on mismatch.
- **Retries**: each file is retried up to `MAX_RETRIES` (default `5`) times before
  the script reports the file as failed.

## Configuration (environment variables)

| Variable       | Default                                      | Description                                             |
| -------------- | -------------------------------------------- | ------------------------------------------------------- |
| `BASE_URL`     | `https://download.qbic.uni-tuebingen.de`     | Base URL of the download server.                        |
| `INSECURE`     | `false`                                      | Set to `true` to skip TLS certificate verification.     |
| `MAX_RETRIES`  | `5`                                          | Retry limit per file before giving up.                  |

Example overriding the server:

```bash
BASE_URL=https://localhost:8090 INSECURE=true \
  ./download-measurement.sh NGSQ0001006AO-25948529211108 "$TOKEN" ./my-measurement
```
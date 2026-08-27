# Smart Download Client

A Python download client that prevents stalls when downloading large files (100+ GB) by adaptively throttling the download rate to match your disk speed.

## Problem Solved

When downloading large files with standard tools like `curl`, downloads often stall at 85-95% completion. This happens because:

1. **Network is faster than disk**: The download speed exceeds what your disk can write
2. **Page cache fills up**: Linux uses RAM as a buffer for disk writes
3. **Disk blocks**: Once the page cache is full, `curl`'s write operations block
4. **TCP buffer fills**: `curl` stops reading from the network
5. **Connection stalls**: The server sees no data being read and times out

This is especially problematic for:
- Large genomics files (FASTQ, BAM) that are 100+ GB
- Clients with spinning disks or network storage
- Long downloads where cumulative packet loss triggers TCP backoff

## How It Works

The smart download client uses **adaptive rate control**:

```
Network ──→ Small buffer (8 MB) ──→ Disk
            ↑
       Rate controller monitors disk write speed
       Adjusts download rate to match disk capacity
```

**Key features:**
- **Monitors disk write latency**: Measures how long each disk write takes
- **Adaptive throttling**: If writes are slow (>50ms), slows down the download
- **Small buffer**: Keeps only 8 MB in memory, preventing page cache buildup
- **Two threads**: One reads from network, one writes to disk
- **Automatic resume**: If interrupted, just run the same command again
- **Retry with backoff**: Up to 10 retries on transient failures

## Installation

No dependencies required - uses only Python standard libraries.

```bash
# Download the script
curl -O https://raw.githubusercontent.com/.../smart_download.py
chmod +x smart_download.py
```

## Usage

### Basic usage

```bash
python3 smart_download.py \
    "https://server/measurements/ID/files/0" \
    output.fastq.gz \
    --token "your-bearer-token"
```

### With environment variable

```bash
export DOWNLOAD_TOKEN="your-bearer-token"
python3 smart_download.py \
    "https://server/measurements/ID/files/0" \
    output.fastq.gz
```

### Resume a partial download

```bash
# Just run the same command again
python3 smart_download.py \
    "https://server/measurements/ID/files/0" \
    output.fastq.gz \
    --token "your-bearer-token"
# Automatically resumes from where it left off
```

### With debug output

```bash
python3 smart_download.py \
    "https://server/measurements/ID/files/0" \
    output.fastq.gz \
    --token "your-bearer-token" \
    --debug
```

## Understanding the Output

### Startup metadata

```
======================================================================
SMART DOWNLOAD CLIENT
======================================================================
File size:        162.82 GB
Output file:      output.fastq.gz
Buffer size:      8.0 MB
Rate limit:       50.0 MB/s (adaptive)
Max retries:      10

Progress fields:
  downloaded    - bytes downloaded / total file size (percentage)
  throughput    - current download speed (network)
  disk write    - average disk write latency (lower is better)
  rate limit    - current adaptive rate limit
  buffer        - current buffer usage (should stay low)
  ETA           - estimated time remaining
======================================================================
```

### Progress metrics

```
697.2 MB / 162.82 GB (0.4%)  throughput: 24.2 MB/s  disk write: 0.2ms  limit: 208.9 MB/s  buffer: 0 B  ETA: 1h 54m  [30s elapsed]
```

**What each field means:**

- **throughput**: Current network download speed
- **disk write**: Average disk write latency
  - `<20ms`: Disk is fast, no bottleneck
  - `20-50ms`: Disk is moderate, may throttle
  - `>50ms`: Disk is slow, download is throttled
- **limit**: Current adaptive rate limit (starts at 50 MB/s, adjusts based on disk speed)
- **buffer**: Current buffer usage (should stay near 0 B)
- **ETA**: Estimated time remaining
- **elapsed**: Total time elapsed

### Interpreting the metrics

**Scenario 1: Network bottleneck (fast SSD)**
```
throughput: 24.2 MB/s  disk write: 0.2ms  limit: 208.9 MB/s  buffer: 0 B
```
- Disk is very fast (0.2ms write latency)
- Rate limit increased to 208.9 MB/s
- Network can only deliver 24.2 MB/s
- **Bottleneck**: Network connection

**Scenario 2: Disk bottleneck (slow disk)**
```
throughput: 15.3 MB/s  disk write: 85.4ms  limit: 18.2 MB/s  buffer: 6.2 MB
```
- Disk is slow (85.4ms write latency)
- Rate limit decreased to 18.2 MB/s to match disk
- Buffer is filling up (6.2 MB)
- **Bottleneck**: Disk speed

**Scenario 3: Balanced**
```
throughput: 22.1 MB/s  disk write: 42.3ms  limit: 23.5 MB/s  buffer: 1.8 MB
```
- Network and disk are roughly matched
- Rate limit adjusted to 23.5 MB/s
- Buffer stays low
- **Bottleneck**: Both network and disk

## Configuration

You can adjust these constants at the top of the script:

```python
# Target: keep disk write latency below this threshold (seconds).
# If writing a chunk takes longer than this, we slow down the download.
MAX_WRITE_LATENCY = 0.05  # 50 ms

# Minimum and maximum download rates (bytes per second).
MIN_RATE = 1 * 1024 * 1024       # 1 MB/s
MAX_RATE = 500 * 1024 * 1024     # 500 MB/s

# Start with this rate (bytes per second).
INITIAL_RATE = 50 * 1024 * 1024  # 50 MB/s

# Size of each chunk read from the network and written to disk.
CHUNK_SIZE = 256 * 1024  # 256 KB

# Maximum bytes allowed in the in-memory buffer before we pause reading.
MAX_BUFFER_SIZE = 8 * 1024 * 1024  # 8 MB

# How many times to retry on transient failures.
MAX_RETRIES = 10

# Delay between retries (seconds). Doubles on each retry.
RETRY_DELAY = 5
```

**Tuning tips:**
- **Slow disk**: Lower `MAX_WRITE_LATENCY` to 0.02 (20ms)
- **Fast SSD**: Raise `MAX_WRITE_LATENCY` to 0.1 (100ms)
- **Slow network**: Lower `INITIAL_RATE` to 10 MB/s
- **Fast network**: Raise `INITIAL_RATE` to 100 MB/s

## Comparison with curl

| Feature | curl | smart_download.py |
|---------|------|-------------------|
| Flow control | None | Adaptive rate control |
| Buffer size | Large (page cache) | Small (8 MB) |
| Disk write | Synchronous | Asynchronous with monitoring |
| Resume | Manual (`-C -`) | Automatic |
| Retry | Manual (`--retry`) | Automatic with backoff |
| Progress | Basic | Detailed metrics |
| Stall prevention | None | Prevents page cache saturation |

**Why curl stalls but smart_download.py doesn't:**

curl downloads as fast as the network allows. If the disk can't keep up, the page cache fills up, curl blocks on write, stops reading from the network, and the TCP buffer fills up. Eventually the server times out.

smart_download.py monitors disk write speed and throttles the download to match. The buffer stays small, the TCP buffer never fills, and the connection stays healthy.

## Troubleshooting

### Download fails with "Authentication failed"

Make sure you're providing a valid Bearer token:
```bash
python3 smart_download.py \
    "https://server/measurements/ID/files/0" \
    output.fastq.gz \
    --token "your-bearer-token"
```

### Download is very slow

Check the metrics:
- If `disk write` is high (>50ms), your disk is the bottleneck
- If `limit` is low and `throughput` matches it, the script is throttling
- Try tuning `MAX_WRITE_LATENCY` or `INITIAL_RATE`

### Buffer keeps filling up

If `buffer` stays above 4 MB, the disk is consistently slower than the network. Try:
- Lower `INITIAL_RATE` to 10 MB/s
- Lower `MAX_WRITE_LATENCY` to 0.02 (20ms)
- Use a faster disk (SSD instead of spinning drive)

### Connection keeps dropping

The script retries up to 10 times with exponential backoff. If it still fails:
- Check your network connection
- Check if the server is overloaded
- Try downloading at a different time

## License

This script is provided as-is for use with the QBiC data download server.

## Support

For issues or questions, please open an issue on GitHub.

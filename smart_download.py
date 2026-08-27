#!/usr/bin/env python3
"""
Smart download client that prevents stalls caused by the client's disk
being slower than the network. It monitors disk write latency and
adaptively throttles the download rate to match what the disk can handle.

Uses only Python standard libraries.

Usage:
    python3 smart_download.py <url> <output_file> [--token BEARER_TOKEN]

Example:
    python3 smart_download.py \
        "https://server/measurements/ID/files/0" \
        output.fastq.gz \
        --token "your-bearer-token-here"

    # Or use environment variable:
    export DOWNLOAD_TOKEN="your-bearer-token-here"
    python3 smart_download.py \
        "https://server/measurements/ID/files/0" \
        output.fastq.gz

The script will:
    - Resume partial downloads automatically (HTTP Range)
    - Monitor disk write speed and adapt download rate
    - Retry on transient failures
    - Show progress with throughput and ETA
"""

import argparse
import http.client
import os
import ssl
import sys
import threading
import time
import urllib.parse
from collections import deque
from pathlib import Path


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

# Target: keep disk write latency below this threshold (seconds).
# If writing a chunk takes longer than this, we slow down the download.
MAX_WRITE_LATENCY = 0.05  # 50 ms

# How often to adjust the download rate (seconds).
ADJUST_INTERVAL = 2.0

# Minimum and maximum download rates (bytes per second).
MIN_RATE = 1 * 1024 * 1024       # 1 MB/s
MAX_RATE = 500 * 1024 * 1024     # 500 MB/s

# Start with this rate (bytes per second).
INITIAL_RATE = 50 * 1024 * 1024  # 50 MB/s

# Size of each chunk read from the network and written to disk.
CHUNK_SIZE = 256 * 1024  # 256 KB

# How many times to retry on transient failures.
MAX_RETRIES = 10

# Delay between retries (seconds). Doubles on each retry.
RETRY_DELAY = 5

# Progress reporting interval (seconds).
PROGRESS_INTERVAL = 5

# Disable SSL certificate verification (set to True for self-signed certs).
SKIP_SSL_VERIFY = True

# Debug mode
DEBUG = False


def debug_print(msg):
    """Print debug message if DEBUG is enabled."""
    if DEBUG:
        print(f"[DEBUG] {msg}", file=sys.stderr)


# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------

def open_connection(url, timeout=30):
    """Open an HTTPS connection and return (conn, parsed_url)."""
    parsed = urllib.parse.urlparse(url)
    host = parsed.hostname
    port = parsed.port or (443 if parsed.scheme == "https" else 80)
    path = parsed.path
    if parsed.query:
        path += "?" + parsed.query

    debug_print(f"Opening connection to {host}:{port}{path}")

    if parsed.scheme == "https":
        ctx = None
        if SKIP_SSL_VERIFY:
            ctx = ssl.create_default_context()
            ctx.check_hostname = False
            ctx.verify_mode = ssl.CERT_NONE
        conn = http.client.HTTPSConnection(host, port, timeout=timeout,
                                           context=ctx)
    else:
        conn = http.client.HTTPConnection(host, port, timeout=timeout)

    return conn, path


def get_auth_headers(token):
    """Return authorization headers if token is provided."""
    if token:
        return {"Authorization": f"Bearer {token}"}
    return {}


def get_file_size(url, token=None):
    """Return the total file size via GET request."""
    debug_print(f"Getting file size for {url}")
    
    conn, path = open_connection(url, timeout=120)
    try:
        headers = get_auth_headers(token)
        debug_print(f"Sending GET request to {path}")
        conn.request("GET", path, headers=headers)
        resp = conn.getresponse()
        debug_print(f"GET response status: {resp.status}")
        
        if resp.status == 401:
            raise RuntimeError("Authentication failed: invalid or missing token")
        if resp.status not in (200, 206):
            raise RuntimeError(f"GET request failed: {resp.status}")
        
        # Get file size from Content-Length
        content_length = resp.getheader("Content-Length")
        if content_length:
            size = int(content_length)
            debug_print(f"File size from Content-Length: {size} bytes")
            return size
        
        raise RuntimeError("Server did not return Content-Length header")
    finally:
        conn.close()


def download_range(url, start_byte, token=None, timeout=120):
    """
    Open a connection and request bytes from start_byte to end.
    Returns (conn, response).
    """
    debug_print(f"Opening download connection to {url} from byte {start_byte}")
    conn, path = open_connection(url, timeout=timeout)
    headers = get_auth_headers(token)
    if start_byte > 0:
        headers["Range"] = f"bytes={start_byte}-"
        debug_print(f"Requesting Range: bytes={start_byte}-")

    debug_print(f"Sending GET request to {path}")
    conn.request("GET", path, headers=headers)
    resp = conn.getresponse()
    debug_print(f"GET response status: {resp.status}")

    if resp.status == 401:
        conn.close()
        raise RuntimeError("Authentication failed: invalid or missing token")
    if resp.status not in (200, 206):
        conn.close()
        raise RuntimeError(f"GET request failed: {resp.status}")

    debug_print("Download connection established")
    return conn, resp


# ---------------------------------------------------------------------------
# Adaptive rate controller
# ---------------------------------------------------------------------------

class RateController:
    """
    Monitors disk write latency and adjusts the download rate.

    The idea: if writing chunks to disk takes longer than MAX_WRITE_LATENCY,
    the disk can't keep up. We slow down the download so the disk can catch
    up. If writes are fast, we speed up.
    """

    def __init__(self, initial_rate=INITIAL_RATE):
        self.rate = initial_rate
        self.lock = threading.Lock()
        self.write_latencies = deque(maxlen=20)
        self.last_adjust_time = time.monotonic()
        self.total_bytes = 0
        self.start_time = time.monotonic()
        self.avg_write_latency_ms = 0.0

    def record_write(self, size, elapsed):
        """Record how long a disk write took."""
        latency_per_byte = elapsed / max(size, 1)
        with self.lock:
            self.write_latencies.append(latency_per_byte)
            # Calculate average latency in milliseconds
            if self.write_latencies:
                avg_latency = sum(self.write_latencies) / len(self.write_latencies)
                self.avg_write_latency_ms = avg_latency * CHUNK_SIZE * 1000

    def record_read(self, size):
        """Record bytes downloaded."""
        with self.lock:
            self.total_bytes += size

    def get_rate(self):
        """Return the current target rate, adjusting if needed."""
        now = time.monotonic()
        with self.lock:
            if now - self.last_adjust_time < ADJUST_INTERVAL:
                return self.rate
            if not self.write_latencies:
                return self.rate

            self.last_adjust_time = now

            # Average write latency per byte over recent writes.
            avg_latency = sum(self.write_latencies) / len(self.write_latencies)

            # How long does it take to write one chunk?
            estimated_chunk_write_time = avg_latency * CHUNK_SIZE

            if estimated_chunk_write_time > MAX_WRITE_LATENCY:
                # Disk is slow — reduce rate by 20%.
                self.rate = max(int(self.rate * 0.8), MIN_RATE)
            elif estimated_chunk_write_time < MAX_WRITE_LATENCY * 0.5:
                # Disk is fast — increase rate by 10%.
                self.rate = min(int(self.rate * 1.1), MAX_RATE)

            return self.rate

    def get_throughput(self):
        """Return current download throughput in bytes/sec."""
        elapsed = time.monotonic() - self.start_time
        if elapsed <= 0:
            return 0
        return self.total_bytes / elapsed


# ---------------------------------------------------------------------------
# Download engine
# ---------------------------------------------------------------------------

class SmartDownloader:
    """
    Downloads a file with adaptive rate control.

    The key insight: we decouple network reads from disk writes using a
    small in-memory buffer. If the buffer grows too large (disk can't keep
    up), we slow down the network reads. This prevents the TCP receive
    buffer from filling up and causing stalls.
    """

    # Maximum bytes allowed in the in-memory buffer before we pause reading.
    # This is intentionally small (a few MB) to prevent the page cache from
    # filling up.
    MAX_BUFFER_SIZE = 8 * 1024 * 1024  # 8 MB

    def __init__(self, url, output_path, token=None):
        self.url = url
        self.output_path = Path(output_path)
        self.token = token
        self.rate_controller = RateController()
        self.buffer = bytearray()
        self.buffer_lock = threading.Lock()
        self.buffer_not_full = threading.Condition(self.buffer_lock)
        self.buffer_not_empty = threading.Condition(self.buffer_lock)
        self.done = False
        self.error = None
        self.file_size = 0
        self.bytes_downloaded = 0
        self.retry_count = 0
        self.start_byte = 0

    def run(self):
        """Run the download. Returns True on success, False on failure."""
        # Get file size.
        try:
            debug_print("Getting file size...")
            self.file_size = get_file_size(self.url, self.token)
            debug_print(f"File size: {self.file_size} bytes")
        except Exception as e:
            print(f"Error getting file size: {e}", file=sys.stderr)
            return False

        # Check for partial download.
        start_byte = 0
        if self.output_path.exists():
            start_byte = self.output_path.stat().st_size
            if start_byte >= self.file_size:
                print(f"File already complete: {self.output_path}")
                return True
            print(f"Resuming from {start_byte / 1024 / 1024:.1f} MB "
                  f"({start_byte * 100 / self.file_size:.1f}%)")

        self.bytes_downloaded = start_byte
        self.start_byte = start_byte
        self.rate_controller.total_bytes = start_byte

        # Start writer thread.
        writer = threading.Thread(target=self._writer_loop, daemon=True)
        writer.start()

        # Reader loop (runs in main thread).
        success = False
        while self.retry_count <= MAX_RETRIES:
            try:
                self._reader_loop(start_byte)
                success = True
                break
            except (ConnectionError, OSError, RuntimeError) as e:
                self.retry_count += 1
                if self.retry_count > MAX_RETRIES:
                    print(f"\nFailed after {MAX_RETRIES} retries: {e}",
                          file=sys.stderr)
                    break
                delay = RETRY_DELAY * (2 ** (self.retry_count - 1))
                delay = min(delay, 60)
                print(f"\nConnection error: {e}")
                print(f"Retrying in {delay}s (attempt {self.retry_count}/"
                      f"{MAX_RETRIES})...")

                # Update start_byte to current position.
                with self.buffer_lock:
                    start_byte = self.bytes_downloaded

                time.sleep(delay)

        # Signal writer to stop.
        with self.buffer_not_empty:
            self.done = True
            self.buffer_not_empty.notify()

        writer.join(timeout=10)
        return success

    def _reader_loop(self, start_byte):
        """Read from the network and fill the buffer."""
        conn, resp = download_range(self.url, start_byte, self.token)
        try:
            while True:
                # Check if buffer is too full — wait for writer to drain.
                with self.buffer_not_full:
                    while len(self.buffer) > self.MAX_BUFFER_SIZE:
                        self.buffer_not_full.wait(timeout=0.5)
                        if self.done:
                            return

                # Rate limiting: sleep to stay within target rate.
                target_rate = self.rate_controller.get_rate()
                current_throughput = self.rate_controller.get_throughput()
                if current_throughput > target_rate and target_rate > 0:
                    # We're downloading too fast — pause briefly.
                    overshoot = current_throughput / target_rate
                    sleep_time = min(0.1 * overshoot, 1.0)
                    time.sleep(sleep_time)

                # Read a chunk from the network.
                chunk = resp.read(CHUNK_SIZE)
                if not chunk:
                    break

                # Add to buffer.
                with self.buffer_not_empty:
                    self.buffer.extend(chunk)
                    self.bytes_downloaded += len(chunk)
                    self.rate_controller.record_read(len(chunk))
                    self.buffer_not_empty.notify()

        finally:
            conn.close()

    def _writer_loop(self):
        """Drain the buffer and write to disk."""
        mode = "ab" if self.output_path.exists() and \
               self.output_path.stat().st_size > 0 else "wb"

        with open(self.output_path, mode) as f:
            while True:
                with self.buffer_not_empty:
                    while not self.buffer and not self.done:
                        self.buffer_not_empty.wait(timeout=0.5)

                    if not self.buffer and self.done:
                        return

                    # Take all data from the buffer.
                    data = bytes(self.buffer)
                    self.buffer.clear()
                    self.buffer_not_full.notify()

                # Write to disk and measure how long it takes.
                write_start = time.monotonic()
                f.write(data)
                f.flush()
                os.fsync(f.fileno())
                write_elapsed = time.monotonic() - write_start

                self.rate_controller.record_write(len(data), write_elapsed)


# ---------------------------------------------------------------------------
# Progress reporting
# ---------------------------------------------------------------------------

def format_size(size_bytes):
    """Format bytes as human-readable string."""
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    elif size_bytes < 1024 * 1024 * 1024:
        return f"{size_bytes / 1024 / 1024:.1f} MB"
    else:
        return f"{size_bytes / 1024 / 1024 / 1024:.2f} GB"


def format_rate(bytes_per_sec):
    """Format bytes/sec as human-readable string."""
    return format_size(bytes_per_sec) + "/s"


def format_time(seconds):
    """Format seconds as human-readable ETA."""
    if seconds < 0 or seconds > 86400 * 30:
        return "unknown"
    hours = int(seconds // 3600)
    minutes = int((seconds % 3600) // 60)
    secs = int(seconds % 60)
    if hours > 0:
        return f"{hours}h {minutes}m"
    elif minutes > 0:
        return f"{minutes}m {secs}s"
    else:
        return f"{secs}s"


def print_header(downloader):
    """Print metadata and field explanations."""
    print("=" * 70)
    print("SMART DOWNLOAD CLIENT")
    print("=" * 70)
    print(f"File size:        {format_size(downloader.file_size)}")
    print(f"Output file:      {downloader.output_path}")
    if downloader.start_byte > 0:
        print(f"Resuming from:    {format_size(downloader.start_byte)}")
        remaining = downloader.file_size - downloader.start_byte
        print(f"Remaining:        {format_size(remaining)}")
    print(f"Buffer size:      {format_size(SmartDownloader.MAX_BUFFER_SIZE)}")
    print(f"Rate limit:       {format_rate(INITIAL_RATE)} (adaptive)")
    print(f"Max retries:      {MAX_RETRIES}")
    print()
    print("Progress fields:")
    print("  downloaded    - bytes downloaded / total file size (percentage)")
    print("  throughput    - current download speed (network)")
    print("  disk write    - average disk write latency (lower is better)")
    print("  rate limit    - current adaptive rate limit")
    print("  buffer        - current buffer usage (should stay low)")
    print("  ETA           - estimated time remaining")
    print("=" * 70)
    print()


def progress_reporter(downloader, stop_event):
    """Print progress every PROGRESS_INTERVAL seconds."""
    last_bytes = downloader.bytes_downloaded
    last_time = time.monotonic()
    start_time = time.monotonic()

    while not stop_event.is_set():
        stop_event.wait(PROGRESS_INTERVAL)
        if stop_event.is_set():
            break

        now = time.monotonic()
        elapsed = now - last_time
        current_bytes = downloader.bytes_downloaded
        bytes_delta = current_bytes - last_bytes
        throughput = bytes_delta / max(elapsed, 0.001)

        remaining = downloader.file_size - current_bytes
        if throughput > 0:
            eta = remaining / throughput
        else:
            eta = -1

        pct = current_bytes * 100 / max(downloader.file_size, 1)
        rate = downloader.rate_controller.get_rate()
        disk_latency = downloader.rate_controller.avg_write_latency_ms
        
        # Get buffer size
        with downloader.buffer_lock:
            buffer_size = len(downloader.buffer)
        
        total_elapsed = now - start_time

        print(f"\r  {format_size(current_bytes)} / {format_size(downloader.file_size)} "
              f"({pct:.1f}%)  "
              f"throughput: {format_rate(throughput)}  "
              f"disk write: {disk_latency:.1f}ms  "
              f"limit: {format_rate(rate)}  "
              f"buffer: {format_size(buffer_size)}  "
              f"ETA: {format_time(eta)}  "
              f"[{format_time(total_elapsed)} elapsed]",
              end="", flush=True)

        last_bytes = current_bytes
        last_time = now

    # Final newline.
    print()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Smart download client with adaptive rate control",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # With token from command line:
  %(prog)s "https://server/measurements/ID/files/0" output.fastq.gz --token "abc123"
  
  # With token from environment variable:
  export DOWNLOAD_TOKEN="abc123"
  %(prog)s "https://server/measurements/ID/files/0" output.fastq.gz
  
  # Resume a partial download:
  %(prog)s "https://server/measurements/ID/files/0" output.fastq.gz --token "abc123"
  (automatically resumes if output.fastq.gz already exists)
  
  # With debug output:
  %(prog)s "https://server/measurements/ID/files/0" output.fastq.gz --token "abc123" --debug
"""
    )
    
    parser.add_argument("url", help="Download URL")
    parser.add_argument("output", help="Output file path")
    parser.add_argument("--token", "-t", 
                       help="Bearer token for authentication (or set DOWNLOAD_TOKEN env var)")
    parser.add_argument("--debug", "-d", action="store_true",
                       help="Enable debug output")
    
    args = parser.parse_args()
    
    # Enable debug mode
    global DEBUG
    DEBUG = args.debug
    
    # Get token from argument or environment variable
    token = args.token or os.environ.get("DOWNLOAD_TOKEN")
    
    if not token:
        print("Warning: No authentication token provided. Use --token or set DOWNLOAD_TOKEN env var.", 
              file=sys.stderr)
        print("The download may fail if the server requires authentication.", file=sys.stderr)
        print()

    url = args.url
    output_path = args.output

    downloader = SmartDownloader(url, output_path, token)

    # Get file size first to print header
    try:
        downloader.file_size = get_file_size(url, token)
    except Exception as e:
        print(f"Error getting file size: {e}", file=sys.stderr)
        sys.exit(1)

    # Check for partial download
    if downloader.output_path.exists():
        downloader.start_byte = downloader.output_path.stat().st_size
        if downloader.start_byte >= downloader.file_size:
            print(f"File already complete: {downloader.output_path}")
            sys.exit(0)

    # Print header with metadata
    print_header(downloader)

    # Start progress reporter.
    stop_progress = threading.Event()
    progress_thread = threading.Thread(
        target=progress_reporter,
        args=(downloader, stop_progress),
        daemon=True,
    )
    progress_thread.start()

    start_time = time.monotonic()
    success = downloader.run()
    elapsed = time.monotonic() - start_time

    stop_progress.set()
    progress_thread.join(timeout=5)

    if success:
        final_size = Path(output_path).stat().st_size
        avg_rate = final_size / max(elapsed, 0.001)
        print()
        print("=" * 70)
        print(f"Download complete!")
        print(f"  File size:    {format_size(final_size)}")
        print(f"  Duration:     {format_time(elapsed)}")
        print(f"  Avg rate:     {format_rate(avg_rate)}")
        print(f"  Output:       {output_path}")
        print("=" * 70)
        sys.exit(0)
    else:
        print()
        print("=" * 70)
        print("Download failed.")
        print("=" * 70)
        sys.exit(1)


if __name__ == "__main__":
    main()

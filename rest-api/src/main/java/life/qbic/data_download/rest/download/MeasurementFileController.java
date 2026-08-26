package life.qbic.data_download.rest.download;

import static org.slf4j.LoggerFactory.getLogger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import life.qbic.data_download.measurements.api.DataFile;
import life.qbic.data_download.measurements.api.FileInfo;
import life.qbic.data_download.measurements.api.MeasurementDataProvider;
import life.qbic.data_download.measurements.api.MeasurementId;
import life.qbic.data_download.measurements.api.PathFormatter;
import life.qbic.data_download.rest.exceptions.GlobalException;
import life.qbic.data_download.rest.exceptions.GlobalException.ErrorCode;
import life.qbic.data_download.rest.exceptions.GlobalException.ErrorParameters;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Endpoints to list and download the files of a measurement without zipping them. Files are
 * referenced by a stable index derived from their sorted path and support resumable downloads via
 * HTTP range requests.
 */
@RestController
@Tag(name = "Download Endpoints", description = "Rest endpoints related to downloading data")
public class MeasurementFileController {

  private static final Logger log = getLogger(MeasurementFileController.class);

  private static final Pattern MEASUREMENT_ID_PATTERN = Pattern.compile("[^a-zA-Z0-9-]+");
  private static final int DEFAULT_BUFFER_SIZE = 1024 * 1024; //1 MB buffer
  private static final long PROGRESS_LOG_INTERVAL_MS = 30_000; // Log progress every 30 seconds
  private static final long POLL_TIMEOUT_MS = 100; // Poll timeout for checking producer status

  private final MeasurementDataProvider measurementDataProvider;
  private final MeasurementFileIndex measurementFileIndex;
  private final ByteRange byteRange;
  private final int downloadBufferSize;
  private final int downloadQueueCapacity;

  private static final int DEFAULT_QUEUE_CAPACITY = 64;

  public MeasurementFileController(
      @Qualifier("measurementDataProvider") MeasurementDataProvider measurementDataProvider,
      MeasurementFileIndex measurementFileIndex,
      ByteRange byteRange,
      @Value("${server.memory.download.buffer}") Integer downloadBufferSize,
      @Value("${server.download.queue.capacity}") Integer downloadQueueCapacity) {
    this.measurementDataProvider = measurementDataProvider;
    this.measurementFileIndex = measurementFileIndex;
    this.byteRange = byteRange;
    this.downloadBufferSize = Optional.ofNullable(downloadBufferSize)
        .orElse(DEFAULT_BUFFER_SIZE);
    this.downloadQueueCapacity = Optional.ofNullable(downloadQueueCapacity)
        .orElse(DEFAULT_QUEUE_CAPACITY);
  }

  @GetMapping(value = {"/measurements/{measurementId}/files/", "/measurements/{measurementId}/files"}, produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "List the files of a measurement in stable order")
  @Parameter(name = "measurementId", required = true, description = "The identifier of the measurement", example = "NGSQ0001006AO-25948529211108")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "successful operation, the manifest is returned", content = @Content(schema = @Schema(implementation = MeasurementManifest.class))),
      @ApiResponse(responseCode = "403", description = "forbidden, you do not have access to this resource"),
      @ApiResponse(responseCode = "404", description = "measurement not found"),
  })
  public ResponseEntity<MeasurementManifest> manifest(
      @PathVariable("measurementId") String measurementId) {
    var sanitizedId = sanitizeMeasurementId(measurementId);
    var measurementIdentifier = new MeasurementId(sanitizedId);
    var files = measurementFileIndex.files(measurementIdentifier);
    // A measurement without any files is indistinguishable from a non-existent one, so an empty
    // list is reported as "not found" to the client.
    if (files.isEmpty()) {
      throw new GlobalException("request failed.",
          ErrorCode.MEASUREMENT_NOT_FOUND, ErrorParameters.of(sanitizedId));
    }
    var entries = new java.util.ArrayList<MeasurementManifest.FileEntry>();
    for (int i = 0; i < files.size(); i++) {
      FileInfo fileInfo = files.get(i);
      String downloadHref =
          "/measurements/%s/files/%d".formatted(sanitizedId, i);
      var links = new MeasurementManifest.Links(
          new MeasurementManifest.Download(downloadHref));
      entries.add(new MeasurementManifest.FileEntry(i, fileInfo.path(), fileInfo.fileName(),
          fileInfo.length(),
          fileInfo.crc32(), formatUtcIso(fileInfo.registrationMillis()), links));
    }
    return ResponseEntity.ok(new MeasurementManifest(sanitizedId, entries));
  }

  @GetMapping(value = "/measurements/{measurementId}/files/{index}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @Operation(summary = "Download a single file of a measurement by its index, supporting resumable range requests")
  @Parameter(name = "measurementId", required = true, description = "The identifier of the measurement", example = "NGSQ0001006AO-25948529211108")
  @Parameter(name = "index", required = true, description = "The zero-based index of the file within the manifest", example = "0")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "successful operation, the file is downloaded", content = @Content(schema = @Schema(implementation = Void.class))),
      @ApiResponse(responseCode = "206", description = "partial content, the requested byte range is downloaded", content = @Content(schema = @Schema(implementation = Void.class))),
      @ApiResponse(responseCode = "403", description = "forbidden, you do not have access to this resource"),
      @ApiResponse(responseCode = "404", description = "measurement or file not found"),
      @ApiResponse(responseCode = "416", description = "the requested byte range is not satisfiable"),
  })
  public ResponseEntity<StreamingResponseBody> downloadFile(
      @PathVariable("measurementId") String measurementId,
      @PathVariable("index") int index,
      @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
    var sanitizedId = sanitizeMeasurementId(measurementId);
    var measurementIdentifier = new MeasurementId(sanitizedId);
    FileInfo fileInfo = measurementFileIndex.fileByIndex(measurementIdentifier, index)
        .orElseThrow(() -> new GlobalException("request failed.",
            ErrorCode.FILE_NOT_FOUND, ErrorParameters.of(sanitizedId)));

    // The index only carries file metadata; the content stream must be opened separately per
    // request, otherwise the byte range could not be streamed independently of the manifest.
    DataFile dataFile = measurementDataProvider.loadFile(measurementIdentifier, fileInfo);
    if (dataFile == null) {
      throw new GlobalException("request failed.",
          ErrorCode.FILE_NOT_FOUND, ErrorParameters.of(sanitizedId));
    }
    String requestId = "downloadFile-" + UUID.randomUUID();
    String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
    log.info("request %s: user %s requests file %s of measurement %s".formatted(requestId,
        currentUser, fileInfo.path(), sanitizedId));

    long fileLength = fileInfo.length();
    ByteRange.Range requestedRange = byteRange.parse(rangeHeader, fileLength);
    long start = requestedRange.start();
    long end = requestedRange.end();
    long contentLength = requestedRange.length();
    boolean isPartial = requestedRange.isPartial();

    StreamingResponseBody responseBody = outputStream -> {
      log.info("request %s: user %s started downloading file %s of measurement %s".formatted(
          requestId, currentUser, fileInfo.path(), sanitizedId));
      try {
        writeRange(dataFile, start, contentLength, outputStream, fileInfo.path(), sanitizedId);
        log.info("request %s: user %s finished downloading file %s of measurement %s".formatted(
            requestId, currentUser, fileInfo.path(), sanitizedId));
      } catch (Exception e) {
        if (isClientAbort(e)) {
          log.warn("request %s: user %s disconnected while downloading file %s of measurement %s"
              .formatted(requestId, currentUser, fileInfo.path(), sanitizedId));
        } else {
          log.error("request %s: user %s failed for file %s of measurement %s".formatted(requestId,
              currentUser, fileInfo.path(), sanitizedId), e);
        }
        throw e;
      }
    };

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    headers.setContentLength(contentLength);
    headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
    headers.set(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"" + PathFormatter.fileNameOf(fileInfo.path()) + "\"");
    if (isPartial) {
      headers.set(HttpHeaders.CONTENT_RANGE,
          "bytes %d-%d/%d".formatted(start, end, fileLength));
      return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(responseBody);
    }
    return ResponseEntity.ok().headers(headers).body(responseBody);
  }

  /**
   * Checks whether the given exception indicates that the client disconnected during streaming
   * (e.g. CURL killed with Ctrl+C). These are not server errors and should not be logged as such.
   * The check is container-agnostic and works with both Tomcat and Jetty.
   */
  private static boolean isClientAbort(Exception e) {
    Throwable cause = e;
    while (cause != null) {
      // Check for well-known client abort exception types by name to avoid
      // hard dependencies on a specific servlet container (Tomcat vs Jetty).
      String className = cause.getClass().getName();
      if (className.equals("org.apache.catalina.connector.ClientAbortException")
          || className.equals("org.eclipse.jetty.io.EofException")) {
        return true;
      }
      String message = cause.getMessage();
      if (message != null && (message.contains("Broken pipe")
          || message.contains("Connection reset by peer"))) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  /**
   * Writes a byte range from the data file to the output stream using an async producer-consumer
   * pattern. A dedicated producer thread reads from the DSS input stream into a bounded queue,
   * while the consumer (calling thread) reads from the queue and writes to the client output stream.
   *
   * <p>This decoupling prevents slow clients from blocking DSS reads. Without this, a slow client
   * would cause outputStream.write() to block, which in turn blocks inputStream.read(), causing
   * the DSS TCP receive window to close and eventually the connection to be reset.
   *
   * <p>The bounded queue provides backpressure: when the queue is full, the producer blocks, which
   * naturally closes the TCP receive window and signals the DSS to slow down.
   */
  private void writeRange(DataFile dataFile, long start, long contentLength, OutputStream outputStream,
      String filePath, String measurementId) throws IOException {
    try (InputStream inputStream = dataFile.inputStream()) {
      skipToStart(inputStream, start);
      Transfer transfer = startProducer(inputStream, contentLength, filePath);
      try {
        consume(transfer, outputStream, contentLength, filePath, measurementId);
      } finally {
        transfer.producer.interrupt();
      }
    }
  }

  /**
   * Advances the stream to the requested start offset. {@link InputStream#skip} is not guaranteed
   * to skip the requested number of bytes, so we loop until the offset is reached. When skip makes
   * no progress (e.g. on some sources), fall back to reading a single byte at a time so we never
   * loop forever.
   */
  private static void skipToStart(InputStream inputStream, long start) throws IOException {
    long skipped = 0;
    while (skipped < start) {
      long skippedNow = inputStream.skip(start - skipped);
      if (skippedNow <= 0) {
        if (inputStream.read() == -1) {
          break;
        }
        skipped++;
      } else {
        skipped += skippedNow;
      }
    }
  }

  /**
   * Starts a producer thread that reads from the DSS input stream into a bounded queue and returns
   * the {@link Transfer} used by the consumer to drain it. The bounded queue decouples the DSS read
   * (producer) from the client write (consumer); when it is full the producer blocks, providing
   * backpressure to the DSS.
   */
  private Transfer startProducer(InputStream inputStream, long contentLength, String filePath) {
    BlockingQueue<byte[]> bufferQueue = new ArrayBlockingQueue<>(downloadQueueCapacity);
    AtomicReference<Throwable> producerError = new AtomicReference<>();
    AtomicBoolean producerDone = new AtomicBoolean(false);
    Thread producer = new Thread(() -> {
      try {
        byte[] buffer = new byte[downloadBufferSize];
        long remaining = contentLength;
        int read;
        while (remaining > 0 && (read = inputStream.read(buffer, 0,
            (int) Math.min(buffer.length, remaining))) != -1) {
          // Copy the data since the buffer is reused for the next read
          byte[] data = Arrays.copyOf(buffer, read);
          // put() blocks if the queue is full, providing backpressure to the DSS
          bufferQueue.put(data);
          remaining -= read;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        producerError.compareAndSet(null, e);
      } catch (Exception e) {
        producerError.compareAndSet(null, e);
      } finally {
        producerDone.set(true);
      }
    }, "dss-reader-" + filePath);
    producer.start();
    return new Transfer(producer, bufferQueue, producerError, producerDone);
  }

  /** Drains the queue in the consumer (calling) thread, writing each buffer to the client. */
  private void consume(Transfer transfer, OutputStream outputStream, long contentLength,
      String filePath, String measurementId) throws IOException {
    long totalBytesWritten = 0;
    long lastProgressLogTime = System.currentTimeMillis();
    try {
      while (!transfer.done.get() || !transfer.queue.isEmpty()) {
        byte[] data = transfer.queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (data != null) {
          outputStream.write(data);
          totalBytesWritten += data.length;
          lastProgressLogTime = logProgress(totalBytesWritten, contentLength, lastProgressLogTime,
              filePath, measurementId, transfer.queue.size());
        }
        transfer.throwIfFailed(filePath);
      }
      transfer.throwIfFailed(filePath);
      log.info("Transfer complete for file {} of measurement {}: {}MB total",
          filePath, measurementId, totalBytesWritten / (1024 * 1024));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      transfer.producer.interrupt();
      throw new IOException("Download interrupted for file " + filePath, e);
    }
  }

  /** Logs download progress at most once per {@link #PROGRESS_LOG_INTERVAL_MS} and returns the
   * updated last-logged timestamp. */
  private static long logProgress(long totalBytesWritten, long contentLength, long lastProgressLogTime,
      String filePath, String measurementId, int queueSize) {
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastProgressLogTime <= PROGRESS_LOG_INTERVAL_MS) {
      return lastProgressLogTime;
    }
    double progressPercent = (totalBytesWritten * 100.0) / contentLength;
    double elapsedSeconds = (currentTime - lastProgressLogTime) / 1000.0;
    double throughputMBps = (totalBytesWritten / (1024.0 * 1024.0)) / elapsedSeconds;
    log.info("Download progress for file {} of measurement {}: {}MB / {}MB ({}%), throughput: {} MB/s, queue size: {}",
        filePath, measurementId,
        totalBytesWritten / (1024 * 1024), contentLength / (1024 * 1024),
        String.format("%.1f", progressPercent),
        String.format("%.2f", throughputMBps),
        queueSize);
    return currentTime;
  }

  /** Shared state handed to the consumer so it can coordinate with and drain the producer. */
  private static final class Transfer {
    final Thread producer;
    final BlockingQueue<byte[]> queue;
    final AtomicReference<Throwable> error;
    final AtomicBoolean done;

    Transfer(Thread producer, BlockingQueue<byte[]> queue,
        AtomicReference<Throwable> error, AtomicBoolean done) {
      this.producer = producer;
      this.queue = queue;
      this.error = error;
      this.done = done;
    }

    void throwIfFailed(String filePath) throws IOException {
      Throwable failure = error.get();
      if (failure != null) {
        throw new IOException("DSS read failed for file " + filePath, failure);
      }
    }
  }

  private String formatUtcIso(long epochMillis) {
    if (epochMillis < 0) {
      return null;
    }
    return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis));
  }

  private String sanitizeMeasurementId(String measurementId) {
    if (MEASUREMENT_ID_PATTERN.matcher(measurementId).find()) {
      throw new GlobalException("unexpected measurement identifier containing unallowed characters",
          ErrorCode.ILLEGAL_MEASUREMENT_ID, ErrorParameters.of("The provided measurement identifier contained unexpected characters."));
    }
    return measurementId;
  }
}

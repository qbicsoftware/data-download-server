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
import life.qbic.data_download.rest.exceptions.GlobalException;
import life.qbic.data_download.rest.exceptions.GlobalException.ErrorCode;
import life.qbic.data_download.rest.exceptions.GlobalException.ErrorParameters;
import life.qbic.data_download.storage.ByteRange;
import life.qbic.data_download.storage.ByteRangeProvider;
import life.qbic.data_download.storage.ByteRangeParser;
import life.qbic.data_download.storage.DataFile;
import life.qbic.data_download.storage.FileInfo;
import life.qbic.data_download.storage.ProviderRegistry;
import life.qbic.data_download.storage.StorageProvider;
import life.qbic.data_download.storage.exception.DatasetNotFoundException;
import life.qbic.data_download.storage.exception.InvalidByteRangeException;
import life.qbic.data_download.storage.exception.StorageFileNotFoundException;
import life.qbic.data_download.storage.exception.StorageProviderException;
import life.qbic.data_download.storage.exception.TransientException;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * V2 endpoints to list and download the files of a measurement without zipping them. Files are
 * referenced by a stable index derived from their sorted path and support resumable downloads via
 * HTTP range requests.
 *
 * <p>This controller uses the {@link ProviderRegistry} and {@link StorageProvider} abstraction
 * instead of the legacy {@link life.qbic.data_download.measurements.api.MeasurementDataProvider}.
 * It is activated via the {@code download.controller-version=v2} property.
 */
@RestController
@ConditionalOnProperty(name = "download.controller-version", havingValue = "v2")
@Tag(name = "Download Endpoints", description = "Rest endpoints related to downloading data")
public class MeasurementFileControllerV2 {

  private static final Logger log = getLogger(MeasurementFileControllerV2.class);

  private static final Pattern MEASUREMENT_ID_PATTERN = Pattern.compile("[^a-zA-Z0-9-]+");
  private static final int DEFAULT_BUFFER_SIZE = 1024 * 1024; // 1 MB buffer
  private static final long DEFAULT_PROGRESS_LOG_INTERVAL_MS = 30_000;
  private static final long POLL_TIMEOUT_MS = 100;

  private final ProviderRegistry providerRegistry;
  private final StorageFileIndex storageFileIndex;
  private final int downloadBufferSize;
  private final int downloadQueueCapacity;
  private final long progressLogIntervalMs;
  private final int nearFullQueueCapacity;

  private static final int DEFAULT_QUEUE_CAPACITY = 64;
  private static final int DEFAULT_NEAR_FULL_QUEUE_LEFT = 3;

  public MeasurementFileControllerV2(
      ProviderRegistry providerRegistry,
      StorageFileIndex storageFileIndex,
      @org.springframework.beans.factory.annotation.Value("${server.memory.download.buffer}") Integer downloadBufferSize,
      @org.springframework.beans.factory.annotation.Value("${server.download.queue.capacity}") Integer downloadQueueCapacity,
      @org.springframework.beans.factory.annotation.Value("${server.download.progress-log-interval:30000}") Long progressLogIntervalMs,
      @org.springframework.beans.factory.annotation.Value("${server.download.near-full-queue-left:3}") Integer nearFullQueueLeft) {
    this.providerRegistry = providerRegistry;
    this.storageFileIndex = storageFileIndex;
    this.downloadBufferSize = Optional.ofNullable(downloadBufferSize).orElse(DEFAULT_BUFFER_SIZE);
    this.downloadQueueCapacity = Optional.ofNullable(downloadQueueCapacity).orElse(DEFAULT_QUEUE_CAPACITY);
    this.progressLogIntervalMs = Optional.ofNullable(progressLogIntervalMs)
        .filter(v -> v > 0).orElse(DEFAULT_PROGRESS_LOG_INTERVAL_MS);
    this.nearFullQueueCapacity = Optional.ofNullable(nearFullQueueLeft)
        .filter(v -> v >= 0).orElse(DEFAULT_NEAR_FULL_QUEUE_LEFT);
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
    String sanitizedId = sanitizeMeasurementId(measurementId);
    java.util.List<FileInfo> files;
    try {
      files = storageFileIndex.files(sanitizedId);
    } catch (DatasetNotFoundException e) {
      throw new GlobalException("request failed.", ErrorCode.MEASUREMENT_NOT_FOUND,
          ErrorParameters.of(sanitizedId));
    } catch (TransientException e) {
      throw new GlobalException("request failed.", ErrorCode.GENERAL, ErrorParameters.empty());
    }
    if (files.isEmpty()) {
      throw new GlobalException("request failed.", ErrorCode.MEASUREMENT_NOT_FOUND,
          ErrorParameters.of(sanitizedId));
    }
    var entries = new java.util.ArrayList<MeasurementManifest.FileEntry>();
    for (int i = 0; i < files.size(); i++) {
      FileInfo fileInfo = files.get(i);
      String downloadHref = "/measurements/%s/files/%d".formatted(sanitizedId, i);
      var links = new MeasurementManifest.Links(new MeasurementManifest.Download(downloadHref));
      long crc32 = parseCrc32(fileInfo);
      entries.add(new MeasurementManifest.FileEntry(i, fileInfo.path(), fileInfo.fileName(),
          fileInfo.size(), crc32, formatUtcIso(fileInfo.registrationMillis()), links));
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
    String sanitizedId = sanitizeMeasurementId(measurementId);

    FileInfo fileInfo;
    try {
      fileInfo = storageFileIndex.fileByIndex(sanitizedId, index)
          .orElseThrow(() -> new GlobalException("request failed.", ErrorCode.FILE_NOT_FOUND,
              ErrorParameters.of(sanitizedId)));
    } catch (DatasetNotFoundException e) {
      throw new GlobalException("request failed.", ErrorCode.MEASUREMENT_NOT_FOUND,
          ErrorParameters.of(sanitizedId));
    } catch (TransientException e) {
      throw new GlobalException("request failed.", ErrorCode.GENERAL, ErrorParameters.empty());
    }

    StorageProvider provider;
    try {
      provider = providerRegistry.getProvider(sanitizedId);
    } catch (StorageProviderException e) {
      throw new GlobalException("request failed.", ErrorCode.GENERAL, ErrorParameters.empty());
    }

    long fileLength = fileInfo.size();
    boolean isRangeCapable = provider instanceof ByteRangeProvider;

    // Parse the range header. For range-capable providers, delegate to the provider.
    // For non-range-capable providers, handle via skip-to-start on the whole-file stream.
    ByteRange byteRange = null;
    ByteRange.ResolvedRange resolvedRange = null;
    boolean isPartial = false;
    if (rangeHeader != null && !rangeHeader.isBlank()) {
      try {
        byteRange = ByteRangeParser.parse(rangeHeader);
        if (byteRange != null) {
          resolvedRange = byteRange.resolve(fileLength);
          isPartial = true;
        }
      } catch (InvalidByteRangeException e) {
        throw new GlobalException("range not satisfiable", ErrorCode.RANGE_NOT_SATISFIABLE,
            ErrorParameters.of(fileLength));
      }
    }

    // Obtain the DataFile, pushing range handling to the provider when supported.
    DataFile dataFile;
    try {
      if (isRangeCapable && byteRange != null) {
        dataFile = ((ByteRangeProvider) provider).getFile(sanitizedId, index, byteRange);
      } else {
        dataFile = provider.getFile(sanitizedId, index);
      }
    } catch (DatasetNotFoundException e) {
      throw new GlobalException("request failed.", ErrorCode.MEASUREMENT_NOT_FOUND,
          ErrorParameters.of(sanitizedId));
    } catch (StorageFileNotFoundException e) {
      throw new GlobalException("request failed.", ErrorCode.FILE_NOT_FOUND,
          ErrorParameters.of(sanitizedId));
    } catch (InvalidByteRangeException e) {
      throw new GlobalException("range not satisfiable", ErrorCode.RANGE_NOT_SATISFIABLE,
          ErrorParameters.of(fileLength));
    } catch (TransientException e) {
      throw new GlobalException("request failed.", ErrorCode.GENERAL, ErrorParameters.empty());
    }

    String requestId = "downloadFile-" + UUID.randomUUID();
    String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
    log.info("request {}: user {} requests file {} of measurement {} (v2)", requestId,
        currentUser, fileInfo.path(), sanitizedId);

    // Determine the effective start offset and content length.
    // For range-capable providers, the stream already starts at the range offset.
    // For non-range-capable providers, we skip to the start manually.
    long start;
    long contentLength;
    long end;
    if (isRangeCapable && resolvedRange != null) {
      start = resolvedRange.start();
      end = resolvedRange.end();
      contentLength = resolvedRange.length();
    } else if (resolvedRange != null) {
      start = resolvedRange.start();
      end = resolvedRange.end();
      contentLength = resolvedRange.length();
    } else {
      start = 0;
      end = fileLength - 1;
      contentLength = fileLength;
    }

    final long skipTo = isRangeCapable ? 0 : start;

    StreamingResponseBody responseBody = outputStream -> {
      log.info("request {}: user {} started downloading file {} of measurement {} (v2)",
          requestId, currentUser, fileInfo.path(), sanitizedId);
      try {
        writeRange(dataFile, skipTo, contentLength, outputStream, fileInfo.path(), sanitizedId);
        log.info("request {}: user {} finished downloading file {} of measurement {} (v2)",
            requestId, currentUser, fileInfo.path(), sanitizedId);
      } catch (Exception e) {
        if (isClientAbort(e)) {
          log.warn("request {}: user {} disconnected while downloading file {} of measurement {} (v2)",
              requestId, currentUser, fileInfo.path(), sanitizedId);
        } else {
          log.error("request {}: user {} failed for file {} of measurement {} (v2)", requestId,
              currentUser, fileInfo.path(), sanitizedId, e);
        }
        throw e;
      }
    };

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    headers.setContentLength(contentLength);
    if (isRangeCapable) {
      headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
    }
    headers.set(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"" + extractFileName(fileInfo.path()) + "\"");
    if (isPartial) {
      headers.set(HttpHeaders.CONTENT_RANGE,
          "bytes %d-%d/%d".formatted(start, end, fileLength));
      return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(responseBody);
    }
    return ResponseEntity.ok().headers(headers).body(responseBody);
  }

  /**
   * Writes a byte range from the data file to the output stream using an async producer-consumer
   * pattern. A dedicated producer thread reads from the input stream into a bounded queue,
   * while the consumer (calling thread) reads from the queue and writes to the client output stream.
   */
  private void writeRange(DataFile dataFile, long skipTo, long contentLength,
      OutputStream outputStream, String filePath, String measurementId) throws IOException {
    try (InputStream inputStream = dataFile.inputStream()) {
      skipToStart(inputStream, skipTo);
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
   * to skip the requested number of bytes, so we loop until the offset is reached.
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
          byte[] data = Arrays.copyOf(buffer, read);
          bufferQueue.put(data);
          remaining -= read;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        producerError.compareAndSet(null, e);
      } catch (Exception | Error e) {
        producerError.compareAndSet(null, e);
      } finally {
        producerDone.set(true);
      }
    }, "provider-reader-" + filePath);
    producer.start();
    return new Transfer(producer, bufferQueue, producerError, producerDone);
  }

  private void consume(Transfer transfer, OutputStream outputStream, long contentLength,
      String filePath, String measurementId) throws IOException {
    long totalBytesWritten = 0;
    long bytesSinceLastLog = 0;
    long lastProgressLogTime = System.currentTimeMillis();
    try {
      while (!transfer.done.get() || !transfer.queue.isEmpty()) {
        byte[] data = transfer.queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (data != null) {
          outputStream.write(data);
          totalBytesWritten += data.length;
          bytesSinceLastLog += data.length;
          logNearFullQueue(transfer, filePath, measurementId);
          long[] result = logProgress(totalBytesWritten, bytesSinceLastLog, contentLength,
              lastProgressLogTime, filePath, measurementId, transfer.queue.size());
          lastProgressLogTime = result[0];
          bytesSinceLastLog = result[1];
        }
        transfer.throwIfFailed(filePath);
      }
      transfer.throwIfFailed(filePath);
      log.info("Transfer complete for file {} of measurement {}: {}MB total (v2)",
          filePath, measurementId, totalBytesWritten / (1024 * 1024));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      transfer.producer.interrupt();
      throw new IOException("Download interrupted for file " + filePath, e);
    }
  }

  private void logNearFullQueue(Transfer transfer, String filePath, String measurementId) {
    int freeCapacity = downloadQueueCapacity - transfer.queue.size();
    if (freeCapacity < nearFullQueueCapacity) {
      log.warn("Download queue nearly full for file {} of measurement {}: {} of {} slots free (v2)",
          filePath, measurementId, freeCapacity, downloadQueueCapacity);
    }
  }

  private long[] logProgress(long totalBytesWritten, long bytesSinceLastLog, long contentLength,
      long lastProgressLogTime, String filePath, String measurementId, int queueSize) {
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastProgressLogTime <= progressLogIntervalMs) {
      return new long[]{lastProgressLogTime, bytesSinceLastLog};
    }
    double progressPercent = (totalBytesWritten * 100.0) / contentLength;
    double elapsedSeconds = (currentTime - lastProgressLogTime) / 1000.0;
    double throughputMBps = (bytesSinceLastLog / (1024.0 * 1024.0)) / elapsedSeconds;
    log.info("Download progress for file {} of measurement {}: {}MB / {}MB ({}%), throughput: {} MB/s, queue size: {} (v2)",
        filePath, measurementId,
        totalBytesWritten / (1024 * 1024), contentLength / (1024 * 1024),
        String.format("%.1f", progressPercent),
        String.format("%.2f", throughputMBps),
        queueSize);
    return new long[]{currentTime, 0};
  }

  private static boolean isClientAbort(Exception e) {
    Throwable cause = e;
    while (cause != null) {
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

  private String formatUtcIso(long epochMillis) {
    if (epochMillis < 0) {
      return null;
    }
    return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis));
  }

  private String sanitizeMeasurementId(String measurementId) {
    if (MEASUREMENT_ID_PATTERN.matcher(measurementId).find()) {
      throw new GlobalException("unexpected measurement identifier containing unallowed characters",
          ErrorCode.ILLEGAL_MEASUREMENT_ID,
          ErrorParameters.of("The provided measurement identifier contained unexpected characters."));
    }
    return measurementId;
  }

  /**
   * Extracts the CRC-32 checksum from the file info, or 0 if none is available or the algorithm
   * is not CRC-32.
   */
  private static long parseCrc32(FileInfo fileInfo) {
    if (fileInfo.checksum() == null) {
      return 0;
    }
    if (!"crc32".equalsIgnoreCase(fileInfo.checksum().algorithm())) {
      return 0;
    }
    try {
      return Long.parseUnsignedLong(fileInfo.checksum().value());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static String extractFileName(String path) {
    int lastSeparator = path.lastIndexOf('/');
    return lastSeparator < 0 ? path : path.substring(lastSeparator + 1);
  }

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
        throw new IOException("Provider read failed for file " + filePath, failure);
      }
    }
  }
}

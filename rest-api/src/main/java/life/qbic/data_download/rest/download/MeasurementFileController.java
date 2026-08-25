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
import java.io.OutputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
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

  private final MeasurementDataProvider measurementDataProvider;
  private final MeasurementFileIndex measurementFileIndex;
  private final ByteRange byteRange;
  private final int downloadBufferSize;

  public MeasurementFileController(
      @Qualifier("measurementDataProvider") MeasurementDataProvider measurementDataProvider,
      MeasurementFileIndex measurementFileIndex,
      ByteRange byteRange,
      @Value("${server.memory.download.buffer}") Integer downloadBufferSize) {
    this.measurementDataProvider = measurementDataProvider;
    this.measurementFileIndex = measurementFileIndex;
    this.byteRange = byteRange;
    this.downloadBufferSize = Optional.ofNullable(downloadBufferSize)
        .orElse(DEFAULT_BUFFER_SIZE);
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
        writeRange(dataFile, start, contentLength, outputStream);
        log.info("request %s: user %s finished downloading file %s of measurement %s".formatted(
            requestId, currentUser, fileInfo.path(), sanitizedId));
      } catch (Exception e) {
        log.error("request %s: user %s failed for file %s of measurement %s".formatted(requestId,
            currentUser, fileInfo.path(), sanitizedId), e);
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

  private void writeRange(DataFile dataFile, long start, long contentLength, OutputStream outputStream)
      throws IOException {
    try (var inputStream = dataFile.inputStream()) {
      // InputStream.skip is not guaranteed to skip the requested number of bytes, so we loop until
      // the requested start offset is reached. When skip makes no progress (e.g. on some sources),
      // fall back to reading a single byte at a time so we never loop forever.
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
      byte[] buffer = new byte[downloadBufferSize];
      long remaining = contentLength;
      int read;
      while (remaining > 0 && (read = inputStream.read(buffer, 0,
          (int) Math.min(buffer.length, remaining))) != -1) {
        outputStream.write(buffer, 0, read);
        remaining -= read;
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

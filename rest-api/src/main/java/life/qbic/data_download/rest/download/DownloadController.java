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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import life.qbic.data_download.measurements.api.DataFile;
import life.qbic.data_download.measurements.api.MeasurementData;
import life.qbic.data_download.measurements.api.MeasurementDataProvider;
import life.qbic.data_download.measurements.api.MeasurementDataReader;
import life.qbic.data_download.measurements.api.MeasurementId;
import life.qbic.data_download.rest.exceptions.GlobalException;
import life.qbic.data_download.rest.exceptions.GlobalException.ErrorCode;
import life.qbic.data_download.rest.exceptions.GlobalException.ErrorParameters;
import life.qbic.data_download.util.zip.api.FileInfo;
import life.qbic.data_download.util.zip.api.FileTimes;
import life.qbic.data_download.util.zip.manipulation.BufferedZippingFunctions;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@Tag(name = "Download Endpoints", description = "Rest endpoints related to downloading data")
public class DownloadController {

  private final MeasurementDataProvider measurementDataProvider;
  private final MeasurementDataReaderFactory measurementDataReaderFactory;
  private final int downloadBufferSize;

  private static final Logger log = getLogger(DownloadController.class);

  public DownloadController(
      @Qualifier("measurementDataProvider") MeasurementDataProvider measurementDataProvider,
      @Qualifier("measurementDataReaderFactory") MeasurementDataReaderFactory measurementDataReaderFactory,
      @Value("${server.memory.download.buffer}") Integer downloadBufferSize) {
    this.measurementDataProvider = measurementDataProvider;
    this.measurementDataReaderFactory = measurementDataReaderFactory;
    this.downloadBufferSize = Optional.ofNullable(downloadBufferSize)
        .orElse(BufferedZippingFunctions.DEFAULT_BUFFER_SIZE);
  }


  @GetMapping(value = "/measurements/{measurementId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Download a measurement from the given measurement identifier")
  @Parameter(name = "measurementId", required = true, description = "The identifier of the measurement to download", example = "NGSQ0001006AO-25948529211108")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "successful operation, the measurement is retrieved asynchronously", content = @Content(schema = @Schema(implementation = Void.class))),
      @ApiResponse(responseCode = "403", description = "forbidden, you do not have access to this resource", content = @Content(schema = @Schema(implementation = Void.class))),
      @ApiResponse(responseCode = "404", description = "measurement not found", content = @Content(schema = @Schema(implementation = Void.class))),
  })
  public ResponseEntity<StreamingResponseBody> downloadMeasurement(
      @PathVariable("measurementId") String measurementId) {
    String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
    var requestId = "downloadMeasurement-" + UUID.randomUUID();
    log.info("request %s: user %s requests measurement %s".formatted(requestId, currentUser,
        measurementId));
    var measurementIdentifier = new MeasurementId(measurementId);
    MeasurementData measurementData = measurementDataProvider.loadData(measurementIdentifier);
    if (measurementData == null) {
      throw new GlobalException("request %s failed.".formatted(requestId),
          ErrorCode.MEASUREMENT_NOT_FOUND, ErrorParameters.of(measurementId));
    }
    String outputFileName =
        measurementId + "-"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd.hhmmss"))
            + ".zip";

    StreamingResponseBody responseBody = outputStream -> {
      log.info("request %s: user %s started downloading measurement %s".formatted(requestId, currentUser, measurementIdentifier.id()));
      writeDataToStream(measurementIdentifier,
          outputStream,
          measurementData,
          measurementDataReaderFactory.getMeasurementDataReader());
      log.info("request %s: user %s finished downloading measurement %s".formatted(requestId, currentUser, measurementIdentifier.id()));
    };
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header("Accept-Charset", "UTF-8")
        .header("Expires", "0")
        .header("Content-Disposition", "attachment;filename=" + outputFileName)
        .body(responseBody);
  }

  private void writeDataToStream(MeasurementId measurementId, OutputStream outputStream, MeasurementData measurementData,
      MeasurementDataReader measurementDataReader) {
    String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
    try (final var dataStream = measurementData.stream();
        final var zippedStream = BufferedZippingFunctions.zipInto(outputStream)) {
      measurementDataReader.open(dataStream);
      DataFile file;
      while ((file = measurementDataReader.nextDataFile()) != null) {
        FileInfo zipEntryFileInfo = new FileInfo(
            file.fileInfo().path(),
            file.fileInfo().length(),
            file.fileInfo().crc32(),
            new FileTimes(file.fileInfo().registrationMillis(), -1,
                file.fileInfo().lastModifiedMillis()));

        BufferedZippingFunctions.addToZip(zippedStream, zipEntryFileInfo, file.inputStream(), downloadBufferSize);
      }
    } catch (IOException e) {
      throw new GlobalException(
          "User %s failed downloading measurement %s. %s".formatted(currentUser, measurementId.id(),
              e.getMessage()), e);
    }
  }
}

package life.qbic.data_download.rest.download;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * TODO!
 * <b>short description</b>
 *
 * <p>detailed description</p>
 *
 * @since <version tag>
 */
@RestController
@RequestMapping(path = "/download")
public class DownloadController {

  private final MeasurementDataProvider measurementDataProvider;
  private final MeasurementDataReader measurementDataReader;

  public DownloadController(
      @Qualifier("measurementDataProvider") MeasurementDataProvider measurementDataProvider,
      @Qualifier("measurementDataReader") MeasurementDataReader measurementDataReader
  ) {
    this.measurementDataProvider = measurementDataProvider;
    this.measurementDataReader = measurementDataReader;
  }


  @GetMapping(value = "/measurements/{measurementId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Download a measurement from the given measurement identifier")
  @Parameter(name = "measurementId", required = true, description = "The identifier of the measurement to download")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "successful operation, the measurement is retrieved asynchronously", content = @Content(schema = @Schema(implementation = Void.class))),
      @ApiResponse(responseCode = "404", description = "measurement not found", content = @Content(schema = @Schema(implementation = Void.class)))
  })
  public ResponseEntity<StreamingResponseBody> downloadMeasurement(
      final HttpServletResponse response,
      @PathVariable("measurementId") String measurementId) {
    var measurementIdentifier = new MeasurementId(measurementId);
    MeasurementData measurementData = measurementDataProvider.loadData(measurementIdentifier);
    if (measurementData == null) {
      throw new GlobalException(ErrorCode.MEASUREMENT_NOT_FOUND, ErrorParameters.of(measurementId));
    }

    String outputFileName =
        measurementId + "-"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd.hhmmss"))
            + ".zip";

    response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Expires", "0"); // no browser caching
    response.setHeader("Content-Disposition", "attachment;filename=" + outputFileName);
    // we do not set the `Content-Length` as the total size is hard to compute
    // (taking into account fileName length and zip header overheads)

    StreamingResponseBody responseBody = outputStream -> writeDataToStream(outputStream,
        measurementData,
        measurementDataReader);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment;filename=" + outputFileName)
        .body(responseBody);
  }

  private static void writeDataToStream(OutputStream outputStream, MeasurementData measurementData,
      MeasurementDataReader measurementDataReader) {
    try (var dataStream = measurementData.stream();
        var zippedStream = BufferedZippingFunctions.zipInto(outputStream)) {
      measurementDataReader.open(dataStream);
      DataFile file;
      while ((file = measurementDataReader.nextDataFile()) != null) {
        FileInfo zipEntryFileInfo = new FileInfo(
            file.fileInfo().path(),
            file.fileInfo().length(),
            file.fileInfo().crc32(),
            new FileTimes(file.fileInfo().registrationMillis(), -1,
                file.fileInfo().lastModifiedMillis()));

        BufferedZippingFunctions.addToZip(zippedStream, zipEntryFileInfo, file.inputStream());
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

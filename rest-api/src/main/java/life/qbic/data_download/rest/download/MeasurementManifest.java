package life.qbic.data_download.rest.download;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A manifest of the files of a measurement in stable order.
 *
 * @param measurementId the identifier of the measurement
 * @param files         the ordered files
 */
@Schema(description = "A manifest of the files of a measurement in stable order")
public record MeasurementManifest(
    @Schema(description = "The identifier of the measurement", example = "NGSQ0001006AO-25948529211108") String measurementId,
    @Schema(description = "The ordered files of the measurement") List<FileEntry> files) {

  /**
   * A single file entry in a manifest.
   *
   * @param index            the zero-based stable index of the file
   * @param path             the formatted path of the file
   * @param fileName         the name of the file
   * @param length           the size of the file in bytes
   * @param crc32            the CRC-32 checksum of the file content
   * @param registrationTime the time the file was registered in the system as a UTC ISO-8601 string, or {@code null} if unknown
   * @param links            links to related resources, e.g. the file download endpoint
   */
  @Schema(description = "A single file entry in a manifest")
  public record FileEntry(
      @Schema(description = "The zero-based stable index of the file", example = "0") int index,
      @Schema(description = "The formatted path of the file", example = "data/read1.fastq.gz") String path,
      @Schema(description = "The name of the file", example = "read1.fastq.gz") String fileName,
      @Schema(description = "The size of the file in bytes", example = "1048576") long length,
      @Schema(description = "The CRC-32 checksum of the file content", example = "123456789") long crc32,
      @Schema(description = "The time the file was registered in the system as an ISO-8601 formatted date-time in UTC",
          example = "2024-01-15T10:30:00Z", type = "string", format = "date-time") String registrationTime,
      @JsonProperty("_links") @Schema(description = "Links to related resources for the file entry") Links links) {

  }

  /**
   * Links to related resources for a manifest file entry.
   *
   * @param download link to download the file
   */
  @Schema(description = "Links to related resources for a manifest file entry")
  public record Links(
      @Schema(description = "The download link of the file") Download download) {

  }

  /**
   * The download link of a file.
   *
   * @param href the relative URL to download the file
   */
  @Schema(description = "The download link of a file")
  public record Download(
      @Schema(description = "The relative URL to download the file", example = "/measurements/NGSQ0001006AO-25948529211108/files/0") String href) {

  }
}

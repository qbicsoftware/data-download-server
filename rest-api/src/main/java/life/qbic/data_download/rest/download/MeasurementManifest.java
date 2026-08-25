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
public record MeasurementManifest(String measurementId, List<FileEntry> files) {

  /**
   * A single file entry in a manifest.
   *
   * @param index         the zero-based stable index of the file
   * @param path          the formatted path of the file
   * @param length        the size of the file in bytes
   * @param crc32         the CRC-32 checksum of the file content
   * @param registrationTime  the time the file was registered in the system as a UTC ISO-8601 string, or {@code null} if unknown
   * @param links         links to related resources, e.g. the file download endpoint
   */
  public record FileEntry(int index, String path, String fileName, long length, long crc32,
      @Schema(description = "The time the file was registered in the system as an ISO-8601 formatted date-time in UTC",
          example = "2024-01-15T10:30:00Z", type = "string", format = "date-time") String registrationTime,
      @JsonProperty("_links") Links links) {

  }

  /**
   * Links to related resources for a manifest file entry.
   *
   * @param download link to download the file
   */
  public record Links(Download download) {

  }

  /**
   * The download link of a file.
   *
   * @param href the relative URL to download the file
   */
  public record Download(String href) {

  }
}

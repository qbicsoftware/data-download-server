package life.qbic.data_download.rest.download;

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
   * @param lastModified  the last modification time in epoch millis, or -1 if unknown
   */
  public record FileEntry(int index, String path, long length, long crc32, long lastModified) {

  }
}
package life.qbic.data_download.util.zip.api;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

/**
 * Information about a file
 */
public record FileInfo(String filePath, long fileSize, long expectedCrc, FileTimes fileTimes) {

  public FileInfo {
    requireNonNull(filePath, "filePath must not be null");
    if (isNull(fileTimes)) {
      fileTimes = FileTimes.unknown();
    }
    if (fileSize < 0) {
      throw new IllegalArgumentException("File size must be greater than 0");
    }
  }
}

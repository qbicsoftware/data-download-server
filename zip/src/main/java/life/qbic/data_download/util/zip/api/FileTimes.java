package life.qbic.data_download.util.zip.api;

import java.nio.file.attribute.FileTime;
import java.util.Optional;

/**
 * File time information for a file. Negative numbers denote unknown times.
 */
public record FileTimes(long creationTimeMillis, long lastAccessTimeMillis,
                        long lastModifiedMillis) {

  public static FileTimes unknown() {
    return new FileTimes(-1, -1, -1);
  }

  private static Optional<FileTime> toOptionalFileTime(long millis) {
    if (millis >= 0) {
      return Optional.of(FileTime.fromMillis(millis));
    }
    return Optional.empty();
  }

  public Optional<FileTime> creationTime() {
    return toOptionalFileTime(creationTimeMillis);
  }

  public Optional<FileTime> lastAccessTime() {
    return toOptionalFileTime(lastAccessTimeMillis);
  }

  public Optional<FileTime> lastModifiedTime() {
    return toOptionalFileTime(lastModifiedMillis);
  }
}

package life.qbic.data_download.storage;

import java.util.Objects;

/**
 * Metadata about a file in a dataset, as reported by a {@link StorageProvider}.
 *
 * <p>The {@code checksum} is an optional integrity value whose algorithm is chosen by the storage
 * provider; a {@code null} checksum means the provider does not expose one.
 *
 * @param path                the stable path of the file within the dataset
 * @param fileName            the plain file name (last path segment)
 * @param size                the file size in bytes
 * @param checksum            the integrity checksum, or {@code null} if none is available
 * @param registrationMillis  creation/registration time, or {@code -1} if unknown
 * @param lastModifiedMillis  last-modified time, or {@code -1} if unknown
 */
public record FileInfo(String path, String fileName, long size, Checksum checksum,
                       long registrationMillis, long lastModifiedMillis) {

  public FileInfo {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(fileName, "fileName must not be null");
    if (size < 0) {
      throw new IllegalArgumentException("size must not be negative");
    }
  }

  /**
   * An integrity checksum whose algorithm is provider-selected.
   *
   * @param algorithm the checksum algorithm (e.g. {@code crc32}, {@code sha256})
   * @param value     the checksum value
   */
  public record Checksum(String algorithm, String value) {

    public Checksum {
      Objects.requireNonNull(algorithm, "algorithm must not be null");
      Objects.requireNonNull(value, "value must not be null");
    }
  }
}
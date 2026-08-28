package life.qbic.data_download.storage;

import life.qbic.data_download.storage.exception.InvalidByteRangeException;

/**
 * A suffix-range, {@code bytes=-suffix-length}, per RFC 9110 §14.1.2. Selects the last
 * {@code length} bytes of the file; if the file is shorter than {@code length}, the entire file is
 * used.
 *
 * @param length the number of trailing bytes to select; must be greater than zero
 */
public record SuffixRange(long length) implements ByteRange {

  public SuffixRange {
    if (length <= 0) {
      throw new IllegalArgumentException("suffix length must be greater than zero");
    }
  }

  @Override
  public Resolved resolve(long fileSize) {
    if (fileSize <= 0) {
      throw new InvalidByteRangeException("no satisfiable range for an empty file");
    }
    long start = Math.max(0, fileSize - length);
    return new Resolved(start, fileSize - 1);
  }
}
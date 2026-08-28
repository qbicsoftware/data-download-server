package life.qbic.data_download.storage;

import life.qbic.data_download.storage.exception.InvalidByteRangeException;

/**
 * An int-range with an absent {@code last-pos}, {@code bytes=first-pos-}, per RFC 9110 §14.1.2.
 * Selects the remainder of the file from {@code start} to the end.
 *
 * @param start the first byte offset, inclusive
 */
public record FromStartRange(long start) implements ByteRange {

  public FromStartRange {
    if (start < 0) {
      throw new IllegalArgumentException("start must not be negative");
    }
  }

  @Override
  public Resolved resolve(long fileSize) {
    if (fileSize <= 0) {
      throw new InvalidByteRangeException("no satisfiable range for an empty file");
    }
    if (start >= fileSize) {
      throw new InvalidByteRangeException(
          "range start " + start + " is out of bounds for a file of " + fileSize + " bytes");
    }
    return new Resolved(start, fileSize - 1);
  }
}
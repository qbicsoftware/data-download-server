package life.qbic.data_download.storage;

import life.qbic.data_download.storage.exception.InvalidByteRangeException;

/**
 * An int-range with both bounds, {@code bytes=first-pos-last-pos} (inclusive on both ends), per
 * RFC 9110 §14.1.2. For example {@code bytes=0-99} selects bytes 0 through 99.
 *
 * <p>The {@code last-pos} may point beyond the end of the file; {@link #resolve(long)} clamps it
 * to the last byte of the file.
 *
 * @param start the first byte offset, inclusive
 * @param end   the last byte offset, inclusive
 */
public record FromToRange(long start, long end) implements ByteRange {

  public FromToRange {
    if (start < 0) {
      throw new IllegalArgumentException("start must not be negative");
    }
    if (end < start) {
      throw new IllegalArgumentException("end must not be smaller than start");
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
    return new Resolved(start, Math.min(end, fileSize - 1));
  }
}
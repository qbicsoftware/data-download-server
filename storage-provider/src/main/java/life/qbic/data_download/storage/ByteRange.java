package life.qbic.data_download.storage;

/**
 * An inclusive byte range of a file, used to request partial content from a
 * {@link ByteRangeProvider}.
 *
 * <p>Both bounds are inclusive, matching HTTP {@code Range}: {@code bytes=0-99} selects exactly 100
 * bytes. A {@code null} range passed to a provider denotes the whole file.
 *
 * @param start the first byte offset, inclusive
 * @param end   the last byte offset, inclusive
 */
public record ByteRange(long start, long end) {

  public ByteRange {
    if (start < 0) {
      throw new IllegalArgumentException("start must not be negative");
    }
    if (end < start) {
      throw new IllegalArgumentException("end must not be smaller than start");
    }
  }

  /**
   * The number of bytes in this range ({@code end - start + 1}).
   */
  public long length() {
    return end - start + 1;
  }
}
package life.qbic.data_download.storage;

import life.qbic.data_download.storage.exception.InvalidByteRangeException;

/**
 * A byte range of a file, as defined by RFC 9110 §14.1.2. Represents a request for partial content
 * from a {@link ByteRangeProvider}.
 *
 * <p>The three permitted forms mirror the RFC 9110 {@code byte-range-spec} grammar:
 *
 * <pre>
 * byte-range-spec = first-byte-pos "-" [last-byte-pos]  ; int-range
 *                 | "-" suffix-length                    ; suffix-range
 * </pre>
 *
 * <ul>
 *   <li>{@link FromToRange} — {@code bytes=start-end} (both offsets inclusive)</li>
 *   <li>{@link FromStartRange} — {@code bytes=start-} (to the end of the file)</li>
 *   <li>{@link SuffixRange} — {@code bytes=-length} (the last {@code length} bytes)</li>
 * </ul>
 *
 * <p>A {@code null} range passed to a provider denotes the whole file.
 */
public sealed interface ByteRange permits FromToRange, FromStartRange, SuffixRange {

  /**
   * Resolves this range against a known file size into a concrete, inclusive
   * {@link ResolvedRange}. Throws {@link InvalidByteRangeException} when the range is
   * unsatisfiable for the given size.
   *
   * @param fileSize the total size of the file in bytes
   * @return the resolved inclusive byte range
   * @throws InvalidByteRangeException if the range is unsatisfiable for the given file size
   */
  ResolvedRange resolve(long fileSize);

  /**
   * An inclusive byte range resolved against a concrete file size.
   *
   * @param start the first byte offset, inclusive
   * @param end   the last byte offset, inclusive
   */
  record ResolvedRange(long start, long end) {

    public ResolvedRange {
      if (start < 0 || end < start) {
        throw new IllegalArgumentException("invalid resolved range " + start + "-" + end);
      }
    }

    /**
     * The number of bytes in this range ({@code end - start + 1}).
     */
    public long length() {
      return end - start + 1;
    }
  }
}